import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import type { CopyResult } from '../types'

const STORAGE_KEY = 'stellar-video:copy-result'
const MAX_HISTORY = 20

function loadState(): CopyResult[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    return JSON.parse(raw)
  } catch {
    return []
  }
}

export const useCopyResultStore = defineStore('video-copy-result', () => {
  const history = ref<CopyResult[]>(loadState())

  function addResult(r: CopyResult) {
    history.value = [r, ...history.value].slice(0, MAX_HISTORY)
  }

  function deleteResult(id: string) {
    history.value = history.value.filter((r) => r.id !== id)
  }

  function clear() {
    history.value = []
  }

  function persist() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(history.value))
  }

  function rehydrate() {
    history.value = loadState()
  }

  watch(history, persist, { deep: true })

  return { history, addResult, deleteResult, clear, rehydrate }
})
