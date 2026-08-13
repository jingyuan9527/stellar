import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/store/auth'
import { useTabStore } from '@/store/tab'
import { useMenuStore } from '@/store/menu'

const Layout = () => import('@/layouts/BasicLayout.vue')

export const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', requiresAuth: false },
  },
  {
    path: '/',
    name: 'Root',
    component: Layout,
    redirect: '/home',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'home',
        name: 'Home',
        component: () => import('@/views/home/index.vue'),
        meta: { title: '首页', icon: 'home', order: 0, requiresAuth: false },
      },
      {
        path: 'about',
        name: 'About',
        component: () => import('@/views/about/index.vue'),
        meta: { title: '关于我', icon: 'about', order: 100, requiresAuth: false },
      },
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '仪表盘', icon: 'grid', order: 1, affix: true },
      },
      {
        path: 'ai',
        name: 'AI',
        redirect: '/ai/chat',
        meta: { title: 'AI创作', icon: 'sparkles', order: 3 },
        children: [
          {
            path: 'chat',
            name: 'AiChat',
            component: () => import('@/views/ai/chat/index.vue'),
            meta: { title: '聊天', icon: 'chatbubble', order: 1, requiresAuth: false, group: '创作' },
          },
          {
            path: 'copy',
            name: 'AiCopy',
            component: () => import('@/views/ai/copy/index.vue'),
            meta: { title: '文案工具', icon: 'log', order: 2, group: '创作' },
          },
          {
            path: 'image',
            name: 'AiImage',
            component: () => import('@/views/ai/image/index.vue'),
            meta: { title: '图片生成', icon: 'image', order: 3, requiresAuth: false, group: '创作' },
          },
          {
            path: 'video',
            name: 'AiVideo',
            component: () => import('@/views/ai/video/index.vue'),
            meta: { title: '视频生成', icon: 'play', order: 4, group: '创作' },
          },
          {
            path: 'tts',
            name: 'AiTts',
            component: () => import('@/views/ai/tts/index.vue'),
            meta: { title: '语音合成', icon: 'volume', order: 5, requiresAuth: false, group: '创作' },
          },
          {
            path: 'template',
            name: 'AiTemplate',
            component: () => import('@/views/ai/template/index.vue'),
            meta: { title: '模板管理', icon: 'palette', order: 6, group: '管理' },
          },
          {
            path: 'sessions',
            name: 'AiSessions',
            component: () => import('@/views/ai/sessions/index.vue'),
            meta: { title: '会话管理', icon: 'timer', order: 7, group: '管理' },
          },
          {
            path: 'memory',
            name: 'AiMemory',
            component: () => import('@/views/ai/memory/index.vue'),
            meta: { title: '长期记忆', icon: 'bulb', order: 8, group: '管理' },
          },
          {
            path: 'knowledge',
            name: 'AiKnowledge',
            component: () => import('@/views/ai/knowledge/index.vue'),
            meta: { title: '知识库', icon: 'book', order: 9, group: '管理' },
          },
          {
            path: 'persona',
            name: 'AiPersona',
            component: () => import('@/views/ai/persona/index.vue'),
            meta: { title: '人设管理', icon: 'persona', order: 10, group: '管理' },
          },
          {
            path: 'rag-eval',
            name: 'AiRagEval',
            component: () => import('@/views/ai/rag-eval/index.vue'),
            meta: { title: 'RAG评估', icon: 'trophy', order: 11, group: '管理' },
          },
        ],
      },
      {
        path: 'tools',
        name: 'Tools',
        redirect: '/tools/cover',
        meta: { title: '工具', icon: 'build', order: 5 },
        children: [
          {
            path: 'cover',
            name: 'ToolsCover',
            component: () => import('@/views/tools/cover/index.vue'),
            meta: { title: '封面工具', icon: 'image', order: 1 },
          },
          {
            path: 'json',
            name: 'ToolsJson',
            component: () => import('@/views/tools/json/index.vue'),
            meta: { title: 'JSON 格式化', icon: 'json', order: 2 },
          },
        ],
      },
      {
        path: 'game',
        name: 'Game',
        redirect: '/game/math',
        meta: { title: '游戏', icon: 'game', order: 6 },
        children: [
          {
            path: 'math',
            name: 'GameMath',
            component: () => import('@/views/game/math/index.vue'),
            meta: { title: '数学游戏', icon: 'calculator', order: 1, requiresAuth: false },
          },
          {
            path: 'conch',
            name: 'GameConch',
            component: () => import('@/views/game/conch/index.vue'),
            meta: { title: '神奇海螺', icon: 'conch', order: 2, requiresAuth: false },
          },
          {
            path: 'conch-admin',
            name: 'GameConchAdmin',
            component: () => import('@/views/game/conch-admin/index.vue'),
            meta: { title: '海螺预设', icon: 'conch', order: 3 },
          },
        ],
      },
      {
        path: 'memos',
        name: 'Memos',
        component: () => import('@/views/memos/index.vue'),
        meta: { title: '备忘同步', icon: 'sync', order: 7 },
      },
      {
        path: 'system',
        name: 'System',
        redirect: '/system/user-profile',
        meta: { title: '系统管理', icon: 'settings', order: 99 },
        children: [
          {
            path: 'user-profile',
            name: 'SystemUserProfile',
            component: () => import('@/views/system/user-profile/index.vue'),
            meta: { title: '用户资料', icon: 'person', order: 1 },
          },
          {
            path: 'log',
            name: 'SystemLog',
            component: () => import('@/views/system/log/index.vue'),
            meta: { title: '日志管理', icon: 'log', order: 2 },
          },
          {
            path: 'ai-config',
            name: 'SystemAiConfig',
            component: () => import('@/views/system/ai-config/index.vue'),
            meta: { title: 'AI 配置', icon: 'sparkles', order: 3 },
          },
          {
            path: 'menu-visibility',
            name: 'SystemMenuVisibility',
            component: () => import('@/views/system/menu-visibility/index.vue'),
            meta: { title: '游客访问配置', icon: 'eye', order: 4 },
          },
          {
            path: 'profile',
            name: 'SystemProfile',
            component: () => import('@/views/system/profile/index.vue'),
            meta: { title: '个人资料', icon: 'info', order: 5 },
          },
          {
            path: 'file',
            name: 'SystemFile',
            component: () => import('@/views/system/file/index.vue'),
            meta: { title: '文件管理', icon: 'folder', order: 6 },
          },
        ],
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/404.vue'),
    meta: { title: '404', requiresAuth: false },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

const whiteList = ['/login']

router.beforeEach(async (to, _from, next) => {
  const authStore = useAuthStore()
  const tabStore = useTabStore()
  const menuStore = useMenuStore()

  if (to.meta.title) {
    document.title = `${to.meta.title} - Stellar Admin`
  }

  if (whiteList.includes(to.path)) {
    if (authStore.isLogin) {
      next('/')
      return
    }
    next()
    return
  }

  // 首页等天然公开路由：放行（不加多标签）
  if (to.meta.requiresAuth === false) {
    next()
    return
  }

  // 未登录：加载游客可见菜单配置，命中公开的工具页则放行，否则跳登录
  if (!authStore.isLogin) {
    await menuStore.loadPublicConfig()
    if (menuStore.publicKeys.includes(to.path)) {
      next()
      return
    }
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }

  if (!authStore.userInfo) {
    try {
      await authStore.fetchUserInfo()
    } catch {
      authStore.handleUnauthorized()
      return
    }
  }

  tabStore.addTab(to)
  next()
})

export default router
