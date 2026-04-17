import http from '@/utils/http'
import type { PageResponse } from '@/types'

// 管理员 API

export interface DashboardMetrics {
  totalUsers: number
  activeUsers: number
  todayTokens: number
  todayCost: number
  todayRequests: number
  modelHealth: ModelHealthInfo[]
  budget: BudgetInfo
}

export interface ModelHealthInfo {
  name: string
  status: string
  circuitBreaker: string
  avgLatency: number
  successRate: number
}

export interface BudgetInfo {
  dailyUsed: number
  dailyTotal: number
  dailyPercent: number
  monthlyUsed: number
  monthlyTotal: number
  monthlyPercent: number
}

export interface TrendData {
  dates: string[]
  tokens: number[]
  costs: number[]
  requests: number[]
}

export interface ModelDistribution {
  name: string
  value: number
}

export interface UserAdminVO {
  id: string
  username: string
  email: string
  nickname: string
  avatar: string
  role: string
  provider: string
  createdAt: string
  lastLoginAt: string
  todayTokens: number
  todayCost: number
  dailyTokenLimit: number
  monthlyTokenLimit: number
}

export interface CostStatistics {
  modelName: string
  totalTokens: number
  promptTokens: number
  completionTokens: number
  totalCost: number
  requestCount: number
  avgTokensPerRequest: number
  costPercent: number
}

export interface UserCostRanking {
  userId: string
  username: string
  tokens: number
  cost: number
}

export const adminApi = {
  // 仪表盘
  getMetrics(): Promise<DashboardMetrics> {
    return http.get('/admin/dashboard/metrics')
  },

  getTrend(params: { days: number }): Promise<TrendData> {
    return http.get('/admin/dashboard/trend', { params })
  },

  getModelDistribution(): Promise<ModelDistribution[]> {
    return http.get('/admin/dashboard/model-distribution')
  },

  // 用户管理
  getUsers(params: {
    page: number
    size: number
    search?: string
    role?: string
    provider?: string
  }): Promise<PageResponse<UserAdminVO>> {
    return http.get('/admin/users', { params })
  },

  getUserDetail(userId: string): Promise<UserAdminVO> {
    return http.get(`/admin/users/${userId}`)
  },

  getUserUsageStats(userId: string): Promise<{
    todayTokens: number
    todayCost: number
    monthTokens: number
    monthCost: number
  }> {
    return http.get(`/admin/users/${userId}/usage`)
  },

  updateUserRole(userId: string, role: string): Promise<void> {
    return http.put(`/admin/users/${userId}/role`, { role })
  },

  updateUserQuota(userId: string, quota: {
    dailyTokenLimit: number
    monthlyTokenLimit: number
  }): Promise<void> {
    return http.put(`/admin/users/${userId}/quota`, quota)
  },

  deleteUser(userId: string): Promise<void> {
    return http.delete(`/admin/users/${userId}`)
  },

  // 成本监控
  getBudget(): Promise<BudgetInfo> {
    return http.get('/admin/cost/budget')
  },

  getModelCostStatistics(): Promise<CostStatistics[]> {
    return http.get('/admin/cost/model-statistics')
  },

  getTopUsers(params: { limit: number }): Promise<UserCostRanking[]> {
    return http.get('/admin/cost/top-users', { params })
  },

  getCostTrend(params: { days: number }): Promise<TrendData> {
    return http.get('/admin/cost/trend', { params })
  }
}

export default adminApi
