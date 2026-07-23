import service, { request } from './request'
import type { PageResult, SysLog, SysLogQuery } from '@/types/api'

export function getLogPage(params: SysLogQuery) {
  return request<PageResult<SysLog>>({ url: '/log/page', method: 'get', params })
}

export function getLogDetail(id: number) {
  return request<SysLog>({ url: `/log/${id}`, method: 'get' })
}

export function exportLogs(params: SysLogQuery) {
  return service
    .get('/log/export', { params, responseType: 'blob' })
    .then((res) => res as unknown as Blob)
}
