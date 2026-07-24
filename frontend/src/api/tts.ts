import service, { request } from './request'
import type { PageResult, TtsRecord, TtsRecordQuery } from '@/types/api'

export interface TtsSynthesizeParams {
  text: string
  voice: string
  rate: number
  pitch: number
  volume: number
}

/**
 * 调用 Edge TTS 合成语音，返回 MP3 Blob。
 */
export function synthesizeEdgeTts(data: TtsSynthesizeParams): Promise<Blob> {
  return service
    .post('/tts/edge/synthesize', data, {
      responseType: 'blob',
      timeout: 60000,
    })
    .then((res) => res as unknown as Blob)
}

/**
 * 分页查询合成历史。
 */
export function getTtsRecordPage(params: TtsRecordQuery) {
  return request<PageResult<TtsRecord>>({ url: '/tts/record/page', method: 'get', params })
}

/**
 * 按记录 ID 获取音频 Blob（用于试听与下载）。
 */
export function getTtsRecordAudio(id: number): Promise<Blob> {
  return service
    .get(`/tts/record/${id}/audio`, { responseType: 'blob', timeout: 30000 })
    .then((res) => res as unknown as Blob)
}

/**
 * 删除合成记录。
 */
export function deleteTtsRecord(id: number) {
  return request<void>({ url: `/tts/record/${id}`, method: 'delete' })
}
