import { request } from './request'
import type { PageResult, SysFile, SysFileQuery } from '@/types/api'

/**
 * 上传文件（图片/音频），登录方可调用。返回相对 URL 如 /file/123，
 * 前端直接作为 <img src> 使用（dev 走 vite 代理 /file → 后端接口）。
 * @param isPublic 游客可见（落地页头像、海螺预设等公开素材传 true；默认私有仅本人可读）
 */
export function uploadFile(file: File, isPublic = false) {
  const form = new FormData()
  form.append('file', file)
  form.append('isPublic', String(isPublic))
  return request<string>({
    url: '/file/upload',
    method: 'post',
    data: form,
  })
}

/** 文件分页（管理后台） */
export function getFilePage(params: SysFileQuery) {
  return request<PageResult<SysFile>>({ url: '/file/page', method: 'get', params })
}

/** 硬删除单条 */
export function deleteFile(id: number) {
  return request<void>({ url: `/file/${id}`, method: 'delete' })
}

/** 批量硬删除 */
export function deleteFileBatch(ids: number[]) {
  return request<void>({ url: '/file/batch', method: 'delete', data: ids })
}
