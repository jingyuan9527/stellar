<script setup lang="ts">
import { computed, watchEffect } from 'vue'
import { darkTheme, zhCN, dateZhCN, type GlobalThemeOverrides } from 'naive-ui'
import { useThemeStore } from '@/store/theme'
import ErrorBoundary from '@/components/ErrorBoundary.vue'

const themeStore = useThemeStore()

const theme = computed(() => (themeStore.darkMode ? darkTheme : null))

const themeOverrides = computed<GlobalThemeOverrides>(() => ({
  common: {
    primaryColor: themeStore.primaryColor,
    primaryColorHover: themeStore.primaryColor,
    borderRadius: '6px',
  },
}))

watchEffect(() => {
  document.documentElement.style.setProperty('--primary-color', themeStore.primaryColor)
})
</script>

<template>
  <NConfigProvider :theme="theme" :theme-overrides="themeOverrides" :locale="zhCN" :date-locale="dateZhCN">
    <NLoadingBarProvider>
      <NDialogProvider>
        <NMessageProvider>
          <NNotificationProvider>
            <ErrorBoundary>
              <RouterView />
            </ErrorBoundary>
          </NNotificationProvider>
        </NMessageProvider>
      </NDialogProvider>
    </NLoadingBarProvider>
  </NConfigProvider>
</template>
