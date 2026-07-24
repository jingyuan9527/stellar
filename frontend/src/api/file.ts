import { request } from './request'

/**
 * 上传文件（图片），登录方可调用。返回相对 URL 如 /uploads/xxx.png，
 * 前端直接作为 <img src> 使用（dev 走 vite 代理 /uploads → 后端静态）。
 */
export function uploadFile(file: File) {
  const form = new FormData()
  form.append('file', file)
  return request<string>({
    url: '/file/upload',
    method: 'post',
    data: form,
  })
}
