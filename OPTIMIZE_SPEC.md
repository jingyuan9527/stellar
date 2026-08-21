# Stellar 后端优化清单（给 AI 程序员的实现规格）

> 用法：每条都是可独立实现的任务。请按顺序（高 → 低）实现，每条给出"问题 / 位置 / 做法"。
> 技术栈：Spring Boot 3.3 / Java 21 / MyBatis-Plus 3.5.7 / Sa-Token 1.38 / PostgreSQL + Redis。
> 约束：改动遵循团队铁律（高内聚低耦合、日志详实、不引入无关优化、危险操作先确认、中文提交、文档与代码一致）。

---

## 进度跟踪

- **已完成（本轮，commit `82157b7` 优化：统一IP解析与RAG缓存多实例一致性 P1-P6）**：
  - ✅ **P1** IP 解析收敛到 `WebUtils.getClientIp`（含 `firstNonUnknown` 跳过 unknown 代理占位），9 处重复实现改为委托。已编译通过、单测绿。
  - ✅ **P2** `WebUtils` 删除未使用的 `AiChatService` import，消除跨包依赖。
  - ✅ **P3** 操作人解析改为：请求线程只取 `operatorUserId`（不查库），用户名在 `SysLogService.saveLog` 异步线程内用 **Caffeine 5min 短缓存**解析填充，`ExternalCallLogger`/`LogAspect` 同步查库已消除。
  - ✅ **P4+P5** RAG 缓存（`AiKnowledgeService`/`MemosRagService`）改 **Caffeine** 原子单飞加载（`get(k,loader)` 不持锁、不同 kb 并发）+ `maximumSize`/`expireAfterWrite(30min)` 驱逐，消除粗锁与 OOM 风险。
  - ✅ **P6** 新增 `CacheInvalidation{Event,Message,Publisher,Listener}`：数据变更实例失效本地缓存后通过 **Redis pub/sub** 广播，各实例 `@EventListener` 订阅失效，多实例索引一致（SSE 通道同源）。
- **已完成（安全与连接池轮，S2/S3/S5/P7/P8 全部完成）**：
  - ✅ **S2（已收尾）** `RedisConfig.cacheManager()` 已关 Jackson default typing（不再写 `@class`），旧存量条目由 `RedisCacheBootstrap` 启动时清理。**收尾**：业务 `redisTemplate` bean 同改为 builder + `JavaTimeModule` 关闭 typing；`CacheInvalidationListener`/`AiNotifyListener` 改用**类级** `Jackson2JsonRedisSerializer(消息类.class)` 直读直出（不再 `instanceof` 判型），发布端复用 redisTemplate 纯 JSON payload 即可兼容——彻底消除「后续补关 typing 导致 pub/sub 静默失效」的隐形炸弹。
  - ✅ **S3（已硬阻断）** `AuthService.login` 密码校验通过后在 `mustChangePassword==1` 时向 Sa-Token 会话写 `mustChangePassword` 标记（`SecurityConstants.SESSION_KEY_MUST_CHANGE_PASSWORD`）；`AuthInterceptor` 对带标记会话仅放行 `/user/change-password`、`/user/info`、`/auth/logout`，其余受保护接口一律 403 `BusinessException`；`UserService.changePassword` 改密成功同步清会话标记 + DB 清 0。非配合客户端无法凭默认口令 `123456` 访问业务接口。
  - ✅ **S5** `MyBatisPlusConfig` 挂 `BlockAttackInnerInterceptor`，无 WHERE 的 update/delete 直接拦截。
  - ✅ **P7** HikariCP `maximum-pool-size` 8→15、`minimum-idle` 2→5，缓解异步 AI worker 并发持连接打满。
  - ✅ **P8** `SseEmitterManager` `EMITTER_TIMEOUT` 24h→1h（30s 心跳保活下不影响长期存活）。
- **待办（下一轮，本轮新研究项）**：**P9** AiChatService SSE 编排（~~P9-1 单轮超时 2min→5min 对齐 ✅已修复~~ / P9-2 背压隔离 / P9-5 补集成测试）/ **P11** 仪表盘 ai_task 全量扫描下推 SQL / **P12** memos 检索前导通配 LIKE（规模化时改 pg_trgm）/ S1 CORS 白名单 / P10 散落 class。

---

