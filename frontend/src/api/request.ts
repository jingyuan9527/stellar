import axios from 'axios'
import type { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios'
import type { ApiResult } from '@/types/api'
import { useAuthStore } from '@/store/auth'

const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 15000,
})

service.interceptors.request.use(
  (config) => {
    const authStore = useAuthStore()
    if (authStore.token) {
      config.headers.Authorization = `Bearer ${authStore.token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

service.interceptors.response.use(
  (response) => {
    const res = response.data as ApiResult
    if (res.code !== undefined && res.code !== 200) {
      window.$message?.error(res.message || '请求失败')
      if (res.code === 401) {
        const authStore = useAuthStore()
        authStore.handleUnauthorized()
      }
      return Promise.reject(new Error(res.message || 'Error'))
    }
    return res as unknown as AxiosResponse
  },
  (error) => {
    const status = error?.response?.status
    if (status === 401) {
      const authStore = useAuthStore()
      authStore.handleUnauthorized()
    } else {
      window.$message?.error(error?.response?.data?.message || error.message || '网络异常')
    }
    return Promise.reject(error)
  },
)

export function request<T = unknown>(config: AxiosRequestConfig): Promise<T> {
  return service(config).then((res) => (res as unknown as ApiResult<T>).data)
}

export default service
