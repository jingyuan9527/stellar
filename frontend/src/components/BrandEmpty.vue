<script setup lang="ts">
import { NEmpty, NButton } from 'naive-ui'
withDefaults(defineProps<{
  title?: string
  description?: string
  size?: 'small' | 'medium' | 'large'
  showAction?: boolean
  actionText?: string
}>(), {
  title: '这里还什么都没有',
  description: '试着创建第一条内容吧',
  size: 'medium',
  showAction: false,
  actionText: '去创建',
})
const emit = defineEmits<{ action: [] }>()
</script>

<template>
  <div class="brand-empty" :class="`size-${size}`">
    <!-- 品牌微光插画：纯内联 SVG，无需外部资源，跟随主题 -->
    <svg class="brand-empty-art" viewBox="0 0 200 140" fill="none" aria-hidden="true">
      <defs>
        <linearGradient id="be-g" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stop-color="var(--c-brand)" stop-opacity=".18" />
          <stop offset="1" stop-color="var(--c-info)" stop-opacity=".18" />
        </linearGradient>
      </defs>
      <rect x="20" y="20" width="160" height="100" rx="16" fill="url(#be-g)" />
      <circle cx="64" cy="58" r="26" fill="var(--c-brand)" opacity=".14" />
      <circle cx="138" cy="86" r="20" fill="var(--c-info)" opacity=".14" />
      <rect x="78" y="50" width="64" height="9" rx="4.5" fill="var(--c-fill-2)" />
      <rect x="78" y="68" width="44" height="7" rx="3.5" fill="var(--c-fill-2)" />
    </svg>
    <NEmpty :description="description">
      <template #extra>
        <div class="brand-empty-text">
          <div class="brand-empty-title">{{ title }}</div>
          <NButton v-if="showAction" size="small" type="primary" secondary
                   class="brand-empty-btn" @click="emit('action')">{{ actionText }}</NButton>
        </div>
      </template>
    </NEmpty>
  </div>
</template>

<style scoped>
.brand-empty { display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 32px 0; }
.brand-empty-art { width: 160px; height: 112px; margin-bottom: 4px; }
.brand-empty-title { font-size: 14px; font-weight: 600; color: var(--c-text-1); margin-bottom: 6px; }
.brand-empty :deep(.n-empty__description) { color: var(--c-text-3); }
.size-small { padding: 16px 0; } .size-small .brand-empty-art { width: 110px; height: 78px; }
</style>