## 🔴 P1 — 收敛重复的 IP 解析逻辑（已完成 ✅）

- **问题**：客户端 IP 解析逻辑散落在 9+ 处，违反高内聚低耦合，改一处需改多处。
- **位置**：
  - `interceptor/WebUtils.java`
  - `infra/SubjectUtils.java`
  - `aspect/LogAspect.java`
  - `ai/controller/AiImageController.java`、`ai/controller/AiNotifyController.java`
  - `ai/service/AiChatSessionService.java`、`ai/service/AiTtsService.java`、`ai/service/AiVideoService.java`
  - `game/controller/GameController.java`
- **做法**：
  1. 以 `WebUtils.getClientIp(HttpServletRequest)` 为唯一实现（保留其无参重载 `getClientIp()` 用 `RequestContextHolder` 取请求）。
  2. 其他所有位置删除本地重复实现，统一改为调用 `WebUtils.getClientIp(...)`。
  3. 解析规则保持一致：优先 `X-Forwarded-For` 取第一个非 unknown IP，其次 `X-Real-IP`，最后 `request.getRemoteAddr()`，并 trim / 去空白。

---

## 🔴 P2 — 清除 WebUtils 无用 import（顺带消除跨包依赖）

- **问题**：`interceptor/WebUtils.java:7` 含 `import com.stellar.ai.service.AiChatService;` 但代码未使用，造成跨包编译依赖与告警。
- **做法**：删除该 import 行（及相关无用 import），`WebUtils` 只依赖 `javax.servlet.http.HttpServletRequest` 等基础设施包。

---

## 🔴 P3 — 审计/外部调用的 operator 解析改为异步查库

- **问题**：`LogAspect` 与 `ExternalCallLogger` 在**请求线程**里 `sysUserMapper.selectById(userId)` 同步查库解析操作员，每次审计/外部调用多一次同步 SELECT，平白增加请求延迟。
- **位置**：
  - `aspect/LogAspect.java`（约 110–111 行 `resolveOperator`）
  - `infra/ExternalCallLogger.java`（约 82–83 行 `resolveOperator`）
- **做法**：
  1. 切面/日志拦截器只取 `StpUtil.getLoginIdAsLong()` 得到 userId，**不查库**，直接把 userId 传入异步落库路径。
  2. 在 `SysLogService.saveLog`（已是 `@Async("logTaskExecutor")`）的异步线程内，用 userId 查 `sysUserMapper.selectById` 得到 username；或加一层短 TTL（如 5min）的 `Map<Long,String>` 缓存避免重复查。
  3. 保持 `saveLog` 的异步语义不变，仅把"解析 operator"这一步从请求线程挪到异步线程。

---

## 🔴 P4 — RAG 缓存重载去掉粗锁 + 持锁查库

- **问题**：`AiKnowledgeService.getCachedChunks` 在**整个 `vectorCache` 上 `synchronized` 的同时执行 JDBC 查询 + 逐行 `VectorOps.parseVector`**。粗锁使所有知识库重载串行，且 DB IO 期间一直占锁，高并发严重劣化。
- **位置**：`ai/service/AiKnowledgeService.java`（`getCachedChunks` 及 `MemosRagService` 同类逻辑）
- **做法**：
  1. 改 `ConcurrentHashMap<String, X>`，对每个 kb 用 **per-key 锁**（如 `vectorCache.computeIfAbsent(kbId, k -> lock)`）或 `computeIfAbsent` + 双重检查。
  2. 锁内**只做轻量对象构建**；JDBC 查询/向量解析放到锁外（先无锁查询得到原始数据，再加锁放入缓存）。
  3. 目标：不同 kb 之间可并发重载，同一 kb 不重复查库。

---

## 🔴 P5 — RAG 本地缓存改为带驱逐的缓存（防 OOM）

- **问题**：`AiKnowledgeService` 与 `MemosRagService` 用 `Collections.synchronizedMap` 但**永不回收**，随知识库/笔记增多存在 OOM 风险。
- **位置**：`ai/service/AiKnowledgeService.java`、`memos/service/MemosRagService.java`
- **做法**：
  1. 引入 **Caffeine**（`com.github.ben-manes.caffeine:caffeine`）。
  2. 替换 `synchronizedMap` 为 `Caffeine.newBuilder().maximumSize(N).expireAfterWrite(30, TimeUnit.MINUTES).build()`（N 按 kb/笔记数量级定，如 200）。
  3. 保留现有 `invalidateIndexCache` / afterCommit 失效逻辑，改为 `cache.invalidate(key)`。

