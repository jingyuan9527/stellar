import service, { request } from './request'
import { useAuthStore } from '@/store/auth'
import type {
  AiPersona, AiChatSession, AiChatSessionAdmin, AiChatMessage,
  AiMemory, AiKnowledgeBase, AiKnowledgeChunk, PageResult,
} from '@/types/api'

// ===== 人设 =====

export function listEnabledPersonas() {
  return request<AiPersona[]>({ url: '/ai/persona/enabled', method: 'get' })
}

export function listAllPersonas() {
  return request<AiPersona[]>({ url: '/ai/persona', method: 'get' })
}

export function createPersona(data: { name: string; systemPrompt: string; description?: string; enabled?: number; sortOrder?: number }) {
  return request<void>({ url: '/ai/persona', method: 'post', data })
}

export function updatePersona(data: { id: number; name: string; systemPrompt: string; description?: string; enabled?: number; sortOrder?: number }) {
  return request<void>({ url: '/ai/persona', method: 'put', data })
}

export function togglePersonaEnabled(id: number, enabled: number) {
  return request<void>({ url: `/ai/persona/${id}/enabled`, method: 'put', params: { enabled } })
}

export function deletePersona(id: number) {
  return request<void>({ url: `/ai/persona/${id}`, method: 'delete' })
}

export function resetPersona(id: number) {
  return request<void>({ url: `/ai/persona/${id}/reset`, method: 'put' })
}

// ===== 聊天会话 =====

export function createChatSession(data: { personaId?: number | null; kbId?: number | null; title?: string }) {
  return request<AiChatSession>({ url: '/ai/chat/session', method: 'post', data })
}

export function listMyChatSessions() {
  return request<AiChatSession[]>({ url: '/ai/chat/session', method: 'get' })
}

export function pageAllChatSessions(params: { pageNum: number; pageSize: number }) {
  return request<PageResult<AiChatSessionAdmin>>({ url: '/ai/chat/session/all', method: 'get', params })
}

/**
 * 回复反馈（👍有用/👎没用/0 取消）：同一主体同一消息重复打分会覆盖。
 * 评分进入 rag_feedback 表，是 RAG 数据飞轮评估集原料（复盘 bad case + 回归用）。
 */
export function submitFeedback(messageId: number, value: number, comment?: string) {
  return request<void>({ url: '/ai/chat/feedback', method: 'post', data: { messageId, value, comment } })
}

export function getChatMessages(sessionId: number) {
  return request<AiChatMessage[]>({ url: `/ai/chat/session/${sessionId}/messages`, method: 'get' })
}

/** 管理员查看任意会话消息（需登录） */
export function getChatMessagesAdmin(sessionId: number) {
  return request<AiChatMessage[]>({ url: `/ai/chat/session/${sessionId}/messages/admin`, method: 'get' })
}

export function updateChatSession(id: number, title: string) {
  return request<void>({ url: `/ai/chat/session/${id}`, method: 'put', data: { title } })
}

export function deleteChatSession(id: number) {
  return request<void>({ url: `/ai/chat/session/${id}`, method: 'delete' })
}

/** 管理员删除任意会话（需登录） */
export function deleteChatSessionAdmin(id: number) {
  return request<void>({ url: `/ai/chat/session/${id}/admin`, method: 'delete' })
}

export function clearMyChatSessions() {
  return request<number>({ url: '/ai/chat/session', method: 'delete' })
}

/**
 * 多轮流式聊天：fetch + ReadableStream 读取后端 SSE。
 * 后端组装多轮上下文（人设+RAG+记忆），返回 assistant 完整文本。
 */
