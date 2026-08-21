# Stellar 后端架构审查结论

> 审查对象：`backend/`（Spring Boot 3.3 / Java 21 / MyBatis-Plus 3.5.7 / Sa-Token 1.38 / PostgreSQL + Redis）
> 审查范围：架构分层、编码规范、命名与模块划分、性能与并发、安全与隐私
> 审查方法：静态代码通读 + 全局模式扫描（244 个 Java 源文件，29 实体 / 35 Service / 31 Controller）
> 审查日期：2026-08-20

---

## 一、总体评价

**结论：架构整体健康，达到生产可用水平。分层清晰、横切关注点收敛良好、可观测性与缓存策略到位。**

- 优点（行业规范符合度）：统一 `Result<T>` 信封 + 全局异常处理、基于注解的鉴权/限流/日志切面、按 domain 分包（高内聚低耦合）、异步线程池隔离、监控指标后台采样、JSR-380 校验全覆盖、无 `System.out`/SQL 注入。
- 主要短板集中在**三处重复/低效**与**两处资源/多实例风险**：IP 解析逻辑散落 9+ 处、请求线程同步查库解析 operator、RAG 本地缓存粗锁且无驱逐（单实例假设）。这些不阻塞当前单实例运行，但是规模化与多实例部署的隐患。
- 安全面：CORS 通配凭据、Jackson `defaultTyping`、默认弱口令属中低风险，需按下方清单收敛。

---

## 二、架构设计评估（符合规范）

### 2.1 分层与模块划分 ✅ 优秀
- 按业务域分子包 `com.stellar.{ai,tts,game,system,memos}`，每域内含 `controller/service/entity/mapper/dto/vo/event`，公共基础（`common/config/interceptor/aspect/annotation/enums`）下沉根级，`infra` 承载跨域基础设施（SSE/限流/SSRF/主题解析）。依赖方向单向（domain → infra/common），无循环。
- Mapper 全用 MyBatis-Plus `BaseMapper`，**无手写 XML**（仅 `logback-spring.xml`），SQL 由包装器生成，可维护性高。

### 2.2 横切关注点 ✅ 良好
- **统一响应与异常**：`Result<T>` + `ResultCode` + `@RestControllerAdvice GlobalExceptionHandler` + `BusinessException`，覆盖业务/未登录/校验/系统异常，traceId 经 MDC 贯穿（`RequestLogInterceptor` 注入）。
- **拦截器链顺序正确**：`RequestLog → Auth → RateLimit`。`AuthInterceptor` 默认全拦仅 `@PublicAccess` 放行；`RateLimitInterceptor` 仅对游客按 IP 单日计数（`RateLimitService` 用 Redis INCR + 当日过期，多实例共享）。
- **自定义注解**：`@PublicAccess` / `@RateLimit` / `@Log` 将横切逻辑声明化，符合关注点分离。

### 2.3 异步与缓存 ✅ 良好
- `AsyncConfig` 三池隔离：`logTaskExecutor`(2/8/500) / `aiTaskExecutor`(2/4/50) / `aiToolExecutor`(2/4/20)，回压策略合理（`CallerRunsPolicy` / `AbortPolicy`）。**注意已规避 `@Async` 自调用**：图片/视频 worker 独立成 Service 拆分自 `AiImageService`，注释明确说明 AOP 代理限制——工程纪律好。
- Spring Cache 用于配置型数据（`ai-model`/`ai-provider`/`setting`/`ai-persona`/`dict`/`menu-visibility`/`profile-project`），变更用 `@CacheEvict(allEntries=true)` 即时失效，`disableCachingNullValues()` 防穿透。`saveLog` 已 `@Async("logTaskExecutor")` 异步落库。（注：`profile` 单 POJO 缓存会因关 typing 致 `ClassCastException`，已摘缓存直查库，见 Redis 基础设施坑。）

### 2.4 可观测性 ✅ 优秀（亮点）
- `MonitorService` 对 **CPU/磁盘/文件句柄等慢变量后台 2s 定时采样 + `volatile` 缓存**，overview 只读缓存，避免轮询阻塞请求线程；JVM 参数启动采样一次。`JvmHealthIndicator` 比例式告警（堆>90% DOWN），优雅降级（Windows 文件句柄 N/A）。`HttpRequestMetrics` 用原子计数并排除 `/monitor/**`、`/actuator/**` 自污染。设计质量高。

---

## 三、编码规范与命名评估