---

## 🔴 P6 — RAG 缓存多实例一致性

- **问题**：`invalidateIndexCache` 仅失效**本实例**缓存（afterCommit 失效本身是亮点），但多实例部署下其他实例保留陈旧向量/BM25 索引，造成数据陈旧。
- **位置**：`ai/service/AiKnowledgeService.java`（`invalidateIndexCache`）、`memos/service/MemosRagService.java`
- **做法**（任选其一，推荐 A）：
  - **A（推荐）**：复用现有 Redis pub/sub（与 SSE 通道一致），在失效时发布一个 cache-invalidation 事件，各实例订阅后 `cache.invalidate(key)`。
  - **B**：将 RAG 索引缓存迁移为 Redis 集中缓存（注意向量序列化成本，仅适合索引元数据而非全量向量）。
  - **C（单实例可接受时）**：在部署文档明确标注"RAG 缓存为单实例假设，多实例需配合 A/B"，并加监控告警缓存命中率骤降。

---

## 🟡 S1 — CORS 收紧为白名单

- **问题**：`SaTokenConfig.corsFilter()` 用 `allowCredentials(true)` + `addAllowedOriginPattern("*")`，任意源可带凭据访问。
- **位置**：`config/SaTokenConfig.java`
- **做法**：
  1. 将通配改为前端域名白名单：`List.of("https://your-frontend.com", "http://localhost:5173")`，用 `allowedOriginPatterns(patterns)` 注入。
  2. 凭据仍为 true（需携带 cookie），但源限定为已知前端。

---

## 🟡 S2 — 关闭 Redis Jackson defaultTyping（已完成 ✅）

- **问题**：`RedisConfig.cacheManager()` 用 `GenericJackson2JsonRedisSerializer` 且 `activateDefaultTyping(...)` 开启，存在反序列化 gadget 风险。
- **位置**：`config/RedisConfig.java`（cacheManager 已关）、`RedisConfig.redisTemplate`（已关）、`infra/CacheInvalidationListener.java`、`ai/service/AiNotifyListener.java`（均已改类型化序列化）
- **做法**（两步，缺一不可）：
  1. **`redisTemplate` bean 关 typing**：改为 builder 形式并显式不开启 typing（与 cacheManager 一致）：
     ```java
     GenericJackson2JsonRedisSerializer ser = GenericJackson2JsonRedisSerializer.builder()
         .objectMapper(new ObjectMapper().registerModule(new JavaTimeModule())).build();
     ```
  2. **pub/sub 改类型化序列化（关键，否则会破坏 P6 与 AI 通知）**：`CacheInvalidationListener` 与 `AiNotifyListener` 改用**类级** 序列化器 `Jackson2JsonRedisSerializer(具体消息类.class)`（按类名定型、不依赖多态 `@class`，无 gadget 风险且类型保真）直读直出，去掉 `instanceof` 判型；发布端复用 `redisTemplate`（关 typing 后 payload 即纯 JSON，与类型化读端兼容）：
     ```java
     // 订阅端
     private final Jackson2JsonRedisSerializer<AiNotifyMessage> ser =
         new Jackson2JsonRedisSerializer<>(AiNotifyMessage.class);
     AiNotifyMessage msg = ser.deserialize(message.getBody());
     ```
  3. 已跑 `AiNotifyListenerTest`/`AiNotifyPublisherTest`/`CacheInvalidationListenerTest` 单测，确认多实例失效广播与 AI 通知在 typing 关闭后仍能正常 round-trip。
- **为什么必须做**：原先 cacheManager 已关 typing，但 `redisTemplate` + 两个 Listener 仍开。这种不一致本身就是隐患，且任何人后续"补关 redisTemplate 的 typing"都会因 `instanceof` 失败而**静默破坏** P6 与 AI 通知——现已在同一轮一次性收尾。

---

## 🟡 S3 — 默认管理员密码强制改密（已完成 ✅ 硬阻断）

