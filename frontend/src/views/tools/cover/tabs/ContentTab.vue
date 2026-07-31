<script setup lang="ts">
import { computed } from 'vue'
import { NInput, NSwitch } from 'naive-ui'
import { useCoverStore } from '../store/cover'

const coverStore = useCoverStore()
const s = computed(() => coverStore.state)

function update(patch: Partial<typeof s.value>) {
  coverStore.update(patch)
}
</script>

<template>
  <div class="tab-content">
    <div class="form-item">
      <span class="label">主标题</span>
      <NInput
        :value="s.title"
        type="textarea"
        :autosize="{ minRows: 3, maxRows: 6 }"
        placeholder="输入主标题"
        @update:value="(v: string) => update({ title: v })"
      />
    </div>
    <div class="form-item">
      <span class="label">副标题</span>
      <NInput
        :value="s.subtitle"
        placeholder="输入副标题"
        @update:value="(v: string) => update({ subtitle: v })"
      />
    </div>
    <div class="form-item">
      <span class="label">顶部角标文案</span>
      <NInput
        :value="s.badgeText"
        placeholder="可选，如：热点解析 / 新手必看"
        @update:value="(v: string) => update({ badgeText: v })"
      />
    </div>
    <div class="form-item row">
      <span class="label">显示角标</span>
      <NSwitch
        :value="s.badgeVisible"
        @update:value="(v: boolean) => update({ badgeVisible: v })"
      />
    </div>
  </div>
</template>

<style scoped>
.tab-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-item.row {
  flex-direction: row;
  align-items: center;
  gap: 12px;
}

.label {
  font-size: 13px;
  font-weight: 500;
  opacity: 0.8;
}
</style>
