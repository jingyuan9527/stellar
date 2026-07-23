import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

const STORAGE_KEY = 'stellar-theme'

interface ThemeState {
  darkMode: boolean
  primaryColor: string
  siderCollapsed: boolean
}

function loadState(): ThemeState {
  const defaults: ThemeState = {
    darkMode: false,
    primaryColor: '#18a058',
    siderCollapsed: false,
  }
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return defaults
    return { ...defaults, ...JSON.parse(raw) }
  } catch {
    return defaults
  }
}

export const useThemeStore = defineStore('theme', () => {
  const initial = loadState()

  const darkMode = ref(initial.darkMode)
  const primaryColor = ref(initial.primaryColor)
  const siderCollapsed = ref(initial.siderCollapsed)

  function persist() {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        darkMode: darkMode.value,
        primaryColor: primaryColor.value,
        siderCollapsed: siderCollapsed.value,
      }),
    )
  }

  watch([darkMode, primaryColor, siderCollapsed], persist)

  function toggleDarkMode() {
    darkMode.value = !darkMode.value
  }

  function toggleSiderCollapsed() {
    siderCollapsed.value = !siderCollapsed.value
  }

  function setPrimaryColor(color: string) {
    primaryColor.value = color
  }

  return {
    darkMode,
    primaryColor,
    siderCollapsed,
    toggleDarkMode,
    toggleSiderCollapsed,
    setPrimaryColor,
  }
})
