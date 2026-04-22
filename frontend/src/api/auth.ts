/**
 * 认证相关 API
 */
import http from '@/utils/http'
import type { TokenResponse, UserInfo, OAuthCallbackResponse, RefreshTokenRequest } from '@/types'

// 注意：http 拦截器已处理 ApiResponse 格式，直接返回 data
// 业务错误（code !== 200）会作为 Promise reject 抛出

/**
 * 用户名密码登录
 */
export function login(username: string, password: string): Promise<OAuthCallbackResponse> {
  return http.post('/auth/login', { username, password })
}

/**
 * 获取 GitHub 授权 URL
 */
export function getGitHubAuthUrl(redirectUri?: string, state?: string): Promise<string> {
  const params = new URLSearchParams()
  if (redirectUri) params.append('redirectUri', redirectUri)
  if (state) params.append('state', state)

  return http.get(`/auth/github/url?${params.toString()}`)
}

/**
 * 获取 GitLab 授权 URL
 */
export function getGitLabAuthUrl(redirectUri?: string, state?: string): Promise<string> {
  const params = new URLSearchParams()
  if (redirectUri) params.append('redirectUri', redirectUri)
  if (state) params.append('state', state)

  return http.get(`/auth/gitlab/url?${params.toString()}`)
}

/**
 * 处理 GitHub OAuth 回调
 */
export function handleGitHubCallback(code: string, state?: string, redirectUri?: string): Promise<OAuthCallbackResponse> {
  const params = new URLSearchParams()
  params.append('code', code)
  if (state) params.append('state', state)
  if (redirectUri) params.append('redirectUri', redirectUri)

  return http.get(`/auth/github/callback?${params.toString()}`)
}

/**
 * 处理 GitLab OAuth 回调
 */
export function handleGitLabCallback(code: string, state?: string, redirectUri?: string): Promise<OAuthCallbackResponse> {
  const params = new URLSearchParams()
  params.append('code', code)
  if (state) params.append('state', state)
  if (redirectUri) params.append('redirectUri', redirectUri)

  return http.get(`/auth/gitlab/callback?${params.toString()}`)
}

/**
 * 刷新 Token
 */
export function refreshToken(request: RefreshTokenRequest): Promise<TokenResponse> {
  return http.post('/auth/refresh', request)
}

/**
 * 登出
 */
export function logout(): Promise<void> {
  return http.post('/auth/logout')
}

/**
 * 获取当前用户信息
 */
export function getCurrentUser(): Promise<UserInfo> {
  return http.get('/auth/me')
}
