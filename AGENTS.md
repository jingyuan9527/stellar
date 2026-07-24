# AGENTS.md

Compact guidance for OpenCode sessions working in this repo. Verify against the executable config if anything seems stale.

> **铁律优先**：本人的硬性要求见 [`RULES.md`](./RULES.md)（需求先问清、代码高内聚低耦合、日志详实、前端统一+移动端适配、不做无关优化、危险操作必确认、中文提交、文档与代码一致、提交前查隐私泄露）。该文件优先级高于本文件，冲突以 `RULES.md` 为准。

## 项目定位

Stellar 是**个人知识/实验沉淀池**，不是纯后台管理工具。游客可免登录浏览落地页与案例库、体验所有工具（受 IP 单日限流保护）；管理功能登录后可见。公开页与管理后台**共用 `BasicLayout`**，按登录态切换可见菜单与入口。

- **落地页 `/home`**：个人介绍 + 项目卡片导航 + 案例库入口；`/` 默认指向此处，登录用户也可见作为门户。
- **案例库 `/showcase`**：统一作品表 `sys_showcase`，响应式 CSS Grid + 类型化卡片 + `NModal` 弹窗详情，**不使用瀑布流**（移动端适配与异构卡片友好）。
- **公开菜单可配置**：哪些路由对游客可见由管理后台 `sys_menu_visibility` 决定，前端拉 `/public/menu-config` 过滤，非写死。
- **公开接口放行**：自定义 `@PublicAccess` 注解标注游客可调方法；Sa-Token 拦截器识别注解跳过 `checkLogin`（默认仍全拦截，仅标注方法放行），方法内用 `StpUtil.isLogin()` 区分游客/登录。
- **IP 限流**：纯 JDK 内存实现（无 Redis/Caffeine 依赖），按 IP 单日计数，超限返回 `Result` 429；阈值配置化。单机够用，多实例需换 Redis。
- **计费/统计**：AI token 消费记录（`sys_ai_usage`，主体 account/ip）+ IP 日调用次数限制（`@RateLimit`）。`AiChatService` 请求 LLM `stream_options.include_usage` 获取精确 token，不返回则字符估算兜底；首页仪表盘展示总/今日 token、调用次数、近 7 日趋势。AI 文案对游客开放（IP 日限 5 次）。

## 规划中的改造（Roadmap）

> 随实现推进，对应行为规范同步补写进本文档，避免文档超前于代码（铁律8）。

- **阶段 0** 鉴权下沉 + 公开壳骨架：前端 `requiresAuth` 下沉到子路由、新增 `/home`、`useMenu` 按登录态过滤、`BasicLayout` 游客态隐藏多标签页；后端 `@PublicAccess` 注解 + `SaTokenConfig` 改造。
- **阶段 1** 可配置公开菜单：`sys_menu_visibility` 表 + `GET /public/menu-config` + 后台可见性配置页（`system/menu-visibility`）。
- **阶段 2** 首页落地页 + 案例库（按 **2b→2a→2c** 推进）：
  - 2b 本地文件上传：`FileController` + `/uploads/**` 静态映射，登录方可上传、游客可读。
  - 2a `sys_showcase` + `sys_profile` + CRUD + 公开 Grid 列表页（类型卡片 + 弹窗）+ 管理页；手动录入。
  - 2c（已实现）各工具页"存入橱窗"按钮：文案 `video/copy` 直写 `sys_showcase`（type=text）；封面 `ExportTab` toPng→`/file/upload`→写表（type=cover）。登录可见（游客不显示）。
- **阶段 3** IP 单日限流：`@PublicAccess` 方法按 IP 计数，超限 `Result` 429，阈值配置化。
- **阶段 4（已实现）** AI token 统计 + IP 日限：`sys_ai_usage` + `AiChatService` usage 解析/估算兜底 + `AiChatController` `@PublicAccess+@RateLimit(daily=5)` 对游客开放 + `dashboard` 统计看板（总/今日 token、调用次数、近 7 日趋势）。

## Repo shape

