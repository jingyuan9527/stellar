import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi, logout as logoutApi } from '@/api/auth'
import { getUserInfo as getUserInfoApi } from '@/api/user'
import type { LoginRequest, SysUser } from '@/types/api'
import { useMenuStore } from '@/store/menu'
import router from '@/router'

const TOKEN_KEY = 'stellar-token'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) || '')
  const userInfo = ref<SysUser | null>(null)

  const isLogin = computed(() => !!token.value)

  function setToken(value: string) {
    token.value = value
    if (value) {
      localStorage.setItem(TOKEN_KEY, value)
    } else {
      localStorage.removeItem(TOKEN_KEY)
    }
  }

  async function login(data: LoginRequest) {
    const res = await loginApi(data)
    setToken(res.token)
    userInfo.value = res.userInfo
    useMenuStore().reset()
    return res
  }

  async function fetchUserInfo() {
    if (!token.value) return null
    const info = await getUserInfoApi()
    userInfo.value = info
    return info
  }

  async function logout() {
    try {
      await logoutApi()
    } finally {
      reset()
      useMenuStore().reset()
      router.replace('/login')
    }
  }

  function reset() {
    setToken('')
    userInfo.value = null
  }

  function handleUnauthorized() {
    reset()
    const current = router.currentRoute.value
    if (current.path !== '/login') {
      router.replace({ path: '/login', query: { redirect: current.fullPath } })
    }
  }

  return {
    token,
    userInfo,
    isLogin,
    setToken,
    login,
    fetchUserInfo,
    logout,
    reset,
    handleUnauthorized,
  }
})