### ✅ 良好项
- 命名统一（大驼峰类、小驼峰方法、表实体 `Sys*`/`Ai*`），包职责单一。
- 日志详实（满足团队铁律 #3）：关键路径带上下文，异常带堆栈；`LogAspect` 对 password/token/secretKey/apiKey 做了**脱敏掩码**（满足铁律 #9 日志侧）。
- 无 `System.out`/`printStackTrace`；无字符串拼接 SQL（raw SQL 均 `?`/`#{}` 绑定，无注入）。
- `@Transactional(rollbackFor = Exception.class)` 显式回滚；无无谓只读事务；`AiKnowledgeService` 对 private 自调用改用 `TransactionTemplate` 编程式事务——对 Spring AOP 限制有正确认知。

### ⚠️ 问题点（详见第五节）

| 编号 | 问题 | 违反/风险 | 位置 |
|---|---|---|---|
| P1 | IP 解析逻辑重复 9+ 处 | 铁律 #2 高内聚低耦合 | `WebUtils`/`SubjectUtils`/`LogAspect`/`GameController`/`AiTtsService`/`AiImageController`/`AiNotifyController`/`AiChatSessionService`/`AiVideoService` |
| P2 | `WebUtils` 含未使用 import `com.stellar.ai.service.AiChatService` | 跨包循环依赖隐患、编译告警 | `interceptor/WebUtils.java:7` |
| P3 | 请求线程同步 `sysUserMapper.selectById` 解析 operator | 每次审计/外部调用多一次同步 SELECT | `LogAspect:110-111` / `ExternalCallLogger:82-83` |
| P10 | 仓库根散落 `com/baomidou/.../TableInfoHelper.class` | 产物污染（已被 `**/*.class` 忽略，但仍应清理） | 项目根 `com/` |

---

## 四、性能与并发评估

### 4.1 数据库查询效率
- ✅ 无 N+1 明显迹象：列表查询走 `LambdaQueryWrapper`/分页 `selectPage`，raw SQL 均为参数化单语句（如 `AiMemoryService` 的 NOT EXISTS 关联查询）。
- ⚠️ **RAG 缓存重载持锁查库（高）**：`AiKnowledgeService.getCachedChunks` 在**整个 `vectorCache` 上 `synchronized` 的同时执行 JDBC 查询 + 逐行 `VectorOps.parseVector`**。粗粒度锁使所有 kb 重载串行，且 DB IO 期间占用锁，高并发下严重劣化。应改为 **per-kb 锁**（或 `ConcurrentHashMap.computeIfAbsent` + double-check），锁内仅做轻量构建。
- ⚠️ **HikariCP `maximum-pool-size=8` 偏小（中）**：异步 AI worker（aiTask 4 + aiTool 4）+ 请求线程并发持连接时易打满，等待队列堆积（监控页 `pendingConnections` 已带预警）。建议调到 15~20 并设 `minimum-idle`。

### 4.2 缓存策略
- ✅ 配置型数据缓存 + 即时失效策略正确；`disableCachingNullValues` 防穿透。
- ⚠️ **RAG 本地缓存无驱逐（高）**：`AiKnowledgeService` 与 `MemosRagService` 用 `Collections.synchronizedMap` 但**永不回收**，随知识库/笔记增多存在 OOM 风险。建议改用 **Caffeine（基于大小/权重驱逐）**，或明确单实例边界并加监控。
- ⚠️ **缓存 TTL 单一 30min**：对配置型可接受；但缺乏每缓存独立 TTL，长驻列表缓存无上限控制（低）。

### 4.3 并发处理与资源占用
- ✅ 原子计数、volatile 缓存、异步隔离、SSE 心跳（30s）保活设计合理。
- ⚠️ **RAG 缓存单实例假设（高）**：`invalidateIndexCache` 仅在**本实例**失效（事务 `afterCommit` 失效是亮点），但多实例部署下其他实例保留陈旧向量/BM25 索引，与 SSE 用的 Redis pub/sub 不同步 → **多实例一致性缺口**。需补 cache-invalidation 广播或改集中式缓存（Redis）。
- ⚠️ **SSE `EMITTER_TIMEOUT = 86400000`（24h）过长（低）**：建议按业务（如 1h）收紧，减少长连接资源占用。
- ⚠️ **`AiChatService` SSE 编排未深入审查（已知缺口）**：该服务约千行、测试覆盖 17.2%、非确定性。其流式连接管理、token 计费、异常恢复是最大未知面，建议补充集成测试与超时/背压策略。

### 4.4 监控采样
- ✅ CPU/磁盘/句柄慢变量后台采样、快变量实时读，设计到位（见 2.4）。

---

## 五、安全问题清单（按优先级）

