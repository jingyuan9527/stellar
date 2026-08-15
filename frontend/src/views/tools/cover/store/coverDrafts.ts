import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import type { CoverDraft, CoverState } from '../types'
import { createDebouncedPersist } from './debouncedPersist'

const STORAGE_KEY = 'stellar-video:cover-drafts'
/** localStorage 草稿条数上限，避免草稿无限增长撑满存储 */
const MAX_DRAFTS = 20

function loadState(): CoverDraft[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    return JSON.parse(raw)
  } catch {
    return []
  }
}

export const useCoverDraftsStore = defineStore('video-cover-drafts', () => {
  const drafts = ref<CoverDraft[]>(loadState())

  function saveDraft(name: string, state: CoverState) {
    drafts.value = [
      {
        id: `d-${Date.now()}`,
        name,
        state: { ...state, backgroundImage: '' },
        savedAt: Date.now(),
      },
      ...drafts.value,
    ]
    if (drafts.value.length > MAX_DRAFTS) {
      drafts.value = drafts.value.slice(0, MAX_DRAFTS)
    }
  }

  function renameDraft(id: string, name: string) {
    const d = drafts.value.find((x) => x.id === id)
    if (d) d.name = name
  }

  function deleteDraft(id: string) {
    drafts.value = drafts.value.filter((d) => d.id !== id)
  }

  function clear() {
    drafts.value = []
  }

  function persist() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(drafts.value))
  }

  function rehydrate() {
    drafts.value = loadState()
  }

  const debouncedPersist = createDebouncedPersist(persist)
  watch(drafts, debouncedPersist.schedule, { deep: true })

  return { drafts, saveDraft, renameDraft, deleteDraft, clear, rehydrate }
})
