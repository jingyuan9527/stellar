import { defineStore } from 'pinia'
import { ref } from 'vue'

export type ModalName = 'api' | 'template' | 'clear' | 'drafts' | null

export const useUIStore = defineStore('video-ui', () => {
  const modal = ref<ModalName>(null)

  function openModal(m: ModalName) {
    modal.value = m
  }

  function closeModal() {
    modal.value = null
  }

  return { modal, openModal, closeModal }
})
