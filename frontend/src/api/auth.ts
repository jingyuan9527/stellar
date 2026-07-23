import { request } from './request'
import type { LoginRequest, LoginResult } from '@/types/api'

export function login(data: LoginRequest) {
  return request<LoginResult>({ url: '/auth/login', method: 'post', data })
}

export function logout() {
  return request<void>({ url: '/auth/logout', method: 'post' })
}