Two independent packages, **no shared workspace tooling**:
- `frontend/` — Vue3 + Vite + TypeScript + Pinia + UnoCSS + Naive UI. Package manager is **pnpm** (not npm/yarn).
- `backend/` — Spring Boot 3.3 + Java 21 + Maven + MyBatis-Plus + PostgreSQL + Sa-Token. Java package root `com.stellar`, mappers scanned from `com.stellar.mapper`.

Run them as two separate processes; there is no root-level build that covers both.

## Commands

Frontend (`frontend/`):
```
pnpm dev          # dev server on :5173
pnpm typecheck    # vue-tsc -b --noEmit
pnpm build        # vue-tsc -b && vite build (typecheck gates build)
```

Backend (`backend/`):
```
mvn -q compile -DskipTests          # fast compile check
$env:SPRING_PROFILES_ACTIVE="local"; mvn spring-boot:run   # run on :8080
```

Verify before committing: `pnpm typecheck` (frontend) + `mvn -q compile -DskipTests` (backend).

## Gotchas that will bite you

**Auto-import dts are generated, not hand-written.** `src/types/auto-imports.d.ts` and `components.d.ts` are emitted by `unplugin-auto-import` / `unplugin-vue-components` during a vite run. If you add a new Vue/Pinia/vue-router API or a new Naive UI component, `pnpm typecheck` will fail with "Cannot find name X" until you run `pnpm dev` (or `pnpm exec vite build`) once to regenerate them. Don't edit those dts by hand.

**PowerShell mangles `-Dspring-boot.run.profiles=local`** (the dots split the arg). Use the `SPRING_PROFILES_ACTIVE=local` env var instead.

**The `local` profile is mandatory for real DB access.** `application.yml` uses `${DB_URL}` / `${DB_USERNAME}` / `${DB_PASSWORD}` placeholders with localhost defaults. The gitignored `application-local.yml` overrides them with the real remote PostgreSQL and also sets `spring.sql.init.mode=always` so `schema.sql` (idempotent `CREATE TABLE IF NOT EXISTS sys_user`) runs on startup. Without the local profile the app starts against localhost and will fail to connect.

**Layout height uses viewport units, not `100%`.** `BasicLayout` is `position: absolute` + `height: 100vh`; the login page uses `min-height: 100dvh`. Don't switch these to `height: 100%` — the Naive UI provider chain (`NConfigProvider` → `NMessageProvider` → …) does not propagate height, so `100%` collapses to content height (this is why the login background used to stop at the card).

## Secrets

- `application-local.yml` is gitignored and holds the real DB credentials. **Never** copy its contents into `application.yml` or any tracked file. `application.yml` must keep only env-var placeholders.
- The DBX MCP connection credentials live in DBX storage (`%APPDATA%\com.dbx.app\dbx.db`), not in the repo. Add connections via the `dbx_add_connection` tool; don't put connection strings in `opencode.json`.

## Frontend conventions

