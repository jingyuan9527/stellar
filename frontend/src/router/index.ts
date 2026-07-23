import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/store/auth'
import { useTabStore } from '@/store/tab'

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
    redirect: '/dashboard',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '仪表盘', icon: 'grid', order: 1, affix: true },
      },
      {
        path: 'system',
        name: 'System',
        redirect: '/system/user-profile',
        meta: { title: '系统管理', icon: 'settings', order: 2 },
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

  if (to.meta.requiresAuth === false) {
    next()
    return
  }

  if (!authStore.isLogin) {
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
