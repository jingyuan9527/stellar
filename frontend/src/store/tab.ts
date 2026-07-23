import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { RouteLocationNormalized } from 'vue-router'
import router from '@/router'
import type { TabItem } from '@/types/route'

export const useTabStore = defineStore('tab', () => {
  const tabs = ref<TabItem[]>([])
  const activePath = ref('')

  function addTab(route: RouteLocationNormalized) {
    const { path, name, meta } = route
    if (!meta?.title || meta.hidden) return
    if (!name) return
    const exists = tabs.value.find((t) => t.path === path)
    if (!exists) {
      tabs.value.push({
        path,
        name: String(name),
        title: meta.title,
        icon: meta.icon,
        closable: meta.affix ? false : meta.closable !== false,
      })
    }
    activePath.value = path
  }

  async function removeTab(path: string) {
    const idx = tabs.value.findIndex((t) => t.path === path)
    if (idx === -1) return
    const target = tabs.value[idx]
    if (!target.closable) return
    tabs.value.splice(idx, 1)
    if (activePath.value === path) {
      const next = tabs.value[idx] || tabs.value[idx - 1]
      if (next) {
        await router.push(next.path)
      } else {
        await router.push('/')
      }
    }
  }

  async function removeOthers(path: string) {
    tabs.value = tabs.value.filter((t) => t.path === path || !t.closable)
    activePath.value = path
    await router.push(path)
  }

  async function removeAll() {
    const affixTabs = tabs.value.filter((t) => !t.closable)
    tabs.value = affixTabs
    if (!affixTabs.find((t) => t.path === activePath.value)) {
      await router.push(affixTabs[0]?.path || '/')
    }
  }

  function reset() {
    tabs.value = []
    activePath.value = ''
  }

  return {
    tabs,
    activePath,
    addTab,
    removeTab,
    removeOthers,
    removeAll,
    reset,
  }
})
