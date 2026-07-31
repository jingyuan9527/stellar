import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import type { ApiConfig } from '../types'

const STORAGE_KEY = 'stellar-video:api-config'

const defaults: ApiConfig = { endpoint: '', apiKey: '', model: '' }

function loadState(): ApiConfig {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return { ...defaults }
    return { ...defaults, ...JSON.parse(raw) }
  } catch {
    return { ...defaults }
  }
}

export const useApiConfigStore = defineStore('video-api', () => {
  const initial = loadState()
  const state = ref<ApiConfig>(initial)

  function update(patch: Partial<ApiConfig>) {
    state.value = { ...state.value, ...patch }
  }

  function reset() {
    state.value = { ...defaults }
  }

  function persist() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state.value))
  }

  function rehydrate() {
    state.value = loadState()
  }

  watch(state, persist, { deep: true })

  return { state, update, reset, rehydrate }
})
