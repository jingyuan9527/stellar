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
        meta: { title: '仪表盘', icon: 'grid', order: 3, affix: true },
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
            path: 'ai-template',
            name: 'SystemAiTemplate',
            component: () => import('@/views/system/ai-template/index.vue'),
            meta: { title: 'AI 模板管理', icon: 'palette', order: 4 },
          },
          {
            path: 'menu-visibility',
            name: 'SystemMenuVisibility',
            component: () => import('@/views/system/menu-visibility/index.vue'),
            meta: { title: '游客访问配置', icon: 'eye', order: 5 },
          },
          {
            path: 'profile',
            name: 'SystemProfile',
            component: () => import('@/views/system/profile/index.vue'),
            meta: { title: '个人资料', icon: 'info', order: 7 },
          },
          {
            path: 'conch',
            name: 'SystemConch',
            component: () => import('@/views/system/conch/index.vue'),
            meta: { title: '海螺预设', icon: 'conch', order: 8 },
          },
        ],
      },
      {
        path: 'tts',
        name: 'TTS',
        redirect: '/tts/edge',
        meta: { title: 'TTS语音合成', icon: 'volume', order: 4 },
        children: [
          {
            path: 'edge',
            name: 'TTSEdge',
            component: () => import('@/views/tts/edge/index.vue'),
            meta: { title: 'Edge语音合成', icon: 'megaphone', order: 1 },
          },
          {
            path: 'ai',
            name: 'TTSAI',
            component: () => import('@/views/tts/ai/index.vue'),
            meta: { title: 'AI语音合成', icon: 'sparkles', order: 2 },
          },
          {
            path: 'history',
            name: 'TTSHistory',
            component: () => import('@/views/tts/history/index.vue'),
            meta: { title: '合成历史', icon: 'list', order: 3 },
          },
        ],
      },
      {
        path: 'video',
        name: 'Video',
        redirect: '/video/cover',
        meta: { title: '视频工具箱', icon: 'film', order: 5 },
        children: [
          {
            path: 'cover',
            name: 'VideoCover',
            component: () => import('@/views/video/cover/index.vue'),
            meta: { title: '封面工具', icon: 'image', order: 1 },
          },
          {
            path: 'copy',
            name: 'VideoCopy',
            component: () => import('@/views/video/copy/index.vue'),
            meta: { title: '文案工具', icon: 'log', order: 2 },
          },
          {
            path: 'image',
            name: 'VideoImage',
            component: () => import('@/views/video/image/index.vue'),
            meta: { title: 'AI图片生成', icon: 'image', order: 3, requiresAuth: false },
          },
        ],
      },
      {
        path: 'game',
        name: 'Game',
        redirect: '/game/math',
        meta: { title: 'Game', icon: 'game', order: 6 },
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
