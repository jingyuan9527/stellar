# 后端架构优化与发展方向（14 个方向）

> 用途：直接复制给 AI 程序员，作为下一阶段整改与演进的 backlog。
> 每条含：**类别 / 问题 / 位置（文件:行号，均经实码核实）/ 影响 / 改进方向 / 优先级 / 工作量**。
> 约束：遵循团队铁律（高内聚低耦合、日志详实、不引入无关优化、危险操作先确认、中文提交、文档与代码一致）。
>
> **核实说明**：前序审查中的 P9-1（聊天 SSE 超时 2min < HTTP 5min）在当前代码**已修复**（`AiChatService.java:70` 的 `CHAT_SSE_TIMEOUT=5min` 与 HTTP `Duration.ofMinutes(5)` 对齐，第 135 行用该常量），故不列入。本列表仅收录当前仍成立的问题与发展方向。
>
> **P13 已解决（DB 驱动单调度器）**：旧"每视频任务独占 `@Async("aiTaskExecutor")` 线程 + Thread.sleep 轮询"会饿死图片生成并反压 HTTP 线程，已改 `AiVideoTaskWorker` 为 `@Scheduled` 唯一线程每 5s 扫 `ai_task` 待轮询视频任务（`AiTaskMapper.selectPendingVideoTasks`），逐条单次查供应商，generating 后推进 `update_time` 节流，不再占业务线程池。故该项不再列入。

---

## 待优化点（代码层面的具体问题，共 9 项）

### 1. S1 — CORS 通配凭据（高 / 低）
- **问题**：`corsFilter()` 用 `addAllowedOriginPattern("*")` + `setAllowCredentials(true)`，任意域名带凭证可跨域调用。
- **位置**：`config/SaTokenConfig.java:54-55`
- **影响**：CSRF / 跨站凭证泄露面放大；生产不应对任意 origin 开放凭据。
- **改进方向**：改为前端域名**白名单**（`List<String>` 配置项，只放行 known origins）；白名单外直接拒绝。不破坏 `setAllowCredentials`（白名单下仍可用）。
- **优先级**：高　**工作量**：低

### 2. S4 — 本地明文凭据（高 / 低）
- **问题**：`application-local.yml` 明文写入远程 PostgreSQL 密码 `password_****` 与带密码的 Redis URL（已被 gitignore 不入库，但本地明文弱口令风险仍在）。
- **位置**：`src/main/resources/application-local.yml`
- **影响**：本地机器/备份泄露即暴露数据库与 Redis；不符合最小暴露原则。
- **改进方向**：凭据改为环境变量 / Spring `${{ENV}}` 占位（`DB_PASSWORD`、`REDIS_URL`），本机用 `.env` 或系统环境变量注入；仓库仅保留 `application-local.yml.example` 模板。
- **优先级**：高　**工作量**：低

### 3. P10 — 根目录散落编译产物（低 / 低）
- **问题**：仓库根目录 `com/baomidou/.../TableInfoHelper.class` 等散落 class（MyBatis-Plus 元数据缓存产物），虽已被 `**/*.class` gitignore，但属脏文件。
- **位置**：`com/baomidou/.../*.class`（项目根）
- **影响**：仓库整洁度、误提交风险（若 gitignore 调整）。
- **改进方向**：删除根目录 `com/`，将此类产物纳入 `.gitignore` 明确条目或构建时输出到 `target/`；CI 增加"根目录禁止 .class"检查。
- **优先级**：低　**工作量**：低

### 4. P11 — 仪表盘全表扫描（高 / 中）
- **问题**：`buildTaskStatByType` 每次刷新仪表盘都把 `ai_task` 整张表按 `task_type` **`selectList` 拉进内存遍历**。`ai_task` 随每次 AI 调用无限增长，是"运行半年后卡顿"的时间炸弹。
- **位置**：`system/service/DashboardService.java:67-70`
- **影响**：DB 压力 + 内存膨胀随数据量线性恶化；同文件 `sys_ai_usage` 已正确用有界 `selectTotalsBetween` 聚合（命中 `idx_sys_ai_usage_create_time`），此处是疏漏。
- **改进方向**：聚合下推 SQL——`SELECT task_type, status, COUNT(*) GROUP BY task_type, status` 并 `WHERE request_time >= :since`，命中已有 `idx_ai_task_request_time`；或按时间窗（近 7/30 日）分页聚合。
- **优先级**：高　**工作量**：中

