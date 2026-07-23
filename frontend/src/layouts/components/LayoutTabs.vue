<script setup lang="ts">
import { h } from 'vue'
import { NIcon, NDropdown, NButton } from 'naive-ui'
import type { DropdownOption } from 'naive-ui'
import { useRouter } from 'vue-router'
import { useTabStore } from '@/store/tab'
import { iconMap } from '@/utils/icons'

const router = useRouter()
const tabStore = useTabStore()

const emit = defineEmits<{ refresh: [] }>()

function navigate(path: string) {
  if (path !== tabStore.activePath) {
    router.push(path)
  }
}

function closeTab(path: string) {
  tabStore.removeTab(path)
}

function renderIcon(name: string) {
  const Icon = iconMap[name]
  if (!Icon) return null
  return h(NIcon, { size: 16 }, { default: () => h(Icon) })
}

const actionOptions: DropdownOption[] = [
  { label: '刷新当前', key: 'refresh', icon: () => renderIcon('refresh') },
  { type: 'divider', key: 'd1' },
  { label: '关闭其他', key: 'others', icon: () => renderIcon('expand') },
  { label: '关闭全部', key: 'all', icon: () => renderIcon('close') },
]

function handleAction(key: string) {
  if (key === 'refresh') {
    emit('refresh')
  } else if (key === 'others') {
    tabStore.removeOthers(tabStore.activePath)
  } else if (key === 'all') {
    tabStore.removeAll()
  }
}
</script>

<template>
  <div class="tabs-bar">
    <div class="tabs-scroll">
      <div
        v-for="tab in tabStore.tabs"
        :key="tab.path"
        class="tab-item"
        :class="{ active: tab.path === tabStore.activePath }"
        @click="navigate(tab.path)"
      >
        <span class="tab-title">{{ tab.title }}</span>
        <NIcon
          v-if="tab.closable"
          class="tab-close"
          size="14"
          @click.stop="closeTab(tab.path)"
        >
          <component :is="iconMap.close" />
        </NIcon>
      </div>
    </div>
    <div class="tabs-actions">
      <NDropdown :options="actionOptions" trigger="click" @select="handleAction">
        <NButton text>
          <NIcon size="16">
            <component :is="iconMap.menu" />
          </NIcon>
        </NButton>
      </NDropdown>
    </div>
  </div>
</template>

<style scoped>
.tabs-bar {
  display: flex;
  align-items: center;
  height: 40px;
  padding: 0 8px;
  border-bottom: 1px solid var(--n-border-color, rgba(0, 0, 0, 0.06));
}

.tabs-scroll {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 1;
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: none;
}

.tabs-scroll::-webkit-scrollbar {
  display: none;
}

.tab-item {
  display: flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  padding: 0 10px;
  border-radius: 4px;
  font-size: 13px;
  white-space: nowrap;
  cursor: pointer;
  transition: background 0.2s;
  flex-shrink: 0;
}

.tab-item:hover {
  background: rgba(127, 127, 127, 0.14);
}

.tab-item.active {
  background: var(--primary-color, #18a058);
  color: #fff;
}

.tab-close {
  border-radius: 50%;
  padding: 1px;
}

.tab-close:hover {
  background: rgba(255, 255, 255, 0.3);
}

.tabs-actions {
  display: flex;
  align-items: center;
  padding: 0 4px;
}
</style>
