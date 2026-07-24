import { request } from './request'
import type { Profile } from '@/types/api'

/** 公开个人介绍（游客可调，落地页展示） */
export function getPublicProfile() {
  return request<Profile>({ url: '/public/profile', method: 'get' })
}

/** 管理获取 */
export function getProfile() {
  return request<Profile>({ url: '/profile', method: 'get' })
}

/** 管理更新 */
export function updateProfile(data: Partial<Profile>) {
  return request<void>({ url: '/profile', method: 'put', data })
}
