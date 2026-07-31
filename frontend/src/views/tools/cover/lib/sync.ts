import { useCoverStore } from '../store/cover'
import { useApiConfigStore } from '@/views/ai/store/apiConfig'
import { useTemplateStore } from '../store/template'
import { useCopyResultStore } from '@/views/ai/store/copyResult'
import { useCoverDraftsStore } from '../store/coverDrafts'

const KEY_MAP: Record<string, () => void> = {
  'stellar-video:cover-state': () => useCoverStore().rehydrate(),
  'stellar-video:api-config': () => useApiConfigStore().rehydrate(),
  'stellar-video:templates': () => useTemplateStore().rehydrate(),
  'stellar-video:copy-result': () => useCopyResultStore().rehydrate(),
  'stellar-video:cover-drafts': () => useCoverDraftsStore().rehydrate(),
}

export function setupCrossTabSync(): () => void {
  const handler = (e: StorageEvent) => {
    if (!e.key) return
    const rehydrate = KEY_MAP[e.key]
    if (rehydrate) rehydrate()
  }
  window.addEventListener('storage', handler)
  return () => window.removeEventListener('storage', handler)
}
