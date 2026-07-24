<script setup lang="ts">
import { computed, h } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NIcon, NDropdown, NAvatar, NBreadcrumb, NBreadcrumbItem } from 'naive-ui'
import type { DropdownOption } from 'naive-ui'
import { useThemeStore } from '@/store/theme'
import { useAuthStore } from '@/store/auth'
import { useIsMobile } from '@/composables/useBreakpoint'
import { iconMap } from '@/utils/icons'

const route = useRoute()
const router = useRouter()
const themeStore = useThemeStore()
const authStore = useAuthStore()
const isMobile = useIsMobile()

const emit = defineEmits<{ 'open-theme': []; 'toggle-sider': [] }>()

const breadcrumbItems = computed(() =>
  route.matched
    .filter((m) => m.meta?.title && m.path !== '/')
    .map((m) => ({ title: m.meta!.title!, path: m.path })),
)

function renderIcon(name: string) {
  const Icon = iconMap[name]
  if (!Icon) return null
  return h(NIcon, { size: 18 }, { default: () => h(Icon) })
}

const userOptions: DropdownOption[] = [
  { label: '用户资料', key: 'profile', icon: () => renderIcon('person') },
  { type: 'divider', key: 'd1' },
  { label: '退出登录', key: 'logout', icon: () => renderIcon('logout') },
]

function handleUserSelect(key: string) {
  if (key === 'profile') {
    router.push('/system/user-profile')
  } else if (key === 'logout') {
    authStore.logout()
  }
}

const avatarText = computed(() => authStore.userInfo?.nickname?.charAt(0) || 'U')
</script>

<template>
  <div class="header">
    <div class="header-left">
      <NButton text class="collapse-btn" @click="emit('toggle-sider')">
        <template #icon>
          <NIcon size="20">
            <component :is="iconMap.menu" />
          </NIcon>
        </template>
      </NButton>
      <NBreadcrumb v-if="!isMobile">
        <NBreadcrumbItem v-for="item in breadcrumbItems" :key="item.path">
          {{ item.title }}
        </NBreadcrumbItem>
      </NBreadcrumb>
    </div>

    <div class="header-right">
      <NButton text @click="themeStore.toggleDarkMode">
        <template #icon>
          <NIcon size="20">
            <component :is="themeStore.darkMode ? iconMap.sunny : iconMap.moon" />
          </NIcon>
        </template>
      </NButton>
      <NButton text @click="emit('open-theme')">
        <template #icon>
          <NIcon size="20">
            <component :is="iconMap.palette" />
          </NIcon>
        </template>
      </NButton>
      <NDropdown v-if="authStore.isLogin" :options="userOptions" @select="handleUserSelect">
        <div class="user-info">
          <NAvatar round size="small" :color="themeStore.primaryColor">
            {{ avatarText }}
          </NAvatar>
          <span v-if="!isMobile" class="username">{{ authStore.userInfo?.nickname || '用户' }}</span>
        </div>
      </NDropdown>
      <NButton v-else type="primary" size="small" @click="router.push('/login')">登录</NButton>
    </div>
  </div>
</template>

<style scoped>
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  padding: 0 16px;
  border-bottom: 1px solid var(--n-border-color, rgba(0, 0, 0, 0.06));
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.collapse-btn {
  cursor: pointer;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s;
}

.user-info:hover {
  background: rgba(127, 127, 127, 0.12);
}

.username {
  font-size: 14px;
}
</style>
