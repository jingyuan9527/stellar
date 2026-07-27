import { request } from './request'
import type { Profile, ProfileProject } from '@/types/api'

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

/** 公开项目展示列表（about 页游客可调） */
export function getPublicProfileProjects() {
  return request<ProfileProject[]>({ url: '/public/profile-projects', method: 'get' })
}

/** 管理获取项目列表 */
export function getProfileProjects() {
  return request<ProfileProject[]>({ url: '/profile/project/list', method: 'get' })
}

/** 管理新增项目 */
export function createProfileProject(data: Partial<ProfileProject>) {
  return request<void>({ url: '/profile/project', method: 'post', data })
}

/** 管理更新项目 */
export function updateProfileProject(data: Partial<ProfileProject>) {
  return request<void>({ url: '/profile/project', method: 'put', data })
}

/** 管理删除项目 */
export function deleteProfileProject(id: number) {
  return request<void>({ url: `/profile/project/${id}`, method: 'delete' })
}
