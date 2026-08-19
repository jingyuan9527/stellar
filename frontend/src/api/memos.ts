import { request } from './request'
import type {
  MemosConfig,
  MemosConfigUpdate,
  MemosJobResult,
  MemosNote,
  MemosQuery,
  MemosStats,
  MemosSyncLog,
  MemosSyncResult,
} from '@/types/api'

/** 拉取备忘同步配置 */
export function getMemosConfig() {
  return request<MemosConfig>({ url: '/memos/config', method: 'get' })
}

/** 保存备忘同步配置（域名/Token/提示词模板） */
export function saveMemosConfig(data: MemosConfigUpdate) {
  return request<void>({ url: '/memos/config', method: 'put', data })
}

/** 立即同步：全量拉取备份 + 标记远端已删（同步耗时操作，加大超时） */
export function pullMemos() {
  return request<MemosSyncResult>({ url: '/memos/pull', method: 'post', timeout: 600000 })
}

/** AI 打标签（勾选笔记 + 可选 TEXT 模型，打标后自动写回远端，同步耗时，加大超时） */
export function tagMemos(ids: number[], modelId?: number) {
  return request<MemosJobResult>({
    url: '/memos/tag',
    method: 'post',
    data: { ids, modelId },
    timeout: 600000,
  })
}

/** 标签写回 Memos（同步耗时，加大超时） */
export function pushMemosTags() {
  return request<MemosJobResult>({ url: '/memos/push-tags', method: 'post', timeout: 600000 })
}

/** 重建备忘笔记 RAG 向量索引（全量向量化，新增/变更自动增量，此按钮为兜底） */
export function rebuildMemosRag() {
  return request<MemosJobResult>({ url: '/memos/rag/rebuild', method: 'post', timeout: 600000 })
}

/** RAG 索引构建状态（存活笔记 已向量化数/总数 + 上次全量重建时间） */
export function getMemosRagStatus() {
  return request<{ total: number; embedded: number; pending: number; lastRebuildAt: string }>({
    url: '/memos/rag/status',
    method: 'get',
  })
}

/** 笔记分页 */
export function getMemosPage(params: MemosQuery) {
  return request<{ records: MemosNote[]; total: number }>({ url: '/memos/page', method: 'get', params })
}

/** 统计 */
export function getMemosStats() {
  return request<MemosStats>({ url: '/memos/stats', method: 'get' })
}

/** Webhook 配置：是否已配置签名密钥 */
export function getMemosWebhookConfig() {
  return request<{ secretConfigured: boolean }>({ url: '/memos/webhook/config', method: 'get' })
}

/** 同步状态记录分页（最近 3 天，含定时/手动「立即同步」） */
export function getMemosSyncLogPage(params: { pageNum: number; pageSize: number }) {
  return request<{ records: MemosSyncLog[]; total: number }>({
    url: '/memos/sync-log/page',
    method: 'get',
    params,
  })
}

/** 最近一次同步状态（无记录返回 null） */
export function getLatestMemosSyncLog() {
  return request<MemosSyncLog | null>({ url: '/memos/sync-log/latest', method: 'get' })
}

/** 保存 Webhook 签名密钥（whsec_ 开头，Memos 创建 webhook 时生成） */
export function saveMemosWebhookSecret(secret: string) {
  return request<void>({ url: '/memos/webhook/config', method: 'put', data: { secret } })
}
