<script setup lang="ts">
import { NSpace, NButton, NTag } from 'naive-ui'
import type { CopyResultData } from '@/types/api'

defineProps<{
  data: CopyResultData
  metaTime?: string
}>()

const emit = defineEmits<{
  copy: [text: string]
  sendCover: [title: string]
}>()

function formatTag(t: string) {
  return t.startsWith('#') ? t : `#${t}`
}
</script>

<template>
  <div class="result-wrap">
    <div v-if="metaTime" class="result-meta">{{ metaTime }}</div>

    <div class="result-section">
      <div class="section-header">
        <span class="section-label">标题</span>
      </div>
      <NSpace vertical :size="8" style="margin-top: 8px">
        <div v-for="(t, i) in data.titles" :key="i" class="title-row">
          <span class="title-text">{{ t }}</span>
          <NSpace size="small">
            <NButton size="small" @click="emit('copy', t)">复制</NButton>
            <NButton size="small" type="primary" @click="emit('sendCover', t)">发送到封面</NButton>
          </NSpace>
        </div>
      </NSpace>
    </div>

    <div class="result-section">
      <div class="section-header">
        <span class="section-label">简介</span>
        <NButton size="tiny" text type="primary" @click="emit('copy', data.description)">复制</NButton>
      </div>
      <p class="section-text">{{ data.description }}</p>
    </div>

    <div class="result-section">
      <div class="section-header">
        <span class="section-label">标签</span>
        <NButton
          size="tiny"
          text
          type="primary"
          @click="emit('copy', data.tags.map(formatTag).join(' '))"
        >
          复制全部
        </NButton>
      </div>
      <NSpace :size="8" style="margin-top: 8px">
        <NTag
          v-for="(t, i) in data.tags"
          :key="i"
          :bordered="false"
          style="cursor: pointer"
          @click="emit('copy', formatTag(t))"
        >
          {{ formatTag(t) }}
        </NTag>
      </NSpace>
    </div>
  </div>
</template>

<style scoped>
.result-meta {
  font-size: 12px;
  opacity: 0.6;
  margin-bottom: 12px;
}

.result-section {
  margin-bottom: 16px;
}

.result-section:last-child {
  margin-bottom: 0;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.section-label {
  font-weight: 600;
  font-size: 14px;
}

.section-text {
  margin: 8px 0 0;
  line-height: 1.6;
}

.title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.title-text {
  flex: 1;
}
</style>
