# Stellar 前端设计系统索引

> 用途：设计评审 / 代码交接 / 防回归。所有页面、组件、样式**必须复用本文件列出的 token 与共享资产**，禁止在应用外壳（chrome）里写死颜色。
> 维护：改动 `tokens.css` / 共享组件后同步更新本文件。

---

## 1. 设计 Token（`src/styles/tokens.css`）

语义化 CSS 变量，浅 / 深双套。深色由 `[data-theme="dark"]` 覆盖；`--c-brand` 跟随 `App.vue` 运行时写入的 `--primary-color`（主题色可在 ThemeDrawer 切换）。

### 1.1 颜色
| Token | 浅色 | 深色 | 用途 |
|---|---|---|---|
| `--c-brand` | `--primary-color`(默认 `#18a058`) | 同浅 | 品牌主色（跟随主题色切换） |
| `--c-info` | `#2080f0` | 同浅 | 辅助信息色 / **链接色** |
| `--c-success` | `#18a058` | `#63d8a8` | 成功 / 上升 |
| `--c-warning` | `#f0a020` | `#ffb84d` | 警告 |
| `--c-error` | `#d03050` | `#ff6b81` | 错误 / 下降 |
| `--c-success-bg` | `color-mix(...success 12%)` | 自动 | 成功柔化底 |
| `--c-warning-bg` | `color-mix(...warning 14%)` | 自动 | 警告柔化底 |
| `--c-error-bg` | `color-mix(...error 12%)` | 自动 | 错误柔化底 / `StateError` 底 |
| `--c-brand-bg` | `color-mix(...brand 12%)` | 自动 | 选中态浅底 / 气泡 |
| `--c-info-bg` | `color-mix(...info 12%)` | 自动 | 信息浅底 |
| `--c-text-1` | `#1f2329` | `#e8eaed` | 主文本 / 标题 |
| `--c-text-2` | `#5b6168` | `#a8aeb8` | 次文本 / 卡片元信息 |
| `--c-text-3` | `#8a9099` | `#6b7280` | 弱文本 / 占位 / 空态文案（**替代 opacity 压字**） |
| `--c-bg` | `#f5f6f8` | `#0f1115` | 页面底 |
| `--c-fill` | `#ffffff` | `#1a1d23` | 卡片底 |
| `--c-fill-2` | `rgba(127,127,127,.1)` | `rgba(255,255,255,.06)` | 次级填充 / 图标底 / 代码块底 |
| `--c-border` | `rgba(0,0,0,.08)` | `rgba(255,255,255,.08)` | 边框 / 分割线 |
| `--c-scrollbar` | `rgba(128,128,128,.35)` | `rgba(255,255,255,.15)` | 滚动条 |

> 状态色柔化底用 `color-mix(in srgb, var(--c-x) N%, transparent)` 写，**暗色自动提亮保对比**，不要再手搓底。

### 1.2 间距 / 圆角 / 阴影 / 字体 / 弹窗档
| Token | 值 | 用途 |
|---|---|---|
| `--sp-1..6` | `4/8/12/16/24/32px` | 间距阶梯 |
| `--r-xs/sm/md/lg/xl` | `4/6/8/12/16px` | 圆角：xs 角标 · sm 控件 · md 小卡/条目 · lg 卡片/弹窗 · xl 大容器 |
| `--sh-card` | `0 2px 12px rgba(0,0,0,.06)`（暗 `.3`） | 卡片阴影 |
| `--font-sans` | 系统中文字体栈 | 全局正文 |
| `--mono-font` | 等宽栈 | 代码 / 数值 |
| `--card-pad` | `18px` | 卡片内边距 |
| `--card-title-size / -weight` | `15px / 600` | 卡片标题尺度 |
| `--card-meta-size` | `13px` | 卡片元信息尺度 |
| `--modal-sm / -md / -lg` | `420 / 560 / 720px` | 弹窗宽度档（保留 `maxWidth:90vw` + 移动端 `isMobile?'92%':var(--modal-*)`） |

---

## 2. 全局工具类（`src/styles/global.css`）

