import { request } from './request'
import { useAuthStore } from '@/store/auth'
import type { AiModel, AiProvider, AiTemplate, AiTemplateQuery, AiChatRecord, AiImageTask, AiVideoTask, AiVideoHistory, AiVideoStatus, PageResult } from '@/types/api'

// ===== AI 供应商 =====

export function getAiProviderList() {
  return request<AiProvider[]>({ url: '/ai/provider', method: 'get' })
}

export function createAiProvider(data: { name: string; endpoint: string; apiKey?: string; enabled?: number; sortOrder?: number }) {
  return request<void>({ url: '/ai/provider', method: 'post', data })
}

export function updateAiProvider(data: { id: number; name: string; endpoint: string; apiKey?: string; enabled?: number; sortOrder?: number }) {
  return request<void>({ url: '/ai/provider', method: 'put', data })
}

export function deleteAiProvider(id: number) {
  return request<void>({ url: `/ai/provider/${id}`, method: 'delete' })
}

export function toggleAiProviderEnabled(id: number, enabled: number) {
  return request<void>({ url: `/ai/provider/${id}/enabled`, method: 'put', params: { enabled } })
}

export function fetchAiProviderModels(id: number) {
  return request<string[]>({ url: `/ai/provider/${id}/models`, method: 'get' })
}

export function testAiProviderConnection(id: number, model?: string) {
  return request<void>({ url: `/ai/provider/${id}/test`, method: 'get', params: model ? { model } : undefined })
}

// ===== AI 模型 =====

export function getAiModelList(providerId?: number) {
  return request<AiModel[]>({ url: '/ai/model', method: 'get', params: providerId ? { providerId } : undefined })
}

export function getAiModelsByType(modelType: string) {
  return request<AiModel[]>({ url: `/ai/model/type/${modelType}`, method: 'get' })
}

export function createAiModel(data: { providerId: number; model: string; modelType: string; enabled?: number; isDefault?: number; sortOrder?: number }) {
  return request<void>({ url: '/ai/model', method: 'post', data })
}

export function updateAiModel(data: { id: number; providerId?: number; model: string; modelType: string; enabled?: number; isDefault?: number; sortOrder?: number }) {
  return request<void>({ url: '/ai/model', method: 'put', data })
}

export function deleteAiModel(id: number) {
  return request<void>({ url: `/ai/model/${id}`, method: 'delete' })
}

export function toggleAiModelEnabled(id: number, enabled: number) {
  return request<void>({ url: `/ai/model/${id}/enabled`, method: 'put', params: { enabled } })
}

export function setAiModelDefault(id: number) {
  return request<void>({ url: `/ai/model/${id}/default`, method: 'put' })
}

// ===== AI 图片生成（异步任务）=====

export function createAiImage(data: { modelId: number; prompt: string; size?: string; ratio?: string }) {
  return request<number>({ url: '/ai/image/create', method: 'post', data })
}

export function getAiImageTask(taskId: number) {
  return request<AiImageTask>({ url: `/ai/image/task/${taskId}`, method: 'get' })
}

/** 图片生成历史分页（登录按账号、游客按 IP） */
export function getAiImagePage(params: { pageNum: number; pageSize: number }) {
  return request<PageResult<AiImageTask>>({ url: '/ai/image/page', method: 'get', params })
}

/** 删除图片生成历史（连关联文件一起删） */
export function deleteAiImage(taskId: number) {
  return request<void>({ url: `/ai/image/${taskId}`, method: 'delete' })
}

// ===== AI 视频生成（异步任务）=====

export function createAiVideo(data: { modelId: number; prompt: string; ratio?: string; duration?: number; width?: number; height?: number; numFrames?: number; frameRate?: number }) {
  return request<AiVideoTask>({ url: '/ai/video/create', method: 'post', data, timeout: 60000 })
}

export function getAiVideoStatus(modelId: number, videoId: string) {
  return request<AiVideoStatus>({ url: '/ai/video/status', method: 'get', params: { modelId, videoId } })
}

/** 视频生成历史分页（登录按账号） */
export function getAiVideoPage(params: { pageNum: number; pageSize: number }) {
  return request<PageResult<AiVideoHistory>>({ url: '/ai/video/page', method: 'get', params })
}

/** 删除视频生成历史（连关联文件一起删） */
export function deleteAiVideo(taskId: number) {
  return request<void>({ url: `/ai/video/${taskId}`, method: 'delete' })
}

// ===== 系统设置 =====

export function getSetting(key: string) {
  return request<string>({ url: `/setting/${key}`, method: 'get' })
}

export function setSetting(key: string, value: string) {
  return request<void>({ url: `/setting/${key}`, method: 'put', params: { value } })
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
 * <p>opts.modelId 选项目模型；opts.endpoint+apiKey+model 用自带 key；都未传用 TEXT 默认模型。
 */
export async function streamAiChat(
  prompt: string,
  onDelta: (full: string) => void,
  signal: AbortSignal,
  opts?: { modelId?: number; endpoint?: string; apiKey?: string; model?: string },
): Promise<string> {
  const authStore = useAuthStore()
  const base = import.meta.env.VITE_API_BASE_URL
  const body: Record<string, string | number> = { prompt }
  if (opts?.modelId) {
    body.modelId = opts.modelId
  } else if (opts?.endpoint && opts?.apiKey && opts?.model) {
    body.endpoint = opts.endpoint
    body.apiKey = opts.apiKey
    body.model = opts.model
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

// ===== AI 文本生成历史（流式结束自动落库）=====

export function getChatRecordPage(params: { pageNum: number; pageSize: number }) {
  return request<PageResult<AiChatRecord>>({ url: '/ai/chat/record/page', method: 'get', params })
}

export function deleteChatRecord(id: number) {
  return request<void>({ url: `/ai/chat/record/${id}`, method: 'delete' })
}

export function clearChatRecords() {
  return request<void>({ url: '/ai/chat/record', method: 'delete' })
}

// ===== 统一 AI 任务历史 =====

export interface AiTaskRecord {
  id: number
  taskType: string
  subjectType: string
  subjectId: string
  providerId: number | null
  model: string | null
  prompt: string
  result: string | null
  status: string
  errorMsg: string | null
  fileId: number | null
  fileSize: number | null
  audioFormat: string | null
  extra: string | null
  requestTime: string
  responseTime: string | null
  durationMs: number | null
  createTime: string
}

export function getAiTaskPage(params: { taskType: string; pageNum: number; pageSize: number }) {
  return request<PageResult<AiTaskRecord>>({ url: '/ai/task/page', method: 'get', params })
}

export function deleteAiTask(id: number) {
  return request<void>({ url: `/ai/task/${id}`, method: 'delete' })
}

export function clearAiTasks(taskType: string) {
  return request<void>({ url: '/ai/task', method: 'delete', params: { taskType } })
}
