<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import type { CSSProperties } from 'vue'
import { useCoverStore } from '../store/cover'
import { getGradient } from '../lib/gradients'
import { ratios } from '../lib/ratios'
import { getTemplateConfig } from '../lib/templateConfig'
import { getFontPreset } from '../lib/fontPresets'
import { fitTextToBox } from '../lib/fitText'
import { buildTextShadow, combineLetterSpacing, normalizeHexColor } from '../lib/textShadow'

const coverStore = useCoverStore()
const s = computed(() => coverStore.state)

const titleRef = ref<HTMLElement | null>(null)
const subtitleRef = ref<HTMLElement | null>(null)
const rootRef = ref<HTMLElement | null>(null)

const gradient = computed(() => getGradient(s.value.gradientId))
const ratio = computed(() => ratios[s.value.ratio])
const tc = computed(() => getTemplateConfig(s.value.templateId, s.value.ratio))
const font = computed(() => getFontPreset(s.value.fontPresetId))

const titleColor = computed(() => normalizeHexColor(s.value.titleColor, '#ffffff'))
const subtitleColor = computed(() => normalizeHexColor(s.value.subtitleColor, '#f1f5f9'))
const strokeColor = computed(() => normalizeHexColor(s.value.strokeColor, '#000000'))

const titleText = computed(() => s.value.title.trim() || '请输入主标题')
const subtitleText = computed(() => s.value.subtitle.trim() || '请输入副标题')
const badgeText = computed(() => s.value.badgeText.trim())

const bgValue = computed(() => s.value.backgroundOverride || gradient.value.value)

const canvasStyle = computed<CSSProperties>(() => ({
  width: `${ratio.value.width}px`,
  height: `${ratio.value.height}px`,
  background: bgValue.value,
  position: 'relative',
  display: 'flex',
  overflow: 'hidden',
  borderRadius: '28px',
  flexShrink: 0,
  boxShadow: '0 30px 80px rgba(0,0,0,0.45)',
  border: '1px solid rgba(255,255,255,0.15)',
}))

const titleStyle = computed<CSSProperties>(() => ({
  wordBreak: 'break-word',
  filter: 'drop-shadow(0 8px 24px rgba(0,0,0,0.35))',
  margin: 0,
  color: titleColor.value,
  fontFamily: font.value.titleFont,
  textShadow: buildTextShadow(strokeColor.value, s.value.strokeWidth, s.value.shadowStrength, s.value.glowStrength),
}))

const subtitleStyle = computed<CSSProperties>(() => ({
  wordBreak: 'break-word',
  margin: 0,
  marginTop: `${tc.value.subtitleMarginTop}px`,
  color: subtitleColor.value,
  fontFamily: font.value.subtitleFont,
  textShadow: buildTextShadow(
    strokeColor.value,
    Math.max(0, s.value.strokeWidth - 1),
    Math.max(0, s.value.shadowStrength - 8),
    s.value.glowStrength * 0.75,
  ),
}))

const badgeStyle = computed<CSSProperties>(() => ({ ...tc.value.badgeStyle, color: s.value.badgeColor }))

const highlightStyle: CSSProperties = {
  position: 'absolute',
  inset: 0,
  background:
    'radial-gradient(circle at top right, rgba(255,255,255,0.28), transparent 30%), radial-gradient(circle at bottom left, rgba(255,255,255,0.12), transparent 28%)',
}

const overlayStyle: CSSProperties = { position: 'absolute', inset: 0, background: 'rgba(0,0,0,0.12)' }

const topGradientStyle: CSSProperties = {
  position: 'absolute',
  insetInline: 0,
  top: 0,
  height: '144px',
  background: 'linear-gradient(to bottom, rgba(255,255,255,0.1), transparent)',
}

function fitTitle() {
  const el = titleRef.value
  if (!el) return
  const cfg = tc.value
  const f = font.value
  const r = ratio.value
  const maxW = r.width * cfg.titleMaxWidth
  const maxH = s.value.ratio === 'portrait' ? r.height * 0.34 : r.height * 0.3
  const text = s.value.title.trim() || '请输入主标题'
  if (s.value.titleFontsize > 0) {
    el.textContent = text
    el.style.fontSize = `${s.value.titleFontsize}px`
    el.style.maxWidth = `${maxW}px`
    el.style.fontWeight = String(f.titleWeight)
    el.style.letterSpacing = combineLetterSpacing(cfg.titleLetterSpacing, f.titleSpacingOffset)
    el.style.lineHeight = String(cfg.titleLineHeight)
  } else {
    fitTextToBox(el, text, {
      min: Math.round(cfg.titleRange[0] * f.titleScale),
      max: Math.round(cfg.titleRange[1] * f.titleScale),
      lineHeight: cfg.titleLineHeight,
      maxWidth: maxW,
      maxHeight: maxH,
      weight: f.titleWeight,
      letterSpacing: combineLetterSpacing(cfg.titleLetterSpacing, f.titleSpacingOffset),
    })
  }
}

function fitSubtitle() {
  const el = subtitleRef.value
  if (!el) return
  const cfg = tc.value
  const f = font.value
  const r = ratio.value
  const maxW = r.width * cfg.subtitleMaxWidth
  const maxH = s.value.ratio === 'portrait' ? r.height * 0.14 : r.height * 0.12
  const text = s.value.subtitle.trim() || '请输入副标题'
  if (s.value.subtitleFontsize > 0) {
    el.textContent = text
    el.style.fontSize = `${s.value.subtitleFontsize}px`
    el.style.maxWidth = `${maxW}px`
    el.style.fontWeight = String(f.subtitleWeight)
    el.style.letterSpacing = combineLetterSpacing('-0.01', f.subtitleSpacingOffset)
    el.style.lineHeight = String(cfg.subtitleLineHeight)
  } else {
    fitTextToBox(el, text, {
      min: Math.round(cfg.subtitleRange[0] * f.subtitleScale),
      max: Math.round(cfg.subtitleRange[1] * f.subtitleScale),
      lineHeight: cfg.subtitleLineHeight,
      maxWidth: maxW,
      maxHeight: maxH,
      weight: f.subtitleWeight,
      letterSpacing: combineLetterSpacing('-0.01', f.subtitleSpacingOffset),
    })
  }
}

watch(
  () => [s.value.title, s.value.titleFontsize, s.value.templateId, s.value.ratio, s.value.fontPresetId],
  fitTitle,
  { flush: 'post', immediate: true },
)

watch(
  () => [s.value.subtitle, s.value.subtitleFontsize, s.value.templateId, s.value.ratio, s.value.fontPresetId],
  fitSubtitle,
  { flush: 'post', immediate: true },
)

defineExpose({
  getEl: () => rootRef.value,
})
</script>

<template>
  <article ref="rootRef" :style="canvasStyle">
    <div :style="highlightStyle" />
    <div v-if="s.backgroundImage" :style="{
      position: 'absolute',
      inset: 0,
      backgroundImage: `url(${s.backgroundImage})`,
      backgroundPosition: 'center',
      backgroundRepeat: 'no-repeat',
      backgroundSize: 'cover',
    }" />
    <div :style="overlayStyle" />
    <div :style="topGradientStyle" />
    <div :style="tc.frameStyle">
      <div :style="tc.stackStyle">
        <span v-if="s.badgeVisible && badgeText" :style="badgeStyle">{{ badgeText }}</span>
        <h3 ref="titleRef" :style="titleStyle">{{ titleText }}</h3>
        <p ref="subtitleRef" :style="subtitleStyle">{{ subtitleText }}</p>
      </div>
    </div>
  </article>
</template>
