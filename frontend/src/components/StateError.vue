<script setup lang="ts">
// 区块级错误占位：图标 + 标题 + 描述 + 重试按钮，替换各页「数据未回空白」。
// 配色走设计 token（--c-error-bg/--c-text-*），暗色自动生效。
import { NButton, NIcon } from 'naive-ui'
import { AlertCircleOutline, RefreshOutline } from '@vicons/ionicons5'

defineProps<{ title?: string; description?: string }>()

const emit = defineEmits<{ retry: [] }>()
</script>

<template>
  <div class="state-error" role="alert">
    <div class="state-error-icon">
      <NIcon size="28"><AlertCircleOutline /></NIcon>
    </div>
    <div class="state-error-title">{{ title || '加载失败' }}</div>
    <div class="state-error-desc">{{ description || '数据暂时未能取回，请稍后重试。' }}</div>
    <NButton type="error" secondary size="small" @click="emit('retry')">
      <template #icon>
        <NIcon><RefreshOutline /></NIcon>
      </template>
      重试
    </NButton>
  </div>
</template>

<style scoped>
.state-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 32px 24px;
  background: var(--c-error-bg);
  border-radius: var(--r-lg);
  text-align: center;
}
.state-error-icon {
  color: var(--c-error);
}
.state-error-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--c-text-1);
}
.state-error-desc {
  font-size: 13px;
  color: var(--c-text-3);
}
</style>