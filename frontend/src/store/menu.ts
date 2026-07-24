import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getPublicMenuConfig } from '@/api/menu-visibility'

/**
 * 菜单可见性 store：缓存游客可见的 route key 列表。
 * 每次调用都拉取最新（不缓存），避免后台改配置后游客会话陈旧。
 */
export const useMenuStore = defineStore('menu', () => {
  const publicKeys = ref<string[]>([])

  /** 拉取游客可见菜单配置（每次拉最新） */
  async function loadPublicConfig() {
    try {
      publicKeys.value = await getPublicMenuConfig()
    } catch {
      publicKeys.value = []
    }
  }

  /** 重置（退出后清空） */
  function reset() {
    publicKeys.value = []
  }

  return { publicKeys, loadPublicConfig, reset }
})
