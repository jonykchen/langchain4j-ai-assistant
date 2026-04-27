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
    return !!token.value && (!isTokenExpired() || isRefreshing.value)
  })

  /** Token 是否正在刷新 */
  const isRefreshing = ref(false)

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
   * @returns 是否成功初始化（含刷新结果）
   */
  async function initAuth(): Promise<boolean> {
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

      // 如果 Token 快过期，尝试刷新并等待完成
      if (isTokenExpired() && storedRefreshToken) {
        return await refreshAccessToken()
      }

      // Token 未过期，直接返回已认证
      return true
    }

    // 无存储的 Token
    return false
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
   * 用户名密码登录
   */
  async function login(username: string, password: string): Promise<void> {
    const data = await authApi.login(username, password)
    saveAuth(data.token, data.user)
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

    // 跳转到授权页面
    window.location.href = await getAuthUrl(redirectUri, state)
  }

  /**
   * 处理 OAuth 回调
   */
  async function handleOAuthCallback(provider: 'github' | 'gitlab', code: string, state: string): Promise<void> {
    const handleCallback = provider === 'github' ? authApi.handleGitHubCallback : authApi.handleGitLabCallback
    const redirectUri = sessionStorage.getItem('oauth_redirect_uri') || undefined

    const data = await handleCallback(code, state, redirectUri)
    saveAuth(data.token, data.user)

    // 清理临时数据
    sessionStorage.removeItem('oauth_state')
    sessionStorage.removeItem('oauth_provider')
    sessionStorage.removeItem('oauth_redirect_uri')
  }

  /**
   * 刷新 Access Token
   */
  async function refreshAccessToken(): Promise<boolean> {
    if (!refreshToken.value) {
      clearAuth()
      return false
    }

    // 防止并发刷新
    if (isRefreshing.value) return true

    isRefreshing.value = true
    try {
      const data = await authApi.refreshToken({ refreshToken: refreshToken.value })

      token.value = data.accessToken
      tokenExpiry.value = Date.now() + data.expiresIn * 1000

      localStorage.setItem('access_token', data.accessToken)
      localStorage.setItem('token_expiry', String(tokenExpiry.value))

      if (data.refreshToken) {
        refreshToken.value = data.refreshToken
        localStorage.setItem('refresh_token', data.refreshToken)
      }

      return true
    } catch (error) {
      clearAuth()
      return false
    } finally {
      isRefreshing.value = false
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
    user.value = await authApi.getCurrentUser()
    localStorage.setItem('user_info', JSON.stringify(user.value))
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
    login,
    redirectToOAuth,
    handleOAuthCallback,
    refreshAccessToken,
    logout,
    clearAuth,
    fetchUser
  }
})