| 级别 | 问题 | 位置 | 改进方向 |
|---|---|---|---|
| 中 | **CORS `allowCredentials(true)` + `addAllowedOriginPattern("*")`** | `SaTokenConfig.corsFilter()` | 限定前端域名白名单（`List.of("https://...")`），避免任意源带凭据访问 |
| 中 | **Redis `GenericJackson2JsonRedisSerializer.defaultTyping(true)`** | `RedisConfig.cacheManager()` | 关闭 defaultTyping，改用 `Jackson2JsonRedisSerializer` 指定具体类型或 `RedisSerializer.json()`；缓存对象仅放可信配置 |
| 中 | **默认管理员密码 `123456`（DataInitializer）** | `config/DataInitializer.java:32` | 首次登录强制改密；或生成随机密码打印到日志 |
| 低 | **本地明文弱口令（`application-local.yml`）** | `application-local.yml:3-9` | 已 gitignore（不入库），但建议用环境变量 `${DB_PASSWORD}`/`${REDIS_URL}`，与 `application.yml` 一致 |
| 低 | **MyBatis-Plus 缺 `BlockAttackInnerInterceptor`** | `MyBatisPlusConfig` | 增加防全表更新/删除拦截器，防止 `update()` 无 WHERE 误伤全表 |
| 低 | **`/actuator/health` 公开** | `SaTokenConfig` exclude | 仅 health 暴露可接受；JvmHealthIndicator 含堆信息，属低风险 |

---

## 六、问题清单汇总（优先级排序）

| 优先级 | 编号 | 问题 | 影响面 |
|---|---|---|---|
| 🔴 高 | P1 | IP 解析逻辑重复 9+ 处 | 维护成本 / 一致性 |
| 🔴 高 | P3 | 请求线程同步查库解析 operator | 每次请求的 DB 往返延迟 |
| 🔴 高 | P4 | RAG 缓存粗锁 + 持锁查库 | 并发重载串行化 |
| 🔴 高 | P5 | RAG 缓存无驱逐（OOM 风险） | 内存 |
| 🔴 高 | P6 | RAG 缓存单实例假设 | 多实例数据陈旧 |
| 🟡 中 | S1 | CORS 通配凭据 | 跨站凭据泄露 |
| 🟡 中 | S2 | Jackson defaultTyping | 反序列化 gadget 风险 |
| 🟡 中 | S3 | 默认弱口令 | 账户安全 |
| 🟡 中 | P7 | HikariCP 连接池偏小 | 并发瓶颈 |
| 🟢 低 | P2 | WebUtils 无用 import | 编译告警/循环依赖 |
| 🟢 低 | S4 | 本地明文密码 | 本地安全 |
| 🟢 低 | S5 | 缺 BlockAttack 拦截器 | 全表误更新 |
| 🟢 低 | P8 | SSE 超时 24h | 长连接资源 |
| 🟢 低 | P10 | 根目录散落 class | 产物污染 |
| ⚪ 待补 | P9 | `AiChatService` SSE 编排未审查 | 未知面 |

---

## 七、改进路线图（建议顺序）

1. **收敛工具方法（P1/P2，低风险高收益）**：删除各处重复的 `getClientIp`，统一调用 `WebUtils.getClientIp(HttpServletRequest)` 及其无参变体；清除 `WebUtils` 无用 import。可顺带消除跨包依赖。
2. **异步化 operator 解析（P3）**：将 `LogAspect`/`ExternalCallLogger` 的 `sysUserMapper.selectById` 移入 `SysLogService.saveLog` 的异步路径（传入 userId，异步线程内查库或短缓存 username），去除请求线程的同步 SELECT。
3. **重构 RAG 缓存（P4/P5/P6，中等改动）**：
   - 用 **Caffeine** 替代 `synchronizedMap`，设 `maximumSize`/`expireAfterWrite` 驱逐；
   - 改 per-kb 锁或 `computeIfAbsent` 避免持锁查库；
   - 多实例下增加 Redis pub/sub 广播缓存失效，或迁移至 Redis 集中缓存。
4. **安全收敛（S1–S5）**：CORS 白名单、关闭 Jackson defaultTyping、首次登录强制改密、加 `BlockAttackInnerInterceptor`、连接池上调至 15~20。
5. **补充测试与超时（P9/P8）**：为 `AiChatService` 非确定性编排补集成测试与流超时/背压；收紧 SSE 超时。

---

## 八、审查局限性说明

- 本次为**静态代码审查**，未运行压测/性能剖析；性能结论基于代码模式推断，建议以实际负载验证（尤其 HikariCP 与 RAG 缓存）。
- `AiChatService`（SSE 异步编排，覆盖 17.2%）因非确定性未深入，列为已知缺口。
- 数据库索引、慢查询需结合 `EXPLAIN` 与真实数据量评估，本文未覆盖（无运行态 DB 访问）。