### 5. P12 — 笔记搜索前导通配（中 / 中）
- **问题**：memos 全文搜索用 `LIKE '%word%'` 对 `content`/`uid`/`tags` 检索，前导通配**无法走任何 B-tree 索引**，随笔记量增长变慢。
- **位置**：`memos/service/MemosService.java:694-696`
- **影响**：个人量可接受，但规模化后成为检索瓶颈；多词 AND 进一步放大。
- **改进方向**：PostgreSQL 改用 `pg_trgm`  trigram GIN 索引（`CREATE INDEX ... USING gin(col gin_trgm_ops)`）+ `<->`/`%` 相似度，或迁移到 `tsvector` 全文索引；前端高亮由应用层 `<mark>` 保留。
- **优先级**：中　**工作量**：中

### 6. P14 — AI 写入缺乏事务边界（高 / 中）
- **问题**：`AiImageTaskWorker.doGenerateAsync` 是 `@Async` 但**无 `@Transactional`**。内部多步写：`fileMapper.insert` → `aiTaskMapper.updateById`（completed + fileId）→ `sysAiUsageService.record` → `publisher.publish` 彼此独立。任一步失败都会留下**孤儿文件 / 任务状态不一致 / 计费缺失**。
- **位置**：`ai/service/AiImageTaskWorker.java:58-114`
- **影响**：部分失败导致数据不一致（如文件已存库但任务仍 pending，或任务 completed 但计费未记）。
- **改进方向**：把"文件落库 + 任务状态更新"包进同一事务（`@Transactional(rollbackFor=Exception.class)`，注意 `@Async` 与事务需分 Bean 或同方法内用 `TransactionTemplate`）；计费记录作为最终一致性的补偿（可异步，但需保证至少一次）。可参考 `AiKnowledgeService` 用 `TransactionTemplate` 编程式事务的写法。
- **优先级**：高　**工作量**：中

### 7. P15 — 限流 INCR+EXPIRE 非原子（中 / 低）
- **问题**：`tryIncr` 先 `INCR`，再 `if(now==1) EXPIRE 1天`。两步非原子——若在 INCR 后、EXPIRE 前进程崩溃/Redis 故障转移，该 key **永不过期**，成为 Redis 内存泄漏（孤儿键）。
- **位置**：`infra/RateLimitService.java:37-45`
- **影响**：长期运行累积无 TTL 的限流键，缓慢吃 Redis 内存；多实例下更明显。
- **改进方向**：用 **Lua 脚本**保证 `INCR`+`EXPIRE` 原子，或首调用用 `SET key 1 NX EX 86400`（不存在才设并带 TTL），已存在则 `INCR`。同时把超限判定放在 INCR 后（当前逻辑可接受）。
- **优先级**：中　**工作量**：低

### 8. P16 — 二进制入 DB bytea + 整段入堆（中 / 高）
- **问题**：上传文件与 AI 生成图片把**完整二进制存入 PostgreSQL `BYTEA` 列**，且读取时整段载入 JVM 堆（`entity.setData(file.getBytes())` / `file.setData(imageBytes)`）。大图/音频/视频 URL 下载（`downloadFile` 读满 `GENERATED_IMAGE_MAX_BYTES` 到 `byte[]`）都会瞬时占满堆。
- **位置**：`system/service/FileService.java:63`、`ai/service/AiImageTaskWorker.java:87`、`src/main/resources/schema.sql:238`（`data BYTEA`）
- **影响**：DB 体积膨胀、备份慢、大对象拉垮连接；堆内整段缓冲是 OOM 隐患（尤其视频类未来扩展）。
- **改进方向**：**对象存储化**——二进制改存 MinIO / S3 / OSS，DB 仅存 `object_key` + `size` + `content_type`；下载走**流式**（`ResponseBody` 直传 `OutputStream` 或预签名 URL 重定向），避免整段入堆。短期至少对 `downloadFile`/`getFull` 改流式读取。
- **优先级**：中（个人站可接受，规模化必改）　**工作量**：高

### 9. P17 — TTS 每片段新建 WebSocket 且超时未显式关闭（中 / 中）
- **问题**：`synthesizeChunk` 对**每个文本分片新建一条 WebSocket**（新 TLS 握手）到 Edge TTS；`audioFuture.get(30, SECONDS)` 超时后**仅抛异常，未显式 `webSocket.abort()` 关闭连接**，连接被遗弃（HttpClient 长生命周期但 WS 句柄泄漏）。
- **位置**：`tts/service/TtsService.java:149`（`buildAsync` 每片新建）、`:163`（超时未关）
- **影响**：长文本 = N 次 TLS 握手风暴（延迟+被限流风险）；超时路径连接泄漏，累积占用文件句柄/连接。
- **改进方向**：①**复用单条 WebSocket** 跨分片发送多条 SSML（Edge TTS 支持同一连接连续请求，靠 `turn.end` 聚合音频）；②超时/异常路径显式 `webSocket.abort()` 释放；③若保留多连接，用连接池或限制并发分片数。
- **优先级**：中　**工作量**：中