| 类 / 规则 | 定义 | 用法 |
|---|---|---|
| `.section-title` | `16px / 700` `var(--c-text-1)` `padding-left:4px` | **所有区块标题**统一调用，各页只保留间距覆盖 |
| `.card-title` | `var(--card-title-size) / var(--card-title-weight)` `var(--c-text-1)` | 卡片标题 |
| `.card-meta` | `var(--card-meta-size)` `var(--c-text-2)` `line-height:1.6` | 卡片元信息 / 描述 |
| `.alert-error` | `--c-error-bg` 底 + `--c-error` 字 + 25% 描边 + `--r-md` | 收敛自各页自造 `.alert/.bubble.error` |
| `.skeleton-line` | 渐变 sweep 动效（`skeleton-shimmer`） | 配合 `SkeletonList` |
| `.n-modal .n-card` | `border-radius: var(--r-lg)` | 所有 `preset="card"` 弹窗圆角统一 |
| `.n-modal .n-card-header__title` | `16px / 700` `var(--c-text-1)` | 弹窗标题对齐 `.section-title` 语言 |
| `.n-drawer` / `.n-drawer .n-card` | `border-radius: var(--r-lg)` | 抽屉同步 |
| `:focus-visible` | `outline:2px solid var(--c-info)` `offset:2px` | 全局键盘焦点环（点击不显示） |
| `@keyframes list-in` | `opacity 0→1 + translateY(8px→0)` | 列表 stagger 入场（接 `animation: list-in .3s ease both; animation-delay: idx*40ms`） |
| `.page-enter/leave` | `opacity + translateY` `0.18s` | `BasicLayout` `RouterView` 切换过渡 |
| `prefers-reduced-motion` | 关停 shimmer / page / list-in | 无障碍兜底 |

---

## 3. 共享组件（`src/components/`）

### 3.1 `Chart.vue` — echarts 柱状图封装
- props：`option: EChartsCoreOption`（必填）、`height?: string`（默认 `'260px'`）
- 按需注册 `BarChart + Tooltip + Grid + Canvas`；监听 `themeStore.darkMode` 在主题切换时重绘。
- **用法**：父组件 computed 用 `getChartColors()` 取色注入 `option`；`darkMode` 变化时重算 `option` 即自动重绘。

### 3.2 `SkeletonList.vue` — 列表骨架
- props：`rows?: number`（默认 4）、`height?: string`（默认 `'72px'`）
- 渲染若干 `.skeleton-line`（shimmer 动效）。用于列表 / 区块初始加载。

### 3.3 `StateError.vue` — 区块级错误占位
- props：`title?: string`（默认「加载失败」）、`description?: string`（默认「数据暂时未能取回，请稍后重试。」）
- emit：`retry`
- 品牌错误底（`--c-error-bg`）+ 图标 `AlertCircleOutline` + 重试按钮（`type="error" secondary`）。**用于某卡片 / 区块数据请求失败**，不用于操作结果 toast。

### 3.4 `BrandEmpty.vue` — 品牌化空态
- props：`title?`（默认「这里还什么都没有」）、`description?`（默认「试着创建第一条内容吧」）、`size?: 'small'|'medium'|'large'`、`showAction?: boolean`、`actionText?: string`
- emit：`action`
- 内嵌品牌微光 SVG 插画（绿/蓝同源，跟随主题）；`description` 走 `--c-text-3`。**替代裸 `NEmpty`**，尤其仪表盘 / 对话 / 笔记 / 海螺等显眼空态；极紧凑位可用 `<NEmpty size="small">`。

### 3.5 `ErrorBoundary.vue` — 渲染错误边界
- 捕获子树渲染异常，展示友好占位 + 自动 `reportClientError` 上报（模块=前端错误）；路由切换自动复位。
- 用法：`<ErrorBoundary><RouterView /></ErrorBoundary>`。圆角已用 `var(--r-lg)`，底 `var(--n-color, var(--c-fill-2))`。

---

## 4. 工具函数与图标