export async function streamChat(
  sessionId: number,
  userMessage: string,
  onDelta: (full: string) => void,
  signal: AbortSignal,
  modelId?: number | null,
  voice?: string | null,
  onStatus?: (status: string) => void,
): Promise<string> {
  const authStore = useAuthStore()
  const base = import.meta.env.VITE_API_BASE_URL
  const body: Record<string, string | number> = { sessionId, userMessage }
  if (modelId) body.modelId = modelId
  if (voice) body.voice = voice
  const res = await fetch(`${base}/ai/chat/session/stream`, {
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
      let json: { content?: string; done?: boolean; error?: string; status?: string }
      try {
        json = JSON.parse(data)
      } catch {
        continue
      }
      if (json.done) return full
      if (json.error) throw new Error(json.error)
      if (json.status) {
        onStatus?.(json.status)
        continue
      }
      if (json.content) {
        full += json.content
        onDelta(full)
      }
    }
  }
  return full
}

// ===== 长期记忆 =====

export function pageAllMemories(params: { pageNum: number; pageSize: number }) {
  return request<PageResult<AiMemory>>({ url: '/ai/memory', method: 'get', params })
}

export function pageMemoriesByUser(userId: number, params: { pageNum: number; pageSize: number }) {
  return request<PageResult<AiMemory>>({ url: `/ai/memory/user/${userId}`, method: 'get', params })
}

export function updateMemory(id: number, content: string) {
  return request<void>({ url: `/ai/memory/${id}`, method: 'put', data: { content } })
}

export function deleteMemory(id: number) {
  return request<void>({ url: `/ai/memory/${id}`, method: 'delete' })
}

export function createMemory(data: { userId: number; content: string }) {
  return request<void>({ url: '/ai/memory', method: 'post', data })
}

export function summarizeSession(sessionId: number) {
  return request<number>({ url: `/ai/memory/summarize/${sessionId}`, method: 'post', timeout: 120000 })
}

// ===== 知识库 =====

export function listKnowledgeBases() {
  return request<AiKnowledgeBase[]>({ url: '/ai/knowledge', method: 'get' })
}

export function createKnowledgeBase(data: { name: string; description?: string; embeddingModelId?: number | null }) {
  return request<void>({ url: '/ai/knowledge', method: 'post', data })
}

export function updateKnowledgeBase(data: { id: number; name: string; description?: string; embeddingModelId?: number | null }) {
  return request<void>({ url: '/ai/knowledge', method: 'put', data })
}

export function deleteKnowledgeBase(id: number) {
  return request<void>({ url: `/ai/knowledge/${id}`, method: 'delete' })
}

export function pageKnowledgeChunks(kbId: number, params: { pageNum: number; pageSize: number }) {
  return request<PageResult<AiKnowledgeChunk>>({ url: `/ai/knowledge/${kbId}/chunk`, method: 'get', params })
}

export function addKnowledgeDocument(kbId: number, data: { text: string; sourceName?: string }) {
  return request<number>({ url: `/ai/knowledge/${kbId}/document`, method: 'post', data, timeout: 120000 })
}

export function uploadKnowledgeDocument(kbId: number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request<number>({ url: `/ai/knowledge/${kbId}/document/file`, method: 'post', data: formData, timeout: 120000, headers: { 'Content-Type': 'multipart/form-data' } })
}

export function deleteKnowledgeChunk(id: number) {
  return request<void>({ url: `/ai/knowledge/chunk/${id}`, method: 'delete' })
}

/** 更新文档：按来源名替换全部旧分块并重新向量化 */
export function updateKnowledgeDocument(kbId: number, data: { text: string; sourceName: string }) {
  return request<number>({ url: `/ai/knowledge/${kbId}/document`, method: 'put', data, timeout: 120000 })
}

/** 知识库全部文档来源名（更新文档下拉用） */
export function listKnowledgeSources(kbId: number) {
  return request<string[]>({ url: `/ai/knowledge/${kbId}/sources`, method: 'get' })
}

export function rebuildKnowledgeBase(kbId: number) {
  return request<void>({ url: `/ai/knowledge/${kbId}/rebuild`, method: 'put', timeout: 300000 })
}
