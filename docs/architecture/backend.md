# Backend

## Auth

Sa-Token `Authorization: Bearer`，`AuthInterceptor` 默认全拦仅 `@PublicAccess` 放行，未登录 401。`excludePathPatterns`：`/auth/login /error /actuator/health`。登录爆破：`stellar:login-attempt:{ip}` + `stellar:login-fail:{username}`。首登强制改密：`must_change_password` + 会话 `mustChangePassword` 硬阻断，仅放行改密/用户信息/登出。会话 7 天绝对有效期。

## Redis

Lettuce + `sa-token-redis-jackson` + Spring Cache。`RedisConfig`：`RedisTemplate` JSON + `RedisCacheManager` 前缀 `stellar:`，关 `defaultTyping`（防 gadget），旧格式存量条目已随 TTL 自然过期（一次性启动清理类已移除）。pub/sub：`stellar:ai:notify` / `stellar:cache:invalidate` 用类级 `Jackson2JsonRedisSerializer`。`CacheConstants` 统一前缀；`@Cacheable` 返回 `List` 需 `collect(Collectors.toList())`。落点：`dict / menu-visibility / profile-project / ai-model / ai-provider / setting`。

## 常规

- 密码 BCrypt；响应 `Result<T>` + `GlobalExceptionHandler` + MDC `traceId`
- MyBatis-Plus：`sys_user.deleted` 逻辑删除，`BlockAttackInnerInterceptor` 防全表
- 操作日志：`@Log` + `LogAspect` `@Async("logTaskExecutor")` 异步落 `sys_log`，脱敏 `password/oldPassword/newPassword/confirmPassword/token/apiKey/secretKey`
- 外部调用：`ExternalCallLogger`（infra，只做上下文捕获/截断）+ `CallLogSink` 缝，system 侧 `SysLogCallLogSink` 落 `sys_log` module=外部调用
- 运行日志：`logback-spring.xml`，`stellar.log` 归档，`LOG_LEVEL` 控制，MDC traceId

## AI / 文件 / 限流 / 其他

- AI 多供应商多模型：`sys_ai_provider/model` + 字典 `model_type`，全局开关 `sys_setting`，`AiProviderService/AiModelService` + `AiResolvedConfig`，SSE `SseEmitter` 代理，`ai_task` 统一历史，内置模板 3 套
- LLM 协议层：`ai.protocol.LlmChatClient` 缝（URL 形态/请求构建/发送/SSE 解析/usage 解析），当前实现 `OpenAiHttpChatClient`（OpenAI 兼容）；新增原生协议加实现类即可。`AiChatService` 只做编排，SSE 写通道在 `SseEmitterChannel`，token 计费/历史落库在 `AiUsageRecorder`；非流式 agent 循环在 `AiAgentLoopService`（工具定义与执行由调用方注入，上限 5 轮），首个工具 `ai.tool.WebPageFetchTool`（fetch_url，jsoup 抓正文，SafeUrlValidator 防 SSRF，超时 10s/正文 2MB/回传截断 4000 字符）
- URL 安全：`SafeUrlValidator` 限公开 http/https，禁重定向，限长读取（图 20MB/视频 200MB）
- 计费：`sys_ai_usage`，`stream_options.include_usage` 精确否则估算，仪表盘聚合
- 任务通知：`SseEmitterManager` + `AiNotify` Redis pub/sub，30s 心跳，前端 `store/aiNotify` 重连
- RAG 缓存：Caffeine + `CacheInvalidation` 广播
- 文件：`sys_file` BYTEA，`POST /file/upload` 白名单（`isPublic` 参数标记游客可见）；`GET /file/{id}` 免登录但仅 `is_public=1` 游客可读，私有文件仅上传者本人可读（防 IDOR 枚举下载），存量头像/海螺预设音频由 schema.sql 幂等回填
- 限流：`@RateLimit(daily, loginDaily)` + `RateLimitInterceptor` + Redis Lua 原子化；游客按 IP、登录用户按 userId 双档计数（默认 50/200，`rate-limit.default-daily/default-user-daily`）
- IP 解析：`WebUtils.getClientIp` 唯一实现，仅当 remoteAddr 命中可信代理白名单（`stellar.security.trusted-proxies`，精确 IP 或 IPv4 CIDR）才采信 XFF/X-Real-IP，防伪造头绕过限流
- CORS：`stellar.cors.allowed-origins` 显式域名白名单（逗号分隔，凭据型跨域禁止通配，启动时校验）
- 游戏/海螺/健康/备忘/Memos/Webhook/监控/TTS：见源码分包 `com.stellar.{ai,tts,game,system,memos,monitor,infra,dashboard}`，`ai_task` 统一历史，`WebUtils.getClientIp` 唯一 IP 解析
- 模块依赖方向（单向，禁环）：`infra` 不依赖任何特性模块；特性模块间经 service/端口缝访问（如 `ai→tts` 走 `tts.port`、RAG 外部检索走 `ExternalRetriever` 缝），不直连他模块 Mapper；`dashboard` 为聚合包可依赖各特性模块；跨模块文件落库走 `FileService.create/deleteById`
- Memos：验签在 infra `HmacWebhookVerifier` + `MemosWebhookGuard`（去重），同步互斥 `RedisMutex`，状态记录 `MemosSyncLogStore`，标签文本处理 `MemosTagCodec`；AI 打标签经 `AiAgentLoopService` + `WebPageFetchTool`（fetch_url）支持链接笔记先抓网页再打标，模型不支持 tools 自动降级纯文本

DBX：`opencode.json` `dbx` MCP，改后重启 opencode 生效。
