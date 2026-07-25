<script setup lang="ts">
import { NCard } from 'naive-ui'

withDefaults(defineProps<{
  asideTitle?: string
  resultTitle?: string
  historyTitle?: string
}>(), {
  asideTitle: '生成配置',
  resultTitle: '生成结果',
  historyTitle: '生成历史',
})
</script>

<template>
  <div class="ai-gen-layout">
    <aside class="ai-gen-aside">
      <NCard :title="asideTitle" :bordered="false">
        <slot name="aside" />
      </NCard>
    </aside>
    <main class="ai-gen-main">
      <NCard v-if="$slots.result" :title="resultTitle" :bordered="false">
        <slot name="result" />
      </NCard>
      <NCard v-if="$slots.history" :title="historyTitle" :bordered="false">
        <template v-if="$slots['history-extra']" #header-extra>
          <slot name="history-extra" />
        </template>
        <slot name="history" />
      </NCard>
    </main>
  </div>
</template>

<style scoped>
.ai-gen-layout {
  display: grid;
  grid-template-columns: 360px 1fr;
  gap: 16px;
  align-items: start;
}

.ai-gen-aside {
  position: sticky;
  top: 8px;
  max-height: calc(100vh - 16px);
  overflow-y: auto;
}

.ai-gen-main {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

@media (max-width: 1023px) {
  .ai-gen-layout {
    grid-template-columns: 1fr;
  }

  .ai-gen-aside {
    position: static;
    max-height: none;
    overflow-y: visible;
  }
}
</style>
