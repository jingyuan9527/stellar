# Docs

> 唯一文档入口。根仅留 `README.md / RULES.md / AGENTS.md`，其余一律进 `docs/`；AI 中间态进 `work/`（gitignored，不提交）。

- **铁律**：[`RULES.md`](../RULES.md) 优先级最高，冲突以它为准。
- **AI 入口**：[`AGENTS.md`](../AGENTS.md) 瘦身索引，高频约定 + 链接，不堆细节。

## 目录

| 路径 | 内容 |
|---|---|
| `guide/commands.md` | 前后端命令、校验、测试 |
| `guide/deploy.md` | 镜像、部署、备份 |
| `guide/gotchas.md` | 踩坑（JDK21 agent / auto-import dts / local profile / Redis db / Layout 高度） |
| `guide/env-secrets.md` | 环境变量、Secrets 约定 |
| `guide/docs-convention.md` | AI 落笔规范（永久 vs 中间态、模板、归档） |
| `architecture/overview.md` | 定位、Repo shape、导航结构 |
| `architecture/frontend.md` | 前端约定（代理、消息、菜单、页面模式） |
| `architecture/backend.md` | 后端约定（Auth/Redis/日志/AI/限流/模块） |
| `archive/` | `STAGES.md` 与 `ARCHITECTURE_REVIEW / OPTIMIZE_SPEC / BACKEND_15 / FEATURE_DIRECTIONS` 归档 |
| `specs/` | 活跃 Spec（进行中需求） |

## 规则

- 新增文档先选归属，不在根乱建 md。
- 中间态（计划单、草稿、AI 过程稿）放 `work/`，不提交、不链入 `README.md`。
- 行为变更必同步对应 `docs/` 篇章（铁律 8）。
