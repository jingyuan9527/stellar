import { request } from './request'
import type { DashboardStats } from '@/types/api'

/** 仪表盘聚合统计（管理后台首页，需登录） */
export function getDashboardStats() {
  return request<DashboardStats>({ url: '/dashboard/stats', method: 'get' })
}
