<script setup lang="ts">
import { computed } from 'vue'
import { NColorPicker, NSlider } from 'naive-ui'
import { useCoverStore } from '../../store/cover'
import { colorPresets } from '../../lib/colorPresets'
import { fontPresets } from '../../lib/fontPresets'
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
      <span class="label">主标题颜色</span>
      <NColorPicker
        :value="s.titleColor"
        :show-alpha="false"
        @update:value="(v: string) => update({ titleColor: v, badgeColor: v })"
      />
    </div>

    <div class="form-item">
      <span class="label">副标题颜色</span>
      <NColorPicker
        :value="s.subtitleColor"
        :show-alpha="false"
        @update:value="(v: string) => update({ subtitleColor: v })"
      />
    </div>

    <div class="form-item">
      <span class="label">文字色预设</span>
      <div class="swatch-grid">
        <button
          v-for="p in colorPresets"
          :key="p.id"
          type="button"
          class="color-swatch"
          @click="update({ titleColor: p.title, subtitleColor: p.subtitle, badgeColor: p.title })"
        >
          <div class="color-pair">
            <div :style="{ height: '28px', flex: 1, borderRadius: '6px', background: p.title }" />
            <div :style="{ height: '28px', flex: 1, borderRadius: '6px', background: p.subtitle }" />
          </div>
          <div class="color-name">{{ p.name }}</div>
        </button>
      </div>
    </div>

    <div class="form-item">
      <span class="label">字体预设</span>
      <div class="preset-list">
        <button
          v-for="f in fontPresets"
          :key="f.id"
          type="button"
          class="preset-btn"
          :class="{ active: s.fontPresetId === f.id }"
          @click="update({ fontPresetId: f.id })"
        >
          <div class="preset-name">{{ f.name }}</div>
          <div class="preset-desc">{{ f.desc }}</div>
        </button>
      </div>
    </div>

    <div class="form-item">
      <span class="label">主标题字号（{{ s.titleFontsize === 0 ? '自适应' : s.titleFontsize + 'px' }}）</span>
      <NSlider
        :value="s.titleFontsize"
        :min="0"
        :max="120"
        @update:value="(v: number) => update({ titleFontsize: v })"
      />
    </div>

    <div class="form-item">
      <span class="label">副标题字号（{{ s.subtitleFontsize === 0 ? '自适应' : s.subtitleFontsize + 'px' }}）</span>
      <NSlider
        :value="s.subtitleFontsize"
        :min="0"
        :max="60"
        @update:value="(v: number) => update({ subtitleFontsize: v })"
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

.swatch-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.color-swatch {
  cursor: pointer;
  border-radius: 8px;
  border: 1px solid rgba(128, 128, 128, 0.2);
  background: transparent;
  padding: 6px;
  width: 100%;
  color: inherit;
}

.color-pair {
  display: flex;
  gap: 4px;
}

.color-name {
  font-size: 11px;
  margin-top: 4px;
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
