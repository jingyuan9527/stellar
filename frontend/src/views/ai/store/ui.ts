import { defineStore } from 'pinia'
import { ref } from 'vue'

export type AiModalName = 'api' | null

export const useUIStore = defineStore('ai-ui', () => {
  const modal = ref<AiModalName>(null)

  function openModal(m: AiModalName) {
    modal.value = m
  }

  function closeModal() {
    modal.value = null
  }

  return { modal, openModal, closeModal }
})
