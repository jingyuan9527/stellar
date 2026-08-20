<script setup lang="ts">
import { computed, ref } from 'vue'
import { NButton, NSpace, useMessage } from 'naive-ui'
import { useCoverStore } from '../store/cover'
import { gradients, getGradient } from '../lib/gradients'
import type { Gradient } from '../types'

const coverStore = useCoverStore()
const s = computed(() => coverStore.state)
const message = useMessage()
const fileInput = ref<HTMLInputElement | null>(null)

function update(patch: Partial<typeof s.value>) {
  coverStore.update(patch)
}

function selectGradient(id: string) {
  const g = getGradient(id)
  coverStore.update({
    gradientId: id,
    backgroundOverride: '',
    ...(g.titleColor
      ? { titleColor: g.titleColor, subtitleColor: g.subtitleColor ?? g.titleColor, badgeColor: g.titleColor }
      : {}),
  })
}

function random() {
  let next: Gradient
  do {
    next = gradients[Math.floor(Math.random() * gradients.length)]
  } while (next.id === s.value.gradientId)
  selectGradient(next.id)
}

function swap() {
  const g = getGradient(s.value.gradientId)
  if (!g.titleColor) {
    message.info('仅纯色配对支持互换')
    return
  }
  const currentBg = s.value.backgroundOverride || g.value
  const currentText = s.value.titleColor
  coverStore.update({
    backgroundOverride: currentText === g.value ? '' : currentText,
    titleColor: currentBg,
    subtitleColor: currentBg,
    badgeColor: currentBg,
  })
}

function onFile(e: Event) {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = () => {
    update({ backgroundImage: reader.result as string })
    message.success('背景图片已上传')
  }
  reader.readAsDataURL(file)
  target.value = ''
}

const gradientItems = computed(() => gradients.filter((g) => !g.titleColor))
const solidItems = computed(() => gradients.filter((g) => g.titleColor))
</script>

<template>
  <div class="tab-content">
    <NSpace>
      <NButton size="small" @click="random">随机</NButton>
      <NButton size="small" @click="swap">互换 A⇌B</NButton>
    </NSpace>

    <div>
      <div class="section-title">渐变</div>
      <div class="swatch-grid">
        <button
          v-for="g in gradientItems"
          :key="g.id"
          type="button"
          class="swatch"
          :class="{ active: s.gradientId === g.id }"
          @click="selectGradient(g.id)"
        >
          <div class="swatch-preview" :style="{ background: g.value }" />
          <div class="swatch-name">{{ g.name }}</div>
        </button>
      </div>
    </div>

    <div>
      <div class="section-title">纯色配对 · 背景 + 文字</div>
      <div class="swatch-grid">
        <button
          v-for="g in solidItems"
          :key="g.id"
          type="button"
          class="swatch"
          :class="{ active: s.gradientId === g.id }"
          @click="selectGradient(g.id)"
        >
          <div class="swatch-preview" :style="{ background: g.value }" />
          <div class="swatch-name">{{ g.name }}</div>
        </button>
      </div>
    </div>

    <div>
      <div class="section-title">背景图片</div>
      <NSpace vertical :size="8">
        <input
          ref="fileInput"
          type="file"
          accept="image/png,image/jpeg,image/webp"
          hidden
          @change="onFile"
        />
        <NButton size="small" @click="fileInput?.click()">上传背景图片</NButton>
        <NButton v-if="s.backgroundImage" size="small" type="error" @click="update({ backgroundImage: '' })">
          移除背景图片
        </NButton>
        <span class="hint">
          {{ s.backgroundImage ? '已使用背景图片（覆盖渐变）' : '当前未使用背景图片（不持久化）' }}
        </span>
      </NSpace>
    </div>
  </div>
</template>

<style scoped>
.tab-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.section-title {
  margin-bottom: 8px;
}

.swatch-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.swatch {
  cursor: pointer;
  border-radius: var(--r-md);
  border: 1px solid rgba(128, 128, 128, 0.2);
  background: transparent;
  padding: 6px;
  text-align: left;
  width: 100%;
  color: inherit;
  transition: border-color 0.2s;
}

.swatch.active {
  border-color: var(--n-color-primary, var(--c-brand));
}

.swatch-preview {
  height: 40px;
  width: 100%;
  border-radius: var(--r-sm);
  border: 1px solid rgba(128, 128, 128, 0.15);
}

.swatch-name {
  font-size: 11px;
  margin-top: 4px;
  color: var(--c-text-2);
}

.hint {
  font-size: 11px;
  color: var(--c-text-3);
}
</style>
