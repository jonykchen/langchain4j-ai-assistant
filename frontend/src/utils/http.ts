/**
 * Axios HTTP 客户端封装
 * 包含请求/响应拦截器，自动处理 Token 和错误
 */
import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios'
import type { ApiResponse } from '@/types'
import router from '@/router'

// 创建 Axios 实例
const http: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

/**
 * 请求拦截器：自动添加 Token
 */
http.interceptors.request.use(
  config => {
    // 从 localStorage 获取 Token
    const token = localStorage.getItem('access_token')

    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }

    return config
  },
  error => {
    return Promise.reject(error)
  }
)

/**
 * 响应拦截器：处理错误和 Token 过期
 */
http.interceptors.response.use(
  response => {
    // 后端返回格式为 ApiResponse: {code, message, data}
    const apiResponse = response.data as ApiResponse<unknown>

    if (apiResponse && typeof apiResponse === 'object' && 'code' in apiResponse) {
      // 检查业务状态码
      if (apiResponse.code !== 200) {
        // 业务错误，创建 Error 并抛出
        const error = new Error(apiResponse.message || '请求失败') as any
        error.response = response
        error.code = apiResponse.code
        return Promise.reject(error)
      }
      // 成功：提取 data 字段作为实际响应数据
      return apiResponse.data
    }

    // 兼容非标准响应格式
    return response.data
  },
  async error => {
    const { response, config } = error

    // 401 未授权
    if (response?.status === 401) {
      // 尝试刷新 Token
      const refreshToken = localStorage.getItem('refresh_token')

      if (refreshToken && !config._retry) {
        config._retry = true

        try {
          // 调用刷新 Token 接口
          const baseUrl = import.meta.env.VITE_API_BASE_URL || ''
          const res = await axios.post(
            baseUrl + '/auth/refresh',
            { refreshToken },
            { headers: { 'Content-Type': 'application/json' } }
          )

          const { accessToken, refreshToken: newRefreshToken } = (res.data as ApiResponse<any>).data

          // 保存新 Token
          localStorage.setItem('access_token', accessToken)
          if (newRefreshToken) {
            localStorage.setItem('refresh_token', newRefreshToken)
          }

          // 重试原请求
          config.headers.Authorization = `Bearer ${accessToken}`
          return http(config)
        } catch (refreshError) {
          // 刷新失败，清除登录状态
          localStorage.removeItem('access_token')
          localStorage.removeItem('refresh_token')
          localStorage.removeItem('user_info')

          // 跳转到登录页
          router.push('/login')
          return Promise.reject(refreshError)
        }
      }

      // 没有刷新 Token 或重试失败
      localStorage.removeItem('access_token')
      localStorage.removeItem('refresh_token')
      localStorage.removeItem('user_info')
      router.push('/login')
      return Promise.reject(error)
    }

    // 403 无权限
    if (response?.status === 403) {
      console.error('权限不足')
      return Promise.reject(error)
    }

    // 429 限流
    if (response?.status === 429) {
      console.warn('请求过于频繁，请稍后再试')
      return Promise.reject(error)
    }

    // 其他错误
    const message = response?.data?.message || '请求失败'
    console.error('API Error:', message)
    return Promise.reject(error)
  }
)

export default http as {
  get<T>(url: string, config?: AxiosRequestConfig): Promise<T>
  post<T>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T>
  put<T>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T>
  patch<T>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T>
  delete<T>(url: string, config?: AxiosRequestConfig): Promise<T>
}
