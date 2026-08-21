# AGENTS.md

> 铁律：[`RULES.md`](./RULES.md) 最高优，冲突以它为准。根仅留 `README/RULES/AGENTS`，其余进 `docs/`；中间态进 `work/`（gitignored，不污染 README）。

## 定位

Stellar 是个人知识/实验沉淀池，游客免登录浏览落地页/关于我/工具（IP 限流），登录见管理。历史见 [`docs/archive/STAGES.md`](./docs/archive/STAGES.md)。

## 文档入口

- 索引：[`docs/README.md`](./docs/README.md)
- 规范：[`docs/guide/docs-convention.md`](./docs/guide/docs-convention.md)（永久 vs 中间态、模板、归档）
- 架构：[`docs/architecture/overview.md`](./docs/architecture/overview.md) · [`frontend.md`](./docs/architecture/frontend.md) · [`backend.md`](./docs/architecture/backend.md)
- 指南：[`commands.md`](./docs/guide/commands.md) · [`deploy.md`](./docs/guide/deploy.md) · [`gotchas.md`](./docs/guide/gotchas.md) · [`env-secrets.md`](./docs/guide/env-secrets.md)

## Repo

`frontend/` Vue3+Vite+TS+Pinia+UnoCSS+NaiveUI (pnpm) · `backend/` SpringBoot 3.3 Java21 Maven MyBatis-Plus PG + Sa-Token + Redis

## 高频约定

- 中间态文档放 `work/`，不提交不链入 `README`；永久化再迁 `docs/`（见 `docs-convention.md`）。
- 行为变更必同步 `docs/`（铁律 8）。
- Secrets：`application-local.yml` 与 `.env` gitignored，不入 `application.yml`；DBX 凭证在 DBX 存储，不写入 `opencode.json`。
- 提交：中文、单功能点、不主动提交、提交前查隐私泄露。