- `src/utils/chartColors.ts` — `getChartColors()`：从 `--c-*` token 运行时读色（echarts canvas 不读 CSS 变量），返回 `{ brand, info, success, warning, error, text3, border }`。
- `src/utils/icons.ts` — `iconMap`：Naive/NIcon 图标集中注册表。**新增图标先在此登记**，全站统一走 `iconMap.xxx`（含 `location` / `arrowUp` / `arrowDown` 等）。
- `src/store/theme.ts` — 主题 store（`darkMode` / `primaryColor`）。

---

## 5. 约定与禁止项（Conventions）

1. **应用外壳（chrome）颜色一律走 token**，禁止写死 `rgba(128,128,128,…)` / `#hex`：
   - 边框 / 分割线 → `var(--c-border)`
   - 次级填充 / 图标底 / 代码块底 → `var(--c-fill-2)`
   - 弱文本 / 占位 → `var(--c-text-3)`（**不要用 `opacity` 压字**，暗色下会跌破 AA）
2. 链接色 → `var(--c-info)`；品牌色 → `var(--c-brand)`；状态 → `--c-success/warning/error`（及 `-bg`）。
3. 圆角走 `--r-*`、间距走 `--sp-*`、阴影走 `--sh-card`、弹窗宽度走 `--modal-sm/md/lg`。
4. 暗色模式**只靠 token 自动生效**，不要为暗色另写一套颜色覆盖（除 `prefers-reduced-motion`）。
5. 标题用 `.section-title`；卡片标题用 `.card-title`、元信息用 `.card-meta`。
6. 三态反馈：加载 → `SkeletonList` / 按钮表格用 `:loading`；空 → `BrandEmpty`（紧凑位 `NEmpty size="small"`）；区块错误 → `StateError`；图表 → `Chart` + `getChartColors()`。
7. 图标统一 `iconMap`（NIcon）。

---

## 6. 暗色模式回归核查（Round 10 / K）

### 6.1 已豁免（by design，**不要改**）
- `cover` 工具全部 `rgba(...)`（`CoverCanvas` / `templateConfig` / `textShadow` / tabs）：导出画布，固定底色，故意写死。
- `tools/json/JsonNode.vue` 的 `--json-*`：代码语法高亮，自带明暗两套色集。
- 套了 Naive 变量的兜底：`var(--n-border-color, …)` / `var(--n-color, …)` / `var(--n-color-hover, …)`（Naive 在暗色覆盖 `--n-*`）。
- `ThemeDrawer` 的 `presetColors` hex：主题色选择器色板，字面量正确。
- `login` 渐变上的白色叠层 + 卡片阴影：景深，与主题无关。

### 6.2 真实漏网点（Round 10 已修复，共 4 文件 14 处）
> ✅ 已在 Round 10 修复；Round 11 回归 grep（见 6.3）验证 `src`（排除 cover/、JsonNode.vue、tokens.css）**零漏网**，无回退。
统一映射规则：**边框/分割线 → `var(--c-border)`；次级填充 → `var(--c-fill-2)`；弱文本 → `var(--c-text-3)`**。

**`src/views/memos/index.vue`**（`.markdown-body` 富文本渲染区）
- L1100 `.markdown-body :deep(code)` background → `var(--c-fill-2)`
- L1106 `.markdown-body :deep(pre)` background → `var(--c-fill-2)`
- L1116 `.markdown-body :deep(blockquote)` border-left → `var(--c-border)`
- L1134 `.markdown-body :deep(th), :deep(td)` border → `var(--c-border)`

**`src/views/ai/sessions/index.vue`**
- L231 `.msg-row.assistant .bubble` background → `var(--c-fill-2)`
- L239 `.msg-refs` border-top → `var(--c-border)`

**`src/views/tools/json/index.vue`**
- L271 `.pane` border → `var(--c-border)`
- L274 `.pane` background → `var(--c-fill-2)`
- L281 `.pane-title` color → `var(--c-text-3)`
- L282 `.pane-title` background → `var(--c-fill-2)`
- L283 `.pane-title` border-bottom → `var(--c-border)`
- L331 `.empty` color → `var(--c-text-3)`

**`src/views/system/menu-visibility/index.vue`**
- L228 `.group-item` border-bottom → `var(--c-border)`

