import { request } from './request'
import type { Showcase, ShowcaseQuery, PageResult } from '@/types/api'

/** 公开作品橱窗列表（游客可调） */
export function getPublicShowcase() {
  return request<Showcase[]>({ url: '/public/showcase', method: 'get' })
}

/** 管理分页 */
export function getShowcasePage(params: ShowcaseQuery) {
  return request<PageResult<Showcase>>({ url: '/showcase/page', method: 'get', params })
}

export function createShowcase(data: Partial<Showcase>) {
  return request<void>({ url: '/showcase', method: 'post', data })
}

export function updateShowcase(id: number, data: Partial<Showcase>) {
  return request<void>({ url: `/showcase/${id}`, method: 'put', data })
}

export function deleteShowcase(id: number) {
  return request<void>({ url: `/showcase/${id}`, method: 'delete' })
}
