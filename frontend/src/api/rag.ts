import { request } from './request'
import type {
  PageResult, RagEvalCase, RagEvalDetail, RagEvalResultRow, RagEvalRunVO,
  RagFeedbackVO,
} from '@/types/api'

// ===== RAG 评估（数据飞轮：golden set + 离线跑分 + bad case 复盘）=====

export function pageRagEvalCases(params: { pageNum: number; pageSize: number }) {
  return request<PageResult<RagEvalCase>>({ url: '/ai/rag/eval/case', method: 'get', params })
}

export function createRagEvalCase(data: { query: string; kbId?: number | null; expectedSources: string[]; note?: string }) {
  return request<void>({ url: '/ai/rag/eval/case', method: 'post', data })
}

export function updateRagEvalCase(data: { id: number; query: string; kbId?: number | null; expectedSources: string[]; note?: string }) {
  return request<void>({ url: '/ai/rag/eval/case', method: 'put', data })
}

export function deleteRagEvalCase(id: number) {
  return request<void>({ url: `/ai/rag/eval/case/${id}`, method: 'delete' })
}

/**
 * 跑分：默认纯检索路径（不改写/不重排/不调 LLM，秒级）；
 * mode=full 走完整管线（含改写/混合检索/重排/loop，与线上一致，每条用例调 LLM，较慢）。
 */
export function runRagEval(mode: 'retrieval' | 'full' = 'retrieval') {
  return request<RagEvalRunVO>({ url: '/ai/rag/eval/run', method: 'post', params: { mode }, timeout: 600000 })
}

/** 某次跑分批次的落库结果（历史回看/回归对比） */
export function getRagEvalRunResults(runId: string) {
  return request<RagEvalResultRow[]>({ url: `/ai/rag/eval/run/${runId}`, method: 'get' })
}

/** 最近的跑分批次列表 */
export function listRecentRagEvalRuns(limit = 20) {
  return request<string[]>({ url: '/ai/rag/eval/run', method: 'get', params: { limit } })
}

/** 反馈复盘分页（value 过滤：-1 坏样本 / 1 好样本 / 空全部） */
export function pageRagFeedback(params: { value?: number; pageNum: number; pageSize: number }) {
  return request<PageResult<RagFeedbackVO>>({ url: '/ai/rag/eval/feedback', method: 'get', params })
}