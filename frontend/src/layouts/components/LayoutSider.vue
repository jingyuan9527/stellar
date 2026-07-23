<script setup lang="ts">
import { h } from 'vue'
import { NIcon } from 'naive-ui'
import { useThemeStore } from '@/store/theme'
import { iconMap } from '@/utils/icons'
import LayoutMenu from './LayoutMenu.vue'

const themeStore = useThemeStore()

function renderLogoIcon() {
  return h(NIcon, { size: 26, color: '#18a058' }, { default: () => h(iconMap.grid) })
}
</script>

<template>
  <NLayoutSider
    bordered
    collapse-mode="width"
    :collapsed-width="64"
    :width="220"
    :collapsed="themeStore.siderCollapsed"
    show-trigger="bar"
    @collapse="themeStore.siderCollapsed = true"
    @expand="themeStore.siderCollapsed = false"
  >
    <div class="logo" :class="{ collapsed: themeStore.siderCollapsed }">
      <component :is="renderLogoIcon" />
      <span v-show="!themeStore.siderCollapsed" class="logo-text">Stellar Admin</span>
    </div>
    <LayoutMenu :collapsed="themeStore.siderCollapsed" />
  </NLayoutSider>
</template>

<style scoped>
.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 56px;
  padding: 0 18px;
  border-bottom: 1px solid var(--n-border-color, rgba(0, 0, 0, 0.06));
  overflow: hidden;
  white-space: nowrap;
}

.logo.collapsed {
  justify-content: center;
  padding: 0;
}

.logo-text {
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0.5px;
}
</style>
