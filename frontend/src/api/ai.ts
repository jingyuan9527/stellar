import service, { request } from './request'
import { useAuthStore } from '@/store/auth'
import type { AiConfig, AiTemplate, AiTemplateQuery, AiCopyResult, AiUsageStats, PageResult } from '@/types/api'

// ===== AI 配置 =====

export function getAiConfig() {
  return request<AiConfig>({ url: '/ai/config', method: 'get' })
}

export function updateAiConfig(data: { endpoint: string; apiKey: string; model: string }) {
  return request<void>({ url: '/ai/config', method: 'put', data })
}

export function fetchAiModels() {
  return request<string[]>({ url: '/ai/config/models', method: 'get' })
}

export function testAiConnection() {
  return request<void>({ url: '/ai/config/test', method: 'get' })
}

// ===== AI 模板 =====

export function getAiTemplatePage(params: AiTemplateQuery) {
  return request<PageResult<AiTemplate>>({ url: '/ai/template/page', method: 'get', params })
}

export function createAiTemplate(data: { name: string; platform: string; prompt: string }) {
  return request<void>({ url: '/ai/template', method: 'post', data })
}

export function updateAiTemplate(id: number, data: { name: string; platform: string; prompt: string }) {
  return request<void>({ url: `/ai/template/${id}`, method: 'put', data })
}

export function deleteAiTemplate(id: number) {
  return request<void>({ url: `/ai/template/${id}`, method: 'delete' })
}

export function resetAiTemplate(id: number) {
  return request<void>({ url: `/ai/template/${id}/reset`, method: 'put' })
}

// ===== AI 流式对话 =====

/**
 * 流式聊天，通过 fetch + ReadableStream 读取后端 SSE。
 * 后端代理调用 LLM，API Key 不暴露给浏览器。
 */
export async function streamAiChat(
  prompt: string,
  onDelta: (full: string) => void,
  signal: AbortSignal,
  userConfig?: { endpoint?: string; apiKey?: string; model?: string },
): Promise<string> {
  const authStore = useAuthStore()
  const base = import.meta.env.VITE_API_BASE_URL
  const body: Record<string, string> = { prompt }
  if (userConfig?.endpoint && userConfig?.apiKey && userConfig?.model) {
    body.endpoint = userConfig.endpoint
    body.apiKey = userConfig.apiKey
    body.model = userConfig.model
  }
  const res = await fetch(`${base}/ai/chat/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${authStore.token}`,
    },
    body: JSON.stringify(body),
    signal,
  })

  if (!res.ok) throw new Error(`HTTP ${res.status}`)

  const reader = res.body!.getReader()
  const decoder = new TextDecoder()
  let full = ''
  let buf = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buf += decoder.decode(value, { stream: true })
    const lines = buf.split('\n')
    buf = lines.pop()!
    for (const line of lines) {
      const trimmed = line.trim()
      if (!trimmed.startsWith('data:')) continue
      const data = trimmed.slice(5).trim()
      let json: { content?: string; done?: boolean; error?: string }
      try {
        json = JSON.parse(data)
      } catch {
        continue
      }
      if (json.done) return full
      if (json.error) throw new Error(json.error)
      if (json.content) {
        full += json.content
        onDelta(full)
      }
    }
  }

  return full
}

// ===== AI 文案历史 =====

export function getCopyResultPage(params: { pageNum: number; pageSize: number }) {
  return request<PageResult<AiCopyResult>>({ url: '/ai/copy-result/page', method: 'get', params })
}

export function saveCopyResult(data: { topic: string; templateId?: number; result: string }) {
  return request<void>({ url: '/ai/copy-result', method: 'post', data })
}

export function deleteCopyResult(id: number) {
  return request<void>({ url: `/ai/copy-result/${id}`, method: 'delete' })
}

export function clearCopyResults() {
  return request<void>({ url: '/ai/copy-result', method: 'delete' })
}

// ===== AI token 统计 =====

/** token 消费统计（仪表盘，需登录） */
export function getAiUsageStats() {
  return request<AiUsageStats>({ url: '/ai/chat/usage/stats', method: 'get' })
}
