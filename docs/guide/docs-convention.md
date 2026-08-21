# Docs Convention

> AI 落笔规范。目标：根整洁、按需加载、低上下文污染。

## 分层

| 类型 | 位置 | 是否提交 | 是否链入 README |
|---|---|---|---|
| 永久文档 | `docs/**` | 是 | 按需（`docs/README.md` 索引即可） |
| 中间态（计划单/草稿/过程稿） | `work/**` | 否（gitignored） | 否 |

- 中间态禁提交、禁链入 `README.md`，避免污染他人阅读主线。
- 永久化判定：需长期复用/跨会话引用/行为变更说明 → 迁 `docs/` 并走评审。

## 归属

- 根仅留 `README.md / RULES.md / AGENTS.md`，禁止新增 md。
- 新增文档先选 `docs/` 子目录：`guide / architecture / specs / archive`，不乱建。
- `docs/specs/` 仅放进行中活跃 Spec，完成后 7 日内迁 `docs/archive/`。

## 命名

- `kebab-case`，有序用 `01-` 前缀，禁止中文文件名。
- 单篇 <300 行，超拆。

## 模板（永久文档强制头）

```
> 状态 / 日期 / 关联代码（文件:行）
背景 → 决策 → 影响（三段，不堆流水账）
```

- 语言：中文简洁，术语/代码英文原样，禁恭维/空话。
- 同步：行为变更必改对应 `docs/` 篇章（铁律 8），PR 勾「文档与代码一致」。

## work 使用

- 路径：`work/<topic>/plan.md` 等，本地草稿、待确认为主。
- 转正流程：`work/` → 提炼 → `docs/specs/` 或对应 `architecture/guide` → 归档。
- `work/` 不做链接聚合，不入 `docs/README.md`。