- **问题**：`DataInitializer` 默认管理员密码硬编码 `123456`，且仅软拦截。
- **位置**：`config/DataInitializer.java`、`system/entity/SysUser.java`、`system/service/UserService.java`、`system/service/AuthService.java`（login）、`resources/schema.sql` + `resources/db/init.sql`
- **现状（已落地）**：
  - `sys_user` 加 `must_change_password` 列（schema 建表 + 幂等 `ALTER TABLE ADD COLUMN IF NOT EXISTS` 兼容老库）；`DataInitializer` 播种 admin 带标记 `1`；登录在 `LoginResult.userInfo` 返回该标记；`UserService` 改密成功清 0（单测 `AuthServiceTest`/`UserServiceTest` 已覆盖，**13/13 绿**）。
- **缺口（软拦截）**：`AuthService.login` 仍 `StpUtil.login(user.getId())` 无条件放行——仅把标记回传前端由路由守卫拦截。非配合客户端可凭 `123456` 拿到有效 token。
- **做法（二选一，建议 A）**：
  1. **A（真·强制）**：`AuthService.login` 密码校验通过后、调用 `StpUtil.login` 后，若 `user.getMustChangePassword() == 1` 向 Sa-Token 会话写 `mustChangePassword` 标记；`AuthInterceptor` 对带标记会话仅放行 `/user/change-password`、`/user/info`、`/auth/logout`，其余受保护接口一律 403；`UserService.changePassword` 改密成功同步清会话标记。配合前端拦截页。
  2. **B（维持软拦截）**：确认前端路由守卫已接 `userInfo.mustChangePassword`，在 `==1` 时强制跳 `/system/change-password` 且禁止其它导航；并在文档标注此为软约束、信任前端。
- **落地**：已选 **A** 完成硬阻断（见进度跟踪），前端守卫/登录页/LayoutHeader 原有软拦截保留作为第一道（正常客户端到不了 403 分支），后端拦截为兜底。

---

## 🟡 P7 — HikariCP 连接池上调

- **问题**：`maximum-pool-size=8` 偏小，异步 AI worker（aiTask 4 + aiTool 4）+ 请求线程并发持连接时易打满，等待队列堆积。
- **位置**：`application.yml`（datasource.hikari）
- **做法**：
  1. 将 `maximum-pool-size` 调到 15~20，并设置 `minimum-idle`（如 5）。
  2. 保留监控页 `pendingConnections` 预警，按实际压测微调。

---

## 🟢 S5 — 增加 MyBatis-Plus 防全表拦截器

- **问题**：`MyBatisPlusConfig` 缺 `BlockAttackInnerInterceptor`，无 WHERE 的 `update()/delete()` 可能误伤全表。
- **位置**：`config/MyBatisPlusConfig.java`
- **做法**：在 `MybatisPlusInterceptor` 链中加入 `new BlockAttackInnerInterceptor()`（注意顺序：放分页/乐观锁拦截器之后）。

---

## 🟢 P8 — 收紧 SSE 超时

- **问题**：`SseEmitterManager` 的 `EMITTER_TIMEOUT = 86400000`（24h）过长，长连接占用资源。
- **位置**：`infra/SseEmitterManager.java`
- **做法**：按业务收紧到如 1 小时（3600000ms）；依赖现有 30s 心跳保活，超时由心跳续期逻辑友好关闭。

---

## 🟢 P10 — 清理根目录散落 class 文件

- **问题**：项目根 `com/baomidou/.../TableInfoHelper.class` 是误放的编译产物（已被 `**/*.class` gitignore，仍应清理）。
- **做法**：删除仓库根目录下的 `com/` 文件夹（确认无源码），避免产物污染与误提交风险。

---

## 🔴 P9 — AiChatService SSE 编排深入审查（已研究，含实质问题）

> 已读实码（1059 行）逐方法分析。整体**设计良好**：生命周期回调干净无泄漏、客户端断开优雅处理、计费有 usage 兜底、日志隐私意识强（截断、不落响应体）。但发现以下实质问题。

