import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import type { CoverState } from '../types'

const STORAGE_KEY = 'stellar-video:cover-state'

const defaultCover: CoverState = {
  ratio: 'landscape',
  gradientId: 'aurora',
  templateId: 'center',
  presetId: 'youtube',
  fontPresetId: 'emphasis',
  title: '',
  subtitle: '',
  badgeText: '',
  badgeVisible: true,
  titleColor: '#ffffff',
  subtitleColor: '#f1f5f9',
  strokeColor: '#000000',
  strokeWidth: 0,
  shadowStrength: 35,
  glowStrength: 0,
  titleFontsize: 0,
  subtitleFontsize: 0,
  badgeColor: '#ffffff',
  backgroundImage: '',
  backgroundOverride: '',
}

function loadState(): CoverState {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return { ...defaultCover }
    return { ...defaultCover, ...JSON.parse(raw), backgroundImage: '' }
  } catch {
    return { ...defaultCover }
  }
}

export const useCoverStore = defineStore('video-cover', () => {
  const initial = loadState()
  const state = ref<CoverState>(initial)

  function update(patch: Partial<CoverState>) {
    state.value = { ...state.value, ...patch }
  }

  function reset() {
    state.value = { ...defaultCover }
  }

  function persist() {
    const { backgroundImage: _bg, ...rest } = state.value
    void _bg
    localStorage.setItem(STORAGE_KEY, JSON.stringify(rest))
  }

  function rehydrate() {
    state.value = loadState()
  }

  watch(state, persist, { deep: true })

  return { state, update, reset, rehydrate }
})
