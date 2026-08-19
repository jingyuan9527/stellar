<script setup lang="ts">
import { NIcon, NButton } from 'naive-ui'
import { useRouter } from 'vue-router'
import { useThemeStore } from '@/store/theme'
import { useIsMobile } from '@/composables/useBreakpoint'
import { iconMap } from '@/utils/icons'

const router = useRouter()
const themeStore = useThemeStore()
const isMobile = useIsMobile()

const navItems = [
  { label: '工具', to: '/home' },
  { label: '游戏', to: '/game/math' },
  { label: '关于我', to: '/about' },
]

function go(to: string) {
  router.push(to)
}
</script>

<template>
  <div class="public-header">
    <div class="ph-logo" @click="go('/home')">
      <NIcon size="22" :color="themeStore.primaryColor">
        <component :is="iconMap.grid" />
      </NIcon>
      <span class="ph-logo-text">Stellar</span>
    </div>

    <nav v-if="!isMobile" class="ph-nav">
      <a
        v-for="item in navItems"
        :key="item.to"
        class="ph-nav-item"
        :class="{ active: $route.path === item.to || (item.to === '/home' && $route.path === '/home') }"
        @click.prevent="go(item.to)"
      >
        {{ item.label }}
      </a>
    </nav>

    <div class="ph-actions">
      <NButton text @click="themeStore.toggleDarkMode">
        <template #icon>
          <NIcon size="20">
            <component :is="themeStore.darkMode ? iconMap.sunny : iconMap.moon" />
          </NIcon>
        </template>
      </NButton>
      <NButton type="primary" size="small" @click="go('/login')">登录</NButton>
    </div>
  </div>
</template>

<style scoped>
.public-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  height: 56px;
  padding: 0 24px;
  background: var(--c-fill);
  border-bottom: 1px solid var(--c-border);
  position: sticky;
  top: 0;
  z-index: 10;
}

.ph-logo {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  user-select: none;
}

.ph-logo-text {
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.ph-nav {
  display: flex;
  gap: 28px;
}

.ph-nav-item {
  font-size: 14px;
  color: var(--c-text-2);
  cursor: pointer;
  padding: 4px 0;
  transition: color 0.2s;
  position: relative;
}

.ph-nav-item:hover,
.ph-nav-item.active {
  color: var(--c-brand);
}

.ph-nav-item.active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -2px;
  height: 2px;
  border-radius: 1px;
  background: var(--c-brand);
}

.ph-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

@media (max-width: 768px) {
  .public-header {
    padding: 0 16px;
  }
}
</style>