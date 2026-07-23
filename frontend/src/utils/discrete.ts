import { createDiscreteApi, darkTheme, zhCN, dateZhCN } from 'naive-ui'
import { reactive, watchEffect } from 'vue'
import { useThemeStore } from '@/store/theme'

let installed = false

export function setupDiscreteApi() {
  if (installed) return
  installed = true

  const themeStore = useThemeStore()
  const configProviderProps = reactive<Record<string, unknown>>({})

  watchEffect(() => {
    configProviderProps.theme = themeStore.darkMode ? darkTheme : null
    configProviderProps.themeOverrides = {
      common: {
        primaryColor: themeStore.primaryColor,
        primaryColorHover: themeStore.primaryColor,
        borderRadius: '6px',
      },
    }
    configProviderProps.locale = zhCN
    configProviderProps.dateLocale = dateZhCN
  })

  const discrete = createDiscreteApi(
    ['message', 'dialog', 'notification', 'loadingBar'] as const,
    { configProviderProps: configProviderProps as never },
  )

  window.$message = discrete.message
  window.$dialog = discrete.dialog
  window.$notification = discrete.notification
  window.$loadingBar = discrete.loadingBar
}