---

## 未来发展开发方向（架构演进，共 5 项）

### 10. F1 — 多实例 / 集群就绪（高 / 高）
- **现状**：RAG 缓存失效已用 Redis pub/sub 广播（P6），限流已用 Redis 共享计数——但 **Sa-Token 会话默认内存存储**（`SaTokenConfig` 未配置 `SaTokenDao` Redis 化），登录态无法跨实例共享；SSE 推送也仅单实例内有效。
- **位置**：`config/SaTokenConfig.java`（无 `SaTokenDao`/Redis session）
- **方向**：①引入 `SaTokenDao` 以 Redis 为会话后端（`StpUtil` 登录态跨实例）；②明确部署约束文档（单实例 or 前置粘滞/共享会话）；③若需跨实例 SSE，引入消息总线（Redis pub/sub 已具备）做 `SseEmitterManager` 跨实例广播。
- **价值**：为水平扩容、蓝绿部署、零停机发布打基础。

### 11. F2 — 后台任务框架化（高 / 高）
- **现状**：视频轮询已改 DB 驱动单调度（见标题 P13 已解决说明）；图片生成仍靠 `@Async` 线程池无统一任务调度、重试、可观测。
- **方向**：引入**持久化任务表驱动的调度器**（参考现有 `ai_task` 表）：①图片生成失败可**有限重试**（指数退避，当前 503 直接 failed）；②任务状态机（pending/running/success/failed/timeout）可视化；③可选升级到消息队列（RabbitMQ/Kafka）做削峰与可靠投递。
- **价值**：稳定性、可观测性、可重放的异步体系，支撑规模化。

### 12. F3 — 可观测性升级（中 / 中）
- **现状**：`monitor/` 包已做应用内实时快照（JVM/HTTP/HikariCP/慢变量采样），质量高但**仅内部轮询查看**，未对外暴露标准指标。
- **方向**：①接入 **OpenTelemetry** 做分布式链路追踪（AI 调用链、外部供应商调用、DB 查询）；②暴露 **Prometheus** 指标（`/actuator/prometheus`），复用 `HttpRequestMetrics`/`MonitorService` 已有计数；③接 **Grafana** 看板（应用健康 + AI 调用量/耗时/错误率/限流命中）；④日志接入结构化 + 集中（Loki/ELK），复用现有 `traceId`。
- **价值**：从"能看快照"到"能告警、能回溯、能容量规划"。

### 13. F4 — AI 供应商故障转移与负载均衡（中 / 中）
- **现状**：`AiModelService.resolveConfig(modelId)` 解析出单一 provider 配置直接调用；无多供应商备选、无权重、无熔断。
- **方向**：①同模型类型配置**多 provider 按权重/优先级**选择；②调用失败（429/5xx/超时）自动**故障转移到下一个** provider；③引入**熔断**（如 Resilience4j）避免单供应商抖动拖垮全站；④按成本/延迟做**负载均衡**策略。
- **价值**：提升 AI 服务可用性（单供应商宕机不中断业务），支撑多密钥/多账号配额管理。

### 14. F5 — API 契约与版本化（低 / 中）
- **现状**：接口无统一契约文档，前端与后端靠约定；无版本前缀，未来破坏性变更难以向后兼容。
- **方向**：①接入 **SpringDoc/OpenAPI** 自动生成接口文档（复用现有 `@Valid`/`@Schema` 可平滑过渡）；②对外 API 加版本前缀（`/api/v1/...`），内部仍用现有路由；③CI 增加 **contract test**（如 Pact）防止前后端契约回归；④公开接口（游客可访问部分）单独标注契约稳定性。
- **价值**：协作效率、第三方集成可能、演进安全。

---

## 实施顺序建议（按收益/风险排序）

1. **立即（高收益低风险）**：S1、S4、P10（配置/清理，半天搞定）
2. **性能与稳定性（高优先级）**：P11（仪表盘全扫描）→ P14（事务边界）→ P15（限流原子性）
3. **规模化准备（中优先级）**：P16（对象存储）→ P17（TTS 连接复用）→ P12（memos 索引）
4. **架构演进（持续发展）**：F1（多实例）→ F2（任务框架）→ F3（可观测性）→ F4（供应商容错）→ F5（API 契约）

> 注：P9 系列（SSE 超时一致性）经核实已在最新代码修复，不在此列；若后续引入更长视频/更大模型，需重新评估 SSE 与 HTTP 子请求超时（当前 `AiChatService` 仍有 `ofMinutes(2)` 的子请求超时，见第 246/588/681 行，属工具调用链路，必要时统一治理）。
