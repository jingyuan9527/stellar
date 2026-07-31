import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import type { Platform, PromptTemplate } from '../types'
import { builtinTemplates } from '../lib/builtinTemplates'
import { createDebouncedPersist } from './debouncedPersist'

const STORAGE_KEY = 'stellar-video:templates'

function loadState(): PromptTemplate[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw || JSON.parse(raw).length === 0) return [...builtinTemplates]
    return JSON.parse(raw)
  } catch {
    return [...builtinTemplates]
  }
}

export const useTemplateStore = defineStore('video-template', () => {
  const templates = ref<PromptTemplate[]>(loadState())

  function addTemplate(data: { name: string; platform: Platform; prompt: string }) {
    templates.value.push({
      id: `t-${Date.now()}`,
      name: data.name,
      platform: data.platform,
      prompt: data.prompt,
      builtIn: false,
      updatedAt: Date.now(),
    })
  }

  function updateTemplate(id: string, patch: Partial<PromptTemplate>) {
    const t = templates.value.find((x) => x.id === id)
    if (t) {
      Object.assign(t, patch, { updatedAt: Date.now() })
    }
  }

  function deleteTemplate(id: string) {
    templates.value = templates.value.filter((t) => t.builtIn || t.id !== id)
  }

  function resetBuiltin(id: string) {
    const builtin = builtinTemplates.find((b) => b.id === id)
    const t = templates.value.find((x) => x.id === id)
    if (builtin && t) {
      Object.assign(t, { prompt: builtin.prompt, name: builtin.name, platform: builtin.platform, updatedAt: Date.now() })
    }
  }

  function resetToBuiltin() {
    templates.value = [...builtinTemplates]
  }

  function persist() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(templates.value))
  }

  function rehydrate() {
    templates.value = loadState()
  }

  const debouncedPersist = createDebouncedPersist(persist)
  watch(templates, debouncedPersist.schedule, { deep: true })

  return { templates, addTemplate, updateTemplate, deleteTemplate, resetBuiltin, resetToBuiltin, rehydrate }
})