### 6.3 防回归 grep 守护（CI / 提交前）
新增 / 修改应用外壳样式时，禁止出现**未走 token 也未走 Naive 变量**的中性灰：
```
# 命中即需整改（排除 cover/ 目录、tokens.css、JsonNode.vue、*fallback*）
rg -n 'rgba\(\s*12[0-9],\s*12[0-9],\s*12[0-9]' src --glob '!src/views/tools/cover/**' --glob '!src/styles/tokens.css' --glob '!src/views/tools/json/JsonNode.vue'
```
> `rgba(128,128,128,…)` / `rgba(127,127,127,…)` 是暗色漏网高发形态；命中后按 6.2 映射收口。

### 6.4 逐页暗色核查清单（人工过一遍）
- [ ] 公开页：`/home` `/about` `/404` — 文字三级、Hero 微光、品牌竖条清晰
- [ ] 后台壳：侧栏 active 竖条 / 顶栏 / 标签页底条 — 浅底文字对比
- [ ] 数据页：dashboard 统计卡、近 7 日趋势、monitor echarts 配色
- [ ] 工具页：chat 气泡、memos 卡片、json 编辑器(pane/title/empty)、menu-visibility 分组线
- [ ] 三态：SkeletonList / BrandEmpty / StateError 在暗色下可读

---

## 7. 新增页面自检清单
- [ ] 颜色全走 token，无 `rgba(128,128,128,…)` 写死（见 6.3）
- [ ] 区块标题用 `.section-title`；卡片用 `.card-title/.card-meta`
- [ ] 空态用 `BrandEmpty`；加载用 `SkeletonList`；区块错误用 `StateError`；图表用 `Chart`
- [ ] 圆角 / 间距 / 阴影 / 弹窗宽度引用对应 token
- [ ] 图标走 `iconMap`
- [ ] 键盘可聚焦项带 `tabindex="0" role="button"` + `:focus-visible` 有反馈
- [ ] 切暗色后无糊字、无未跟随主题的固定色

---

## 8. 数据叙事模式：环比箭头（Round 11，跨前后端）

仪表盘 KPI 卡用「真·环比」（近 7 日 vs 前 7 日）替代纯前端方向推导，让数据会说话。

### 8.1 前端
- `views/dashboard/index.vue`：
  - `deltaPct(curr: number, prev: number): KpiDelta | null` —— **prev=0 返回 null**（不渲染箭头，避免除零/误导）。
  - KPI 卡 `delta` 字段接 `deltaPct(cur, prev)`；模板渲染 `.kpi-delta`（`.up` → `--c-success` 升，`.down` → `--c-error` 降），箭头图标 `iconMap.arrowUp / arrowDown`。
- `types/api.d.ts` 的 `AiUsageStats` 加 `periodTokens / periodCalls / prevPeriodTokens / prevPeriodCalls: number`（数据来源见 8.2）。
- 配色复用 `utils/icons.ts` 的 `arrowUp/arrowDown` 与 token 的 `--c-success / --c-error`，与第九轮 `.trend-caption` 升降语义同源。

### 8.2 后端
- `ai/mapper/SysAiUsageMapper.java` 新增 `selectTotalsBetween(@Param start, @Param end)`：
  `SELECT COALESCE(SUM(total_tokens),0) AS tokens, COUNT(*) AS calls FROM sys_ai_usage WHERE create_time >= #{start} AND create_time < #{end}`
- `ai/service/SysAiUsageService.stats()` 调两次：当前 7 日 `[today-6 00:00, now]`、前 7 日 `[today-13 00:00, today-6 00:00]` → 写入 `AiUsageStatsVO.periodTokens / periodCalls / prevPeriodTokens / prevPeriodCalls`（long）。
- 单测：`SysAiUsageServiceTest` 已给 `selectTotalsBetween` 补 stub 并断言 period 字段非空。

### 8.3 新增同类 KPI 时
复用 `.kpi-delta` + `deltaPct`；后端若需新区间聚合，仿 `selectTotalsBetween` 加 mapper 方法，VO 加 `period*/prevPeriod*` 字段，**不要在前端手搓"假环比"**。