- **Dev proxy**: Vite proxies `/api` → `http://localhost:8080`. Backend has no context path (serves at root). Frontend axios `baseURL` is `/api` (see `.env`).
- **Messages**: use `window.$message` / `window.$dialog` (discrete API, set up in `src/utils/discrete.ts`, follows the theme) for code outside components — notably the axios interceptor in `src/api/request.ts`. Use `useMessage()` inside components; it requires `NMessageProvider`, which is present in `App.vue`.
- **Menu is static and route-driven.** Routes live in `src/router/index.ts`; the sidebar is generated from the `Root` route's children by `src/composables/useMenu.ts`. To add a page, add a route (with `meta.title` / `meta.icon` / `meta.order`) — the menu updates automatically. **菜单按登录态 + 公开配置过滤**：`generateMenus(isLogin, publicKeys)` 未登录时渲染 `meta.requiresAuth === false`（天然公开，如 `/home`）或 `publicKeys` 命中的路由；`publicKeys` 由 `store/menu.ts` 从 `GET /public/menu-config` 拉取缓存，`LayoutMenu` 以 `authStore.isLogin`/`menuStore.publicKeys` 为响应式依赖，登录/退出后菜单自动刷新（`auth.login`/`logout` 会 `menuStore.reset()`）。路由守卫：未登录时先 `loadPublicConfig()`，对 `publicKeys` 命中的工具页放行游客访问，否则跳 `/login`（`vue-router` 的 `route.meta` 合并父→子，`Root` 默认 `requiresAuth: true`，子路由显式标 `false` 才天然公开）。游客态 `BasicLayout` 隐藏多标签页（`LayoutTabs v-if="authStore.isLogin"`），`LayoutHeader` 显示"登录"按钮。后台 `系统管理 → 游客访问配置`（`/system/menu-visibility`）勾选哪些菜单对游客公开。
- **Icons are string keys.** `meta.icon` is a string (e.g. `"grid"`) mapped to a `@vicons/ionicons5` component in `src/utils/icons.ts`. Add new icons to that map.
- **Mobile**: below 768px the persistent sidebar is replaced by a drawer (`src/composables/useBreakpoint.ts`). The header's menu button opens the drawer on mobile, toggles collapse on desktop.
- **落地页 `/home` + 案例库 `/showcase`**：均公开（`meta.requiresAuth: false`）。`/home` 读 `/public/profile` + `menuStore.publicKeys` 渲染介绍与公开工具卡片导航 + 橱窗入口；`/showcase` 读 `/public/showcase` + `/public/profile` 用响应式 CSS Grid + 类型化卡片 + `NModal` 弹窗详情（**不瀑布流**，移动端友好）。管理页 `system/showcase`、`system/profile` 用 `NUpload` custom-request + `/file/upload` 上传封面/头像。`vite.config` 代理 `/uploads` → 8080。

## Backend conventions

