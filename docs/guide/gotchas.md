# Gotchas

## JDK 21 Agent

JDK21 禁止动态加载 agent（JEP 451），Mockito/jacoco 需 `-XX:+EnableDynamicAgentLoading -Djdk.attach.allowAttachSelf=true`，`pom.xml` 已配勿删。`JAVA_HOME` 可能指向 JDK8，注意切 21。

## Auto-import dts

`src/types/auto-imports.d.ts` / `components.d.ts` 由 `unplugin-*` 生成，`pnpm typecheck` 报 `Cannot find name X` 时跑 `pnpm dev` 重生成，勿手改。

## PowerShell

` -Dspring-boot.run.profiles=local` 会被 PowerShell 拆参，用 `$env:SPRING_PROFILES_ACTIVE="local"`。

## local Profile

`application.yml` 仅占位符，真实 DB/Redis 在 gitignored `application-local.yml`（`spring.sql.init.mode=always` 执行 `schema.sql`）。无 local  profile 起于 localhost 会连失败；Redis 空 URL 懒连接，首次使用才暴露。

`spring.data.redis.database` 必须显式设（url 末段 `/db` Lettuce 可能不解析），local 固定 1，部署用 `REDIS_DATABASE`。

## Layout 高度

`BasicLayout` 用 `100vh`，登录页 `100dvh`，勿改 `100%`（Naive UI provider 链不传高度会坍缩）。
