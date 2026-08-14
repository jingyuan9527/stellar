import { request } from './request'
import type { MonitorOverview } from '@/types/api'

/** 系统监控实时快照（需登录，3-5s 轮询） */
export function getMonitorOverview() {
  return request<MonitorOverview>({ url: '/monitor/overview', method: 'get' })
}

/** 导出 Markdown 监控报告（blob 下载，需登录） */
export function exportMonitorReport() {
  return request<Blob>({ url: '/monitor/export', method: 'get', responseType: 'blob' })
}