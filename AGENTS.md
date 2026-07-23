# AGENTS.md

Compact guidance for OpenCode sessions working in this repo. Verify against the executable config if anything seems stale.

> **铁律优先**：本人的硬性要求见 [`RULES.md`](./RULES.md)（需求先问清、代码高内聚低耦合、日志详实、前端统一+移动端适配、不做无关优化、危险操作必确认、中文提交、文档与代码一致、提交前查隐私泄露）。该文件优先级高于本文件，冲突以 `RULES.md` 为准。

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
- **Menu is static and route-driven.** Routes live in `src/router/index.ts`; the sidebar is generated from the `Root` route's children by `src/composables/useMenu.ts`. To add a page, add a route (with `meta.title` / `meta.icon` / `meta.order`) — the menu updates automatically.
- **Icons are string keys.** `meta.icon` is a string (e.g. `"grid"`) mapped to a `@vicons/ionicons5` component in `src/utils/icons.ts`. Add new icons to that map.
- **Mobile**: below 768px the persistent sidebar is replaced by a drawer (`src/composables/useBreakpoint.ts`). The header's menu button opens the drawer on mobile, toggles collapse on desktop.

## Backend conventions

- **Auth = Sa-Token.** Token is sent as `Authorization: Bearer <token>`. Endpoints: `POST /auth/login`, `POST /auth/logout`, `GET /user/info`. The Sa-Token interceptor (`config/SaTokenConfig.java`) protects everything except `/auth/login`; unauthenticated calls return 401, which the frontend interceptor turns into a redirect to `/login`.
- **Passwords are BCrypt.** `PasswordConfig` exposes a `PasswordEncoder` bean; `DataInitializer` seeds `admin / 123456` on first startup if missing. Don't store plaintext passwords.
- **Response envelope**: all controllers return `Result<T>` (`{ code, message, data }`); `code === 200` means success. `GlobalExceptionHandler` maps `BusinessException`, validation errors, and Sa-Token `NotLoginException` to this envelope.
- **MyBatis-Plus**: `sys_user` uses logic delete on the `deleted` column (0/1). The PG pagination dialect is configured in `MyBatisPlusConfig`.
- **操作日志 = AOP + 注解**: Controller 方法标注 `@Log(title="模块", type=OperationType.X)`，由 `LogAspect`（`@Around`）统一采集模块/类型/操作人/请求方法+URL/Java方法/参数(脱敏)/状态/异常/IP/耗时，异步（`@Async("logTaskExecutor")`）写入 `sys_log` 表。操作人解析：LOGIN 取请求体 username，其余取 `StpUtil` 登录态。脱敏硬编码 `password/oldPassword/confirmPassword/token/secretKey` → `******`。日志查询/详情/导出(xlsx, EasyExcel) 在 `SysLogController`，前端页在 `系统管理 → 日志管理`。新增可记录的操作时，给对应 Controller 方法加 `@Log` 即可。

## DBX MCP

`opencode.json` registers a `dbx` MCP server (the `@dbx-app/mcp-server` global npm bin). Config is loaded once at opencode startup — **after editing `opencode.json`, restart opencode** for changes to take effect. Use the `dbx_*` tools to inspect schemas and run SQL against the configured PostgreSQL.
