export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
}

export interface SysUser {
  id: number
  username: string
  nickname: string
  avatar: string
  status: number
  createTime: string
  updateTime: string
}

export interface LoginResult {
  token: string
  userInfo: SysUser
}

export interface LoginRequest {
  username: string
  password: string
}

export interface SysLog {
  id: number
  module: string
  operationType: string
  operator: string
  requestMethod: string
  requestUrl: string
  javaMethod: string
  params: string
  status: number
  errorMsg: string
  ip: string
  duration: number
  createTime: string
}

export interface SysLogQuery {
  module?: string
  operator?: string
  status?: number | null
  startTime?: string | null
  endTime?: string | null
  pageNum?: number
  pageSize?: number
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}
