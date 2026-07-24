import service, { request } from './request'
import type { ConchAnswer, ConchAnswerQuery, ConchAskResult, ConchRecord, PageResult } from '@/types/api'

/**
 * 神奇海螺提问，返回命中的回答文本 + 音频地址。
 */
export function askConch(question: string) {
  return request<ConchAskResult>({ url: '/tts/conch/ask', method: 'post', data: { question } })
}

/**
 * 按预设 ID 获取音频 Blob（用于自动播放/下载）。
 */
export function getConchAnswerAudio(id: number): Promise<Blob> {
  return service
    .get(`/tts/conch/answer/${id}/audio`, { responseType: 'blob', timeout: 30000 })
    .then((res) => res as unknown as Blob)
}

/**
 * 预设回答分页（管理后台）。
 */
export function getConchAnswerPage(params: ConchAnswerQuery) {
  return request<PageResult<ConchAnswer>>({ url: '/tts/conch/answer/page', method: 'get', params })
}

/**
 * 新增预设回答。
 */
export function createConchAnswer(data: { answerText: string; matchDescription?: string; fileId: number }) {
  return request<void>({ url: '/tts/conch/answer', method: 'post', data })
}

/**
 * 编辑预设回答。
 */
export function updateConchAnswer(data: { id: number; answerText: string; matchDescription?: string; fileId: number }) {
  return request<void>({ url: '/tts/conch/answer', method: 'put', data })
}

/**
 * 删除预设回答。
 */
export function deleteConchAnswer(id: number) {
  return request<void>({ url: `/tts/conch/answer/${id}`, method: 'delete' })
}

/**
 * 切换预设启用状态。
 */
export function toggleConchAnswerEnabled(id: number, enabled: number) {
  return request<void>({
    url: `/tts/conch/answer/${id}/enabled`,
    method: 'put',
    params: { enabled },
  })
}

/**
 * 提问历史分页（管理后台）。
 */
export function getConchRecordPage(pageNum: number, pageSize: number) {
  return request<PageResult<ConchRecord>>({
    url: '/tts/conch/record/page',
    method: 'get',
    params: { pageNum, pageSize },
  })
}
