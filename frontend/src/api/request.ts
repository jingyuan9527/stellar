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
      // 401 不弹提示，跳登录即反馈；其余按后端 message 提示
      if (res.code === 401) {
        useAuthStore().handleUnauthorized()
      } else {
        window.$message?.error(res.message || '请求失败')
      }
      return Promise.reject(new Error(res.message || 'Error'))
    }
    // 直接返回业务数据体，调用方（request<T>）取 .data 即得 payload，避免无意义的 AxiosResponse 强转
    return response.data
  },
  async (error) => {
    const status = error?.response?.status
    // 401 不弹提示，直接跳登录
    if (status === 401) {
      useAuthStore().handleUnauthorized()
      return Promise.reject(error)
    }
    // 超时单独提示
    if (error.code === 'ECONNABORTED') {
      window.$message?.error('请求超时，请稍后重试')
      return Promise.reject(error)
    }
    // 其他 HTTP 错误：优先用后端 message，兜底网络异常
    const data = error?.response?.data
    if (data instanceof Blob) {
      // 用 await 拉平，避免 Blob.text() 嵌套回调
      try {
        const text = await data.text()
        const json = JSON.parse(text)
        window.$message?.error(json.message || '请求失败')
      } catch {
        window.$message?.error('网络异常')
      }
      return Promise.reject(error)
    }
    window.$message?.error(data?.message || '网络异常')
    return Promise.reject(error)
  },
)

export function request<T = unknown>(config: AxiosRequestConfig): Promise<T> {
  return service(config).then((res) => (res as unknown as ApiResult<T>).data)
}

export default service
