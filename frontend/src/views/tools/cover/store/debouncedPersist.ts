export function createDebouncedPersist(persist: () => void, delay = 300) {
  let timer: ReturnType<typeof setTimeout> | null = null

  function schedule() {
    if (timer !== null) clearTimeout(timer)
    timer = setTimeout(() => {
      timer = null
      persist()
    }, delay)
  }

  function flush() {
    if (timer === null) return
    clearTimeout(timer)
    timer = null
    persist()
  }

  window.addEventListener('beforeunload', flush)

  return { schedule, flush }
}
