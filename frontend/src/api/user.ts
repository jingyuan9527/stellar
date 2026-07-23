import { request } from './request'
import type { SysUser } from '@/types/api'

export function getUserInfo() {
  return request<SysUser>({ url: '/user/info', method: 'get' })
}
