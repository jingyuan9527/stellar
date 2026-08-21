# Frontend

- Dev proxy：Vite `/api` → `:8080`，axios `baseURL=/api`
- Messages：组件外 `window.$message/$dialog`（`utils/discrete.ts`），组件内 `useMessage()`；错误统一 axios 拦截器处理
- 全局错误兜底：`utils/clientError.ts` + `components/ErrorBoundary.vue` → `POST /system/client-error` 落 `sys_log`
- 路由即菜单：`src/router/index.ts` + `composables/useMenu.ts`，`meta.title/icon/order`；`generateMenus(isLogin, publicKeys)` 按 `requiresAuth` + `/public/menu-config` 过滤；守卫 `loadPublicConfig` 后放行
- Icons：`meta.icon` 字符串 → `utils/icons.ts` `@vicons/ionicons5`
- Mobile：`<768px` 抽屉（`useBreakpoint.ts`）

## 页面模式

- JSON 格式化 `/tools/json`：纯前端，双栏 + `JsonNode.vue` 树，移动端上下布局
- `/home` + `/about`：公开，读 `/public/profile`，头像 `NUpload` + `/file/upload`
- 游戏/海螺：公开，`api/game.ts` / `api/conch.ts`
- AI 配置 `/ai/config`：供应商+模型两级，`api/ai.ts`
- 文案/图片/视频/TTS：`AiGeneratorLayout.vue` 公共骨架（左 360 sticky + 右历史 + Drawer，<1024 栈叠），历史 remote 分页 + 抽屉回看
- 文件管理 `/system/file`：分页+过滤+抽屉+批量删
- TTS：Edge+AI Tab，`constants/tts-voices.ts`，`api/tts.ts`
