<script setup lang="ts">
import { computed } from 'vue'
import { darkTheme, zhCN, dateZhCN, type GlobalThemeOverrides } from 'naive-ui'
import { useThemeStore } from '@/store/theme'

const themeStore = useThemeStore()

const theme = computed(() => (themeStore.darkMode ? darkTheme : null))

const themeOverrides = computed<GlobalThemeOverrides>(() => ({
  common: {
    primaryColor: themeStore.primaryColor,
    primaryColorHover: themeStore.primaryColor,
    borderRadius: '6px',
  },
}))
</script>

<template>
  <NConfigProvider :theme="theme" :theme-overrides="themeOverrides" :locale="zhCN" :date-locale="dateZhCN">
    <NLoadingBarProvider>
      <NDialogProvider>
        <NMessageProvider>
          <NNotificationProvider>
            <RouterView />
          </NNotificationProvider>
        </NMessageProvider>
      </NDialogProvider>
    </NLoadingBarProvider>
  </NConfigProvider>
</template>
