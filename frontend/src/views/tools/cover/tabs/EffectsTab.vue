<script setup lang="ts">
import { computed } from 'vue'
import { NSlider, NColorPicker } from 'naive-ui'
import { useCoverStore } from '../store/cover'
import { templates } from '../lib/templates'
import { useThemeStore } from '@/store/theme'

const coverStore = useCoverStore()
const s = computed(() => coverStore.state)
const themeStore = useThemeStore()

function update(patch: Partial<typeof s.value>) {
  coverStore.update(patch)
}
</script>

<template>
  <div class="tab-content">
    <div class="form-item">
      <span class="label">排版模板</span>
      <div class="preset-list">
        <button
          v-for="t in templates"
          :key="t.id"
          type="button"
          class="preset-btn"
          :class="{ active: s.templateId === t.id }"
          @click="update({ templateId: t.id })"
        >
          <div class="preset-name">{{ t.name }}</div>
          <div class="preset-desc">{{ t.desc }}</div>
        </button>
      </div>
    </div>

    <div class="form-item">
      <span class="label">描边强度（{{ s.strokeWidth }}px）</span>
      <NSlider
        :value="s.strokeWidth"
        :min="0"
        :max="8"
        :step="0.5"
        @update:value="(v: number) => update({ strokeWidth: v })"
      />
    </div>

    <div class="form-item">
      <span class="label">描边颜色</span>
      <NColorPicker
        :value="s.strokeColor"
        :show-alpha="false"
        @update:value="(v: string) => update({ strokeColor: v })"
      />
    </div>

    <div class="form-item">
      <span class="label">阴影强度（{{ s.shadowStrength }}）</span>
      <NSlider
        :value="s.shadowStrength"
        :min="0"
        :max="100"
        @update:value="(v: number) => update({ shadowStrength: v })"
      />
    </div>

    <div class="form-item">
      <span class="label">发光强度（{{ s.glowStrength }}）</span>
      <NSlider
        :value="s.glowStrength"
        :min="0"
        :max="100"
        @update:value="(v: number) => update({ glowStrength: v })"
      />
    </div>
  </div>
</template>

<style scoped>
.tab-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.label {
  font-size: 13px;
  font-weight: 500;
  opacity: 0.8;
}

.preset-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.preset-btn {
  cursor: pointer;
  border-radius: 8px;
  border: 1px solid rgba(128, 128, 128, 0.2);
  background: transparent;
  padding: 10px 12px;
  text-align: left;
  width: 100%;
  color: inherit;
  transition: border-color 0.2s, background 0.2s;
}

.preset-btn.active {
  border-color: v-bind('themeStore.primaryColor');
  background: rgba(128, 128, 128, 0.08);
}

.preset-name {
  font-size: 13px;
  font-weight: 600;
}

.preset-desc {
  font-size: 11px;
  opacity: 0.6;
  margin-top: 2px;
}
</style>
