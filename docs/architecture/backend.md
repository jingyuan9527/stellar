# Backend

## Auth

Sa-Token `Authorization: Bearer`，`AuthInterceptor` 默认全拦仅 `@PublicAccess` 放行，未登录 401。`excludePathPatterns`：`/auth/login /error /actuator/health`。登录爆破：`stellar:login-attempt:{ip}` + `stellar:login-fail:{username}`。首登强制改密：`must_change_password` + 会话 `mustChangePassword` 硬阻断，仅放行改密/用户信息/登出。会话 7 天绝对有效期。

## Redis

Lettuce + `sa-token-redis-jackson` + Spring Cache。`RedisConfig`：`RedisTemplate` JSON + `RedisCacheManager` 前缀 `stellar:`，关 `defaultTyping`（防 gadget），存量旧条目 `RedisCacheBootstrap` 启动清理。pub/sub：`stellar:ai:notify` / `stellar:cache:invalidate` 用类级 `Jackson2JsonRedisSerializer`。`CacheConstants` 统一前缀；`@Cacheable` 返回 `List` 需 `collect(Collectors.toList())`。落点：`dict / menu-visibility / profile-project / ai-model / ai-provider / setting`。

## 常规

- 密码 BCrypt；响应 `Result<T>` + `GlobalExceptionHandler` + MDC `traceId`
- MyBatis-Plus：`sys_user.deleted` 逻辑删除，`BlockAttackInnerInterceptor` 防全表
- 操作日志：`@Log` + `LogAspect` `@Async("logTaskExecutor")` 异步落 `sys_log`，脱敏 `password/token/apiKey`
- 外部调用：`ExternalCallLogger` + `sys_log` module=外部调用
- 运行日志：`logback-spring.xml`，`stellar.log` 归档，`LOG_LEVEL` 控制，MDC traceId

## AI / 文件 / 限流 / 其他

- AI 多供应商多模型：`sys_ai_provider/model` + 字典 `model_type`，全局开关 `sys_setting`，`AiProviderService/AiModelService` + `AiResolvedConfig`，SSE `SseEmitter` 代理，`ai_task` 统一历史，内置模板 3 套
- URL 安全：`SafeUrlValidator` 限公开 http/https，禁重定向，限长读取（图 20MB/视频 200MB）
- 计费：`sys_ai_usage`，`stream_options.include_usage` 精确否则估算，仪表盘聚合
- 任务通知：`SseEmitterManager` + `AiNotify` Redis pub/sub，30s 心跳，前端 `store/aiNotify` 重连
- RAG 缓存：Caffeine + `CacheInvalidation` 广播
- 文件：`sys_file` BYTEA，`POST /file/upload` 白名单，`GET /file/{id}` 公开
- 限流：`@RateLimit` + `RateLimitInterceptor` + Redis Lua 原子化，登录跳过
- 游戏/海螺/健康/备忘/Memos/Webhook/监控/TTS：见源码分包 `com.stellar.{ai,tts,game,system,memos,monitor,infra}`，`ai_task` 统一历史，`WebUtils.getClientIp` 唯一 IP 解析

DBX：`opencode.json` `dbx` MCP，改后重启 opencode 生效。