- **Auth = Sa-Token.** Token is sent as `Authorization: Bearer <token>`. Endpoints: `POST /auth/login`, `POST /auth/logout`, `GET /user/info`. 鉴权由 `config/SaTokenConfig.java` 注册的 `AuthInterceptor`（`com.stellar.interceptor`）统一处理：**默认所有接口要求登录，仅标注 `@PublicAccess`（`common/annotation`）的 Controller 方法/类对游客放行**；未登录调用受保护接口抛 `NotLoginException`，由 `GlobalExceptionHandler` 转 401 envelope，前端 axios 拦截器据此重定向 `/login`。公开接口内可用 `StpUtil.isLogin()` 区分游客/登录做差异化处理。拦截器 `excludePathPatterns`：`/auth/login`、`/auth/register`、`/error`、`/uploads/**`（阶段2b 静态资源）。公开接口集合 `PublicController`（`/public/**`，方法标 `@PublicAccess`），如 `GET /public/menu-config` 返回游客可见 route keys；`MenuVisibilityController`（`/menu-visibility/**`，需登录）管理可见性，存 `sys_menu_visibility` 表。
- **Passwords are BCrypt.** `PasswordConfig` exposes a `PasswordEncoder` bean; `DataInitializer` seeds `admin / 123456` on first startup if missing. Don't store plaintext passwords.
- **Response envelope**: all controllers return `Result<T>` (`{ code, message, data }`); `code === 200` means success. `GlobalExceptionHandler` maps `BusinessException`, validation errors, and Sa-Token `NotLoginException` to this envelope.
- **MyBatis-Plus**: `sys_user` uses logic delete on the `deleted` column (0/1). The PG pagination dialect is configured in `MyBatisPlusConfig`.
- **操作日志 = AOP + 注解**: Controller 方法标注 `@Log(title="模块", type=OperationType.X)`，由 `LogAspect`（`@Around`）统一采集模块/类型/操作人/请求方法+URL/Java方法/参数(脱敏)/状态/异常/IP/耗时，异步（`@Async("logTaskExecutor")`）写入 `sys_log` 表。操作人解析：LOGIN 取请求体 username，其余取 `StpUtil` 登录态。脱敏硬编码 `password/oldPassword/confirmPassword/token/secretKey` → `******`。日志查询/详情/导出(xlsx, EasyExcel) 在 `SysLogController`，前端页在 `系统管理 → 日志管理`。新增可记录的操作时，给对应 Controller 方法加 `@Log` 即可。
- **AI 模块**: `com.stellar.ai` 含内置模板种子数据。LLM 配置（endpoint/apiKey/model）存于 `sys_ai_config` 表，apiKey 脱敏返回前端。流式对话通过 `SseEmitter` + JDK `HttpClient` 代理转发 LLM 的 SSE 流，前端用 `fetch`+`ReadableStream` 读取（可带 Authorization header，绕过 `EventSource` 限制）。模板（`sys_ai_template`）和文案历史（`sys_ai_copy_result`）存于 DB。`AiDataInitializer` 启动时播种 3 套内置模板（B站/抖音/小红书，`built_in=1` 不可删，可"恢复默认"）。前端页面：系统管理 → AI 配置 / AI 模板管理；视频工具箱 → 文案工具调用后端流式接口。封面工具（画布编辑、草稿）仍为纯前端 LocalStorage。
- **AI 计费/统计**: 每次 LLM 调用记录 token 消费到 `sys_ai_usage`（subject_type=account/ip，subject_id=userId/IP，prompt/completion/total_tokens，source=usage/estimate）。`AiChatService` 请求加 `stream_options.include_usage`，LLM 返回则记精确值，否则字符估算兜底（source=estimate）。`AiChatController#/stream` `@PublicAccess + @RateLimit(daily=5)` 对游客开放（IP 日限 5 次）；`GET /ai/chat/usage/stats` 返回统计（总/今日 token、调用次数、近 7 日趋势），首页 `dashboard` 展示。文案工具 `/video/copy` 游客态：`/ai/template/page` `@PublicAccess` 加载模板，跳过配置/历史（私有），生成结果本地虚拟展示（不调 `/ai/copy-result` 保存），历史区与"存入橱窗"按钮隐藏（登录可见）。用户可自带 AI 配置：文案工具"自己的 AI"按钮（复用 `ApiSettingsModal`，存 localStorage `apiConfigStore`），`streamAiChat` 调用时传 endpoint/apiKey/model 给 `/ai/chat/stream`，`AiChatService.streamChat(ChatRequest)` 优先用传入配置（后端不持久化 key），无则回退项目 `sys_ai_config`。自带 key 仍受 `@RateLimit` 限制。
- **本地文件上传**: `FileController` `POST /file/upload`（需登录，仅图片，UUID 存名）存 `${file.upload-dir}` 磁盘目录，返回 `/uploads/xxx`；`SaTokenConfig.addResourceHandlers` 把 `/uploads/**` 映射到磁盘（游客可读，拦截器已 excludePathPatterns 放行）。`application.yml` `file.upload-dir` + `spring.servlet.multipart` 限 10MB。作品橱窗 `sys_showcase`（type/title/cover_url/content/...，`ShowcaseController` 管理 + `/public/showcase` 游客列表）+ 个人介绍 `sys_profile`（单条 id=1，`ProfileController` 管理 + `/public/profile` 游客读取）。
- **IP 单日限流**: `@RateLimit(daily=N)`（`common/annotation`）标在耗资源接口，由 `RateLimitInterceptor`（`com.stellar.interceptor`，注册在 `AuthInterceptor` 之后）按 IP+当日 计数，超限抛 `BusinessException(TOO_MANY_REQUESTS=429)`，经 `GlobalExceptionHandler` 转 429 envelope。`RateLimitService` 纯 JDK `ConcurrentHashMap` 内存计数（无 Redis 依赖，单机够用，多实例需换 Redis）。阈值 `rate-limit.default-daily`（yml，默认 50）。对游客开放的耗资源接口（如 `TtsController#/edge/synthesize` `@PublicAccess + @RateLimit(daily=20)`）才限流；纯展示类公开接口（橱窗/菜单配置）无限流。AI 文案 `/ai/chat/stream` 阶段4 已开放（`@PublicAccess+@RateLimit(daily=5)`，token 统计见下）。TTS 合成历史 `/tts/record/page` + `/tts/record/{id}/audio` `@PublicAccess` 作公共墙只读（游客可看全部合成、试听下载，删除仍需登录）。

## DBX MCP

`opencode.json` registers a `dbx` MCP server (the `@dbx-app/mcp-server` global npm bin). Config is loaded once at opencode startup — **after editing `opencode.json`, restart opencode** for changes to take effect. Use the `dbx_*` tools to inspect schemas and run SQL against the configured PostgreSQL.
