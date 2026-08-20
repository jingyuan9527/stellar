<script setup lang="ts">
// 通用列表骨架屏：若干 shimmer 行，替换各页自造的「加载中…」文本态
withDefaults(defineProps<{ rows?: number; height?: string }>(), {
  rows: 4,
  height: '72px',
})
</script>

<template>
  <div class="skeleton-list" role="status" aria-label="加载中">
    <div v-for="i in rows" :key="i" class="skeleton-line" :style="{ height }" />
  </div>
</template>

<style scoped>
.skeleton-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 8px 0;
}
</style>