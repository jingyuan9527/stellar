import { request } from './request'
import type { MenuVisibility, MenuVisibilityItem } from '@/types/api'

/** 游客可见菜单配置（游客可调，无需登录） */
export function getPublicMenuConfig() {
  return request<string[]>({ url: '/public/menu-config', method: 'get' })
}

/** 全量配置列表（管理后台） */
export function getMenuVisibilityList() {
  return request<MenuVisibility[]>({ url: '/menu-visibility/list', method: 'get' })
}

/** 批量保存（管理后台） */
export function batchUpdateMenuVisibility(data: MenuVisibilityItem[]) {
  return request<void>({ url: '/menu-visibility/batch', method: 'post', data })
}
