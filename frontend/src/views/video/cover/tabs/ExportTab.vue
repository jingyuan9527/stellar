<script setup lang="ts">
import { computed, ref } from 'vue'
import { NButton, NRadioGroup, NRadioButton, NSpace, useMessage } from 'naive-ui'
import { toPng } from 'html-to-image'
import { useCoverStore } from '../../store/cover'
import { ratios, ratioList } from '../../lib/ratios'
import { presets, getPreset } from '../../lib/presets'
import type { Ratio } from '../../types'
import { useThemeStore } from '@/store/theme'

const props = defineProps<{
  getCanvasEl: () => HTMLElement | null
}>()

const coverStore = useCoverStore()
const s = computed(() => coverStore.state)
const message = useMessage()
const themeStore = useThemeStore()
const busy = ref(false)

function update(patch: Partial<typeof s.value>) {
  coverStore.update(patch)
}

function onRatioChange(v: string) {
  const ratio = v as Ratio
  const newPresets = presets[ratio]
  coverStore.update({ ratio, presetId: newPresets[0].id })
}

async function download() {
  const el = props.getCanvasEl()
  if (!el) return
  busy.value = true
  try {
    const ratio = ratios[s.value.ratio]
    const preset = getPreset(s.value.ratio, s.value.presetId)
    const dataUrl = await toPng(el, {
      pixelRatio: Math.max(2, Math.min(4, preset.width / ratio.width)),
      cacheBust: true,
    })
    const link = document.createElement('a')
    const safeTitle = (s.value.title.trim() || 'video-cover').replace(/[\\/:*?"<>|]/g, '-').slice(0, 40)
    link.download = `${safeTitle}-${preset.id}.png`
    link.href = dataUrl
    link.click()
    message.success('PNG 已生成并开始下载')
  } catch {
    message.error('导出失败，请重试')
  } finally {
    busy.value = false
  }
}

const activePresets = computed(() => presets[s.value.ratio])
</script>

<template>
  <div class="tab-content">
    <div class="form-item">
      <span class="label">画布方向</span>
      <NRadioGroup :value="s.ratio" @update:value="onRatioChange">
        <NRadioButton v-for="r in ratioList" :key="r" :value="r">
          {{ ratios[r].label }}
        </NRadioButton>
      </NRadioGroup>
    </div>

    <div class="form-item">
      <span class="label">导出尺寸</span>
      <div class="preset-list">
        <button
          v-for="p in activePresets"
          :key="p.id"
          type="button"
          class="preset-btn"
          :class="{ active: s.presetId === p.id }"
          @click="update({ presetId: p.id })"
        >
          <div class="preset-name">{{ p.name }}</div>
          <div class="preset-desc">{{ p.desc }}</div>
        </button>
      </div>
    </div>

    <NSpace>
      <NButton type="primary" :loading="busy" @click="download">下载 PNG</NButton>
    </NSpace>
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
  gap: 8px;
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
  padding: 12px;
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
