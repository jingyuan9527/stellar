import { request } from './request'
import type { SysUser } from '@/types/api'

export function getUserInfo() {
  return request<SysUser>({ url: '/user/info', method: 'get' })
}

export function getUserList() {
  return request<SysUser[]>({ url: '/user/list', method: 'get' })
}

export function changePassword(data: { oldPassword: string; newPassword: string }) {
  return request<void>({ url: '/user/change-password', method: 'post', data })
}
