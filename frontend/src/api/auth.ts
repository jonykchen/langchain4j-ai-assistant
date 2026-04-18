/**
 * 认证相关 API
 */
import http from '@/utils/http'
import type {
  ApiResponse,
  TokenResponse,
  UserInfo,
  OAuthCallbackResponse,
  RefreshTokenRequest
} from '@/types'

/**
 * 获取 GitHub 授权 URL
 */
export function getGitHubAuthUrl(redirectUri?: string, state?: string): Promise<ApiResponse<string>> {
  const params = new URLSearchParams()
  if (redirectUri) params.append('redirectUri', redirectUri)
  if (state) params.append('state', state)

  return http.get(`/auth/github/url?${params.toString()}`)
}

/**
 * 获取 GitLab 授权 URL
 */
export function getGitLabAuthUrl(redirectUri?: string, state?: string): Promise<ApiResponse<string>> {
  const params = new URLSearchParams()
  if (redirectUri) params.append('redirectUri', redirectUri)
  if (state) params.append('state', state)

  return http.get(`/auth/gitlab/url?${params.toString()}`)
}

/**
 * 处理 GitHub OAuth 回调
 */
export function handleGitHubCallback(code: string, state?: string, redirectUri?: string): Promise<ApiResponse<OAuthCallbackResponse>> {
  const params = new URLSearchParams()
  params.append('code', code)
  if (state) params.append('state', state)
  if (redirectUri) params.append('redirectUri', redirectUri)

  return http.get(`/auth/github/callback?${params.toString()}`)
}

/**
 * 处理 GitLab OAuth 回调
 */
export function handleGitLabCallback(code: string, state?: string, redirectUri?: string): Promise<ApiResponse<OAuthCallbackResponse>> {
  const params = new URLSearchParams()
  params.append('code', code)
  if (state) params.append('state', state)
  if (redirectUri) params.append('redirectUri', redirectUri)

  return http.get(`/auth/gitlab/callback?${params.toString()}`)
}

/**
 * 刷新 Token
 */
export function refreshToken(request: RefreshTokenRequest): Promise<ApiResponse<TokenResponse>> {
  return http.post('/auth/refresh', request)
}

/**
 * 登出
 */
export function logout(): Promise<ApiResponse<void>> {
  return http.post('/auth/logout')
}

/**
 * 获取当前用户信息
 */
export function getCurrentUser(): Promise<ApiResponse<UserInfo>> {
  return http.get('/auth/me')
}