### P9-1（✅ 已修复，保留分析备查）单轮流式 emitter 超时 2min < HTTP 5min → 慢模型被掐断 + 漏计费
> **核验结论（2026-08-20 复看最新代码）**：该项**已修复**。`AiChatService.java:70` 现为 `private static final long CHAT_SSE_TIMEOUT = Duration.ofMinutes(5).toMillis();`，第 135 行 `new SseEmitter(CHAT_SSE_TIMEOUT)` 与第 149 行上游 HTTP `.timeout(Duration.ofMinutes(5))` 已对齐。原 2min 硬编码已不存在，慢模型不再被掐断、计费不再漏。下方为原始分析，仅供追溯。
- **位置（历史）**：`ai/service/AiChatService.java:127`（`new SseEmitter(120000L)`）vs `:141`（`.timeout(Duration.ofMinutes(5))`）。
- **问题（历史）**：`doStreamChat` 的 SSE emitter 超时仅 **2 分钟**，但上游 LLM HTTP 请求超时是 **5 分钟**。若模型 3 分钟才出完，`onTimeout` 触发、Spring 自动 complete；此时 HTTP 仍在进行，`parseStream` 后续 `emitter.send()` 抛 `ResponseBodyEmitter has already completed` → 被 `isClientDisconnect()` 判为客户端断开**静默 return**，**不再执行 `recordTokenUsage`/`recordHistory`**。结果：用户 2 分钟被掐断、且这次调用**不计费、不落历史**。
- **对比**：多轮/工具链路走 `createChatEmitter()`（`:365` 用 5 分钟），与 HTTP 超时一致，无此问题——**单轮与多轮超时不一致本身就是 bug 信号**。
- **做法（已实施）**：
  1. ✅ 抽常量 `CHAT_SSE_TIMEOUT = Duration.ofMinutes(5).toMillis()`，`doStreamChat` 与 `createChatEmitter` 统一用它（≥ 最坏 HTTP 超时）。
  2. **顺带提醒**：`SseEmitterManager.EMITTER_TIMEOUT` 已改 1h，但聊天 emitter 是 `new SseEmitter(...)` 自建、不进 manager，P8 对聊天无效。若要统一，聊天 emitter 也应引用同一常量（或挂到 manager）。
  3. 修复后 `isClientDisconnect` 对"已 complete 后 send"的静默 return 仍合理（真断开），无需改。

### P9-2（中）SSE 分片 `emitter.send()` 阻塞共享 HttpClient worker 线程（背压）
- **位置**：`parseStream`（`:861-905`）在 `HttpClient.sendAsync` 的响应读取线程里同步调用 `emitter.send()`。
- **问题**：`httpClient` 是字段级单例（`:64`），`sendAsync` 默认用其内置 executor。`emitter.send()` 是同步阻塞写——**客户端慢时该写阻塞，占用 HttpClient 的 worker 线程**。并发慢客户端多时，HttpClient executor 被占满 → 所有 AI 请求被拖慢（HttpClient 默认 cached 无限池，仍是隐性瓶颈）。
- **做法**（个人站可暂缓，规模化必做）：SSE 分片发送改投到独立发送线程池（把 `emitter.send` 包一层专用 executor / `SseEmitter` scheduler），避免反向阻塞上游读取线程；或显式给 `httpClient` 配有界、命名清晰的 executor 并监控队列。

### P9-3（低/加固）流式接口缺单主体并发上限
- **位置**：`streamChat`/`streamMultiChat*` 入口。
- **问题**：未对"同一 account/ip 的并发 SSE 流"做上限；工具链路还会再发起一次上游 HTTP 调用（`doSecondStream`）。`aiToolExecutor` 仅 4 线程（`AbortPolicy`，被拒时走 `catch` 优雅报错，不崩，但用户看到失败）。
- **做法**：确认聊天 SSE 入口已挂 `@PublicAccess`+`@RateLimit`（与文案/IP 日限一致）；如需更强隔离，加"单主体最大并发流"信号量。个人站当前风险低。

### P9-4（信息）工具调用会话计费≈2 倍
- **位置**：`:721`（第一次判定调用计费）+ `:806`（第二次生成调用计费）。
- **说明**：工具调用会真实发起两次 LLM 调用（判定 + 生成），各计一次 token，属合理；但语义上"用户看到的一段回复"在统计里≈2 倍 token。仅作口径说明，非 bug。

### P9-5（建议）补流式编排的集成测试
- **做法**：Mock `HttpClient`/上游 SSE 流，覆盖①正常流式转发+`done`+计费；②上游 HTTP 非 200 → `sendError`；③客户端中途断开 → 优雅 return 不崩；④工具调用两阶段（判定有 tool_calls → 执行 → 第二次流）；⑤P9-1 修复后"慢模型不被掐断"。把 `AiChatService` 覆盖率从 17.2% 拉起。

