/**
 * 认证状态管理
 * 处理用户登录、登出、Token 管理
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserInfo, TokenResponse } from '@/types'
import * as authApi from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  // ========== State ==========

  /** 访问令牌 */
  const token = ref<string | null>(null)

  /** 刷新令牌 */
  const refreshToken = ref<string | null>(null)

  /** Token 过期时间（毫秒时间戳） */
  const tokenExpiry = ref<number | null>(null)

  /** 当前用户信息 */
  const user = ref<UserInfo | null>(null)

  // ========== Getters ==========

  /** 是否已认证 */
  const isAuthenticated = computed(() => {
    return !!token.value && !isTokenExpired()
  })

  /** 是否是管理员 */
  const isAdmin = computed(() => {
    return user.value?.role === 'ADMIN'
  })

  // ========== Actions ==========

  /**
   * 检查 Token 是否过期
   */
  function isTokenExpired(): boolean {
    if (!tokenExpiry.value) return true
    // 提前 5 分钟认为过期，留出刷新时间
    return Date.now() >= tokenExpiry.value - 5 * 60 * 1000
  }

  /**
   * 初始化认证状态（从 localStorage 恢复）
   */
  function initAuth(): void {
    const storedToken = localStorage.getItem('access_token')
    const storedRefreshToken = localStorage.getItem('refresh_token')
    const storedExpiry = localStorage.getItem('token_expiry')
    const storedUser = localStorage.getItem('user_info')

    if (storedToken && storedExpiry) {
      token.value = storedToken
      refreshToken.value = storedRefreshToken
      tokenExpiry.value = parseInt(storedExpiry)

      if (storedUser) {
        try {
          user.value = JSON.parse(storedUser)
        } catch (e) {
          console.error('解析用户信息失败', e)
        }
      }

      // 如果 Token 快过期，尝试刷新
      if (isTokenExpired() && storedRefreshToken) {
        refreshAccessToken()
      }
    }
  }

  /**
   * 保存认证信息到 localStorage
   */
  function saveAuth(tokenResponse: TokenResponse, userInfo: UserInfo): void {
    token.value = tokenResponse.accessToken
    refreshToken.value = tokenResponse.refreshToken
    tokenExpiry.value = Date.now() + tokenResponse.expiresIn * 1000
    user.value = userInfo

    // 持久化存储
    localStorage.setItem('access_token', tokenResponse.accessToken)
    localStorage.setItem('refresh_token', tokenResponse.refreshToken)
    localStorage.setItem('token_expiry', String(tokenExpiry.value))
    localStorage.setItem('user_info', JSON.stringify(userInfo))
  }

  /**
   * 获取 OAuth 授权 URL 并跳转
   */
  async function redirectToOAuth(provider: 'github' | 'gitlab'): Promise<void> {
    // 生成 state 参数防止 CSRF
    const state = generateRandomState()
    sessionStorage.setItem('oauth_state', state)
    sessionStorage.setItem('oauth_provider', provider)

    // 获取授权 URL
    const redirectUri = `${window.location.origin}/auth/${provider}/callback`
    sessionStorage.setItem('oauth_redirect_uri', redirectUri)
    const getAuthUrl = provider === 'github' ? authApi.getGitHubAuthUrl : authApi.getGitLabAuthUrl

    const response = await getAuthUrl(redirectUri, state)

    // 跳转到授权页面
    window.location.href = response.data
  }

  /**
   * 处理 OAuth 回调
   */
  async function handleOAuthCallback(provider: 'github' | 'gitlab', code: string, state: string): Promise<void> {
    const handleCallback = provider === 'github' ? authApi.handleGitHubCallback : authApi.handleGitLabCallback
    const redirectUri = sessionStorage.getItem('oauth_redirect_uri') || undefined

    const response = await handleCallback(code, state, redirectUri)

    if (response.code === 200 && response.data) {
      saveAuth(response.data.token, response.data.user)

      // 清理临时数据
      sessionStorage.removeItem('oauth_state')
      sessionStorage.removeItem('oauth_provider')
      sessionStorage.removeItem('oauth_redirect_uri')
    } else {
      throw new Error(response.message || 'OAuth 认证失败')
    }
  }

  /**
   * 刷新 Access Token
   */
  async function refreshAccessToken(): Promise<boolean> {
    if (!refreshToken.value) {
      clearAuth()
      return false
    }

    try {
      const response = await authApi.refreshToken({ refreshToken: refreshToken.value })

      if (response.code === 200 && response.data) {
        token.value = response.data.accessToken
        tokenExpiry.value = Date.now() + response.data.expiresIn * 1000

        localStorage.setItem('access_token', response.data.accessToken)
        localStorage.setItem('token_expiry', String(tokenExpiry.value))

        if (response.data.refreshToken) {
          refreshToken.value = response.data.refreshToken
          localStorage.setItem('refresh_token', response.data.refreshToken)
        }

        return true
      }

      clearAuth()
      return false
    } catch (error) {
      clearAuth()
      return false
    }
  }

  /**
   * 登出
   */
  async function logout(): Promise<void> {
    try {
      await authApi.logout()
    } catch (e) {
      // 忽略登出接口错误
    } finally {
      clearAuth()
    }
  }

  /**
   * 清除认证状态
   */
  function clearAuth(): void {
    token.value = null
    refreshToken.value = null
    tokenExpiry.value = null
    user.value = null

    localStorage.removeItem('access_token')
    localStorage.removeItem('refresh_token')
    localStorage.removeItem('token_expiry')
    localStorage.removeItem('user_info')
  }

  /**
   * 获取当前用户信息
   */
  async function fetchUser(): Promise<void> {
    const response = await authApi.getCurrentUser()

    if (response.code === 200 && response.data) {
      user.value = response.data
      localStorage.setItem('user_info', JSON.stringify(response.data))
    }
  }

  // ========== 辅助函数 ==========

  /**
   * 生成随机 state
   */
  function generateRandomState(): string {
    return Math.random().toString(36).substring(2, 15) +
           Math.random().toString(36).substring(2, 15)
  }

  return {
    // State
    token,
    refreshToken,
    user,
    // Getters
    isAuthenticated,
    isAdmin,
    // Actions
    initAuth,
    saveAuth,
    redirectToOAuth,
    handleOAuthCallback,
    refreshAccessToken,
    logout,
    clearAuth,
    fetchUser
  }
})
