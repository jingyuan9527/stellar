import type { App } from 'vue'

/**
 * 前端错误上报：Vue 渲染错误 / window error / 未捕获 Promise rejection /
 * 组件边界错误统一收敛到此，POST 到后端落 sys_log（模块=前端错误）。
 * 上报用原生 fetch，不依赖 axios 拦截器，避免错误循环与 UI 依赖。
 */

const REPORT_API = `${import.meta.env.VITE_API_BASE_URL || '/api'}/system/client-error`
const DEDUP_WINDOW_MS = 30_000
let lastKey = ''
let lastTime = 0
let reportedCount = 0
const MAX_REPORT_PER_SESSION = 10

export interface ClientErrorPayload {
  message: string
  stack?: string
  source?: string
  url?: string
}

export function reportClientError(payload: ClientErrorPayload) {
  // 会话内最多上报 10 条，避免错误风暴打爆日志
  if (reportedCount >= MAX_REPORT_PER_SESSION) return
  // 同源（同消息）30 秒内去重
  const now = Date.now()
  const key = payload.message
  if (key === lastKey && now - lastTime < DEDUP_WINDOW_MS) return
  lastKey = key
  lastTime = now
  reportedCount += 1

  try {
    fetch(REPORT_API, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        message: String(payload.message).slice(0, 500),
        stack: payload.stack ? String(payload.stack).slice(0, 4000) : undefined,
        source: payload.source,
        url: payload.url || location.href,
      }),
    }).catch(() => {
      // 上报失败静默，不影响页面
    })
  } catch {
    // 忽略
  }
}

function toMessage(err: unknown): string {
  if (err instanceof Error) return err.message
  if (typeof err === 'string') return err
  try {
    return JSON.stringify(err)
  } catch {
    return String(err)
  }
}

function toStack(err: unknown): string | undefined {
  if (err instanceof Error) return err.stack
  return undefined
}

/** 挂载全局错误捕获（在 app.mount 之前调用） */
export function setupGlobalErrorCapture(app: App) {
  app.config.errorHandler = (err, _instance, info) => {
    reportClientError({
      message: `[Vue 渲染] ${toMessage(err)}`,
      stack: `${toStack(err) || ''}\ninfo: ${info}`,
      source: 'vue',
    })
  }

  window.addEventListener('error', (e) => {
    // 资源加载错误（script/img 等）也有 message 为空的情况
    const msg = (e as ErrorEvent).message || `资源加载失败: ${(e as ErrorEvent).filename || ''}`
    reportClientError({
      message: `[window error] ${msg}`,
      stack: (e as ErrorEvent).error ? toStack((e as ErrorEvent).error) : undefined,
      source: 'window',
      url: (e as ErrorEvent).filename || location.href,
    })
  })

  window.addEventListener('unhandledrejection', (e) => {
    reportClientError({
      message: `[Promise rejection] ${toMessage(e.reason)}`,
      stack: toStack(e.reason),
      source: 'promise',
    })
  })
}