---

## 🔴 P11 — 仪表盘 ai_task 全量扫描（性能时间炸弹）

- **问题**：`system/service/DashboardService.java:67-70` 的 `buildTaskStatByType` 每次刷新仪表盘都 `aiTaskMapper.selectList(new LambdaQueryWrapper<AiTask>().eq(taskType))`——**把该类型全部历史行拉进内存再 Java 遍历**算 total/today/success/周环比。`ai_task` 随每次 AI 调用无限增长（文本生成、图片、视频、TTS 都记），**表越大每次仪表盘越慢、内存越高**，典型"上线一时爽、运行半年卡"。
- **对比**：同文件 `sys_ai_usage` 统计走 `selectTotalsBetween(start,end)` **有界 SQL 聚合**（命中 `idx_sys_ai_usage_create_time`），作者懂这手法，task 统计漏了。`:156-159` 的 tts 统计同样全量扫描。
- **位置**：`DashboardService.buildTaskStatByType`（`:66-70`）、tts 统计（`:156-159`）。
- **做法**（聚合下推 SQL，索引已就绪）：
  1. `AiTaskMapper` 新增聚合方法：`selectCountByTypeAndStatus(taskType, status)`、`selectCountByTypeAndStatusSince(taskType, status, since)`（命中 `idx_ai_task_request_time`）、近7日/前7日 `request_time` 范围 count。
  2. `buildTaskStatByType` 改为多次有界 `selectCount`，不再 `selectList` 全表。
  3. `:111` `fileMapper.selectList` 若也是无界全量，同样改 count/有界查询。

---

## 🟡 P12 — Memos 全文检索走前导通配 LIKE（扩展天花板）

- **问题**：`memos/service/MemosService.java:694-696` 搜索用 `like(content, word) or like(uid, word) or like(tags, word)`，多词之间 AND。**`content` 是前导通配 `LIKE '%word%'`——任何 B-tree 索引都用不上，必走全表顺序扫描**。`idx_memos_note_uid` 仅对 `uid` 前缀匹配有用，对 `content` 检索无效。
- **位置**：`MemosService` 搜索分支（约 `:690-700`）。
- **现状评估**：个人笔记量（数百~数千条）下延迟可忽略，**当前不必改**；但这是明确扩展天花板——笔记上万后搜索随表增长线性变慢。
- **做法**（按需，规模化时做）：
  1. **PostgreSQL 全文检索**：`content` 建 `tsvector` 生成列 + GIN 索引，`to_tsquery` 替代 LIKE（中文需 `zhparser` 插件，否则按空格分词对中文弱）。
  2. **或 pg_trgm 三元组索引**（推荐，改动最小）：`CREATE INDEX ... USING gin(content gin_trgm_ops)`，让 `LIKE '%word%'` 也能走索引（对中文子串匹配友好，无需分词插件）。
  3. 多词 AND 改为 `tsquery` 的 `&` 连接或 `word1% & word2%`（pg_trgm 用 `%` 操作符）。

---

## 实施顺序建议（更新）

- ✅ **第一轮（已完成 commit `82157b7`）**：P1 + P2 + P3 + P4 + P5 + P6
- ✅ **第二轮（安全与连接池，已完成）**：S2 全部收尾（cacheManager + redisTemplate 关 typing，pub/sub 改类型化序列化）+ S3 硬阻断（登录会话标记 + AuthInterceptor 拦截 + 改密清标记）+ S5 BlockAttack 拦截器 + P7 HikariCP 连接池上调 + P8 SSE 超时收紧
- ⬜ **第三轮（性能深挖，本轮新研究）**：**P9-1** 单轮流式 emitter 超时 2min→5min 对齐（防慢模型掐断+漏计费）→ **P11** 仪表盘 ai_task 全量扫描下推 SQL 聚合 → **P9-2** SSE 发送背压隔离（规模化）→ **P9-5** 补流式编排集成测试 → **P12** memos 检索前导通配 LIKE（规模化时改 pg_trgm）
- ⬜ **第四轮（清理）**：S1 CORS 白名单 → P10 散落 class 清理
