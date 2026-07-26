import { defineStore } from 'pinia'
import { ref } from 'vue'
import { useAuthStore } from '@/store/auth'

export interface AiNotifyMessage {
  subject: string
  type: 'image' | 'video'
  taskId: number
  status: 'completed' | 'failed'
}

/**
 * AI 任务通知 SSE 长连接管理。
 * 通过 fetch + ReadableStream 连接 GET /ai/notify（带 Authorization header，绕过 EventSource 限制）。
 * 后端任务完成时经 Redis pub/sub 广播 → SSE 推送，前端收到后触发回调刷新列表。
 * 断线自动指数退避重连，登录态变化时重连切换 subject。
 */
export const useAiNotifyStore = defineStore('aiNotify', () => {
  const connected = ref(false)
  const listeners: Array<(msg: AiNotifyMessage) => void> = []
  let controller: AbortController | null = null
  let reconnectTimer: number | null = null
  let reconnectAttempts = 0
  let manualClose = false

  function connect() {
    if (controller) return
    manualClose = false
    const authStore = useAuthStore()
    const base = import.meta.env.VITE_API_BASE_URL
    controller = new AbortController()

    fetch(`${base}/ai/notify`, {
      method: 'GET',
      headers: {
        Authorization: authStore.token ? `Bearer ${authStore.token}` : '',
      },
      signal: controller.signal,
    })
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        connected.value = true
        reconnectAttempts = 0
        const reader = res.body!.getReader()
        const decoder = new TextDecoder()
        let buf = ''
        let currentEvent = ''
        const pump = (): Promise<void> => {
          return reader.read().then(({ done, value }) => {
            if (done) {
              connected.value = false
              if (!manualClose) scheduleReconnect()
              return
            }
            buf += decoder.decode(value, { stream: true })
            const lines = buf.split('\n')
            buf = lines.pop()!
            for (const line of lines) {
              const trimmed = line.trim()
              if (trimmed.startsWith('event:')) {
                currentEvent = trimmed.slice(6).trim()
              } else if (trimmed.startsWith('data:')) {
                const data = trimmed.slice(5).trim()
                handleEvent(currentEvent, data)
                currentEvent = ''
              }
            }
            return pump()
          })
        }
        return pump()
      })
      .catch((err) => {
        connected.value = false
        if (err.name === 'AbortError') return
        if (!manualClose) scheduleReconnect()
      })
  }

  function handleEvent(eventName: string, data: string) {
    if (eventName === 'ping' || eventName === 'connected') return
    if (eventName === 'task') {
      try {
        const msg: AiNotifyMessage = JSON.parse(data)
        for (const cb of listeners) {
          cb(msg)
        }
      } catch {
        // 忽略解析失败
      }
    }
  }

  function scheduleReconnect() {
    if (manualClose) return
    if (reconnectTimer) clearTimeout(reconnectTimer)
    const delay = Math.min(1000 * Math.pow(2, reconnectAttempts), 30000)
    reconnectAttempts++
    reconnectTimer = window.setTimeout(() => {
      controller = null
      connect()
    }, delay)
  }

  function disconnect() {
    manualClose = true
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    if (controller) {
      controller.abort()
      controller = null
    }
    connected.value = false
    reconnectAttempts = 0
  }

  function onTaskNotify(cb: (msg: AiNotifyMessage) => void): () => void {
    listeners.push(cb)
    return () => {
      const idx = listeners.indexOf(cb)
      if (idx >= 0) listeners.splice(idx, 1)
    }
  }

  return { connected, connect, disconnect, onTaskNotify }
})
