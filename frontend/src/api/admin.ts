import http from '@/utils/http'
import type { ApiResponse } from '@/types'

// 管理员 API

export interface PageResponse<T> {
  data: T[]
  total: number
  page: number
  size: number
}

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

/**
 * 提取 ApiResponse 中的 data 字段
 */
function extractData<T>(response: ApiResponse<T>): T {
  if (response.code !== 200) {
    throw new Error(response.message || '请求失败')
  }
  return response.data as T
}

export const adminApi = {
  // 仪表盘
  async getMetrics(): Promise<DashboardMetrics> {
    const response = await http.get<ApiResponse<DashboardMetrics>>('/api/admin/dashboard/metrics')
    return extractData(response)
  },

  async getTrend(params: { days: number }): Promise<TrendData> {
    const response = await http.get<ApiResponse<TrendData>>('/api/admin/dashboard/trend', { params })
    return extractData(response)
  },

  async getModelDistribution(): Promise<ModelDistribution[]> {
    const response = await http.get<ApiResponse<ModelDistribution[]>>('/api/admin/dashboard/model-distribution')
    return extractData(response)
  },

  // 用户管理
  async getUsers(params: {
    page: number
    size: number
    search?: string
    role?: string
    provider?: string
  }): Promise<PageResponse<UserAdminVO>> {
    const response = await http.get<ApiResponse<PageResponse<UserAdminVO>>>('/api/admin/users', { params })
    return extractData(response)
  },

  async getUserDetail(userId: string): Promise<UserAdminVO> {
    const response = await http.get<ApiResponse<UserAdminVO>>(`/api/admin/users/${userId}`)
    return extractData(response)
  },

  async getUserUsageStats(userId: string): Promise<{
    todayTokens: number
    todayCost: number
    monthTokens: number
    monthCost: number
  }> {
    const response = await http.get<ApiResponse<{
      todayTokens: number
      todayCost: number
      monthTokens: number
      monthCost: number
    }>>(`/api/admin/users/${userId}/usage`)
    return extractData(response)
  },

  async updateUserRole(userId: string, role: string): Promise<void> {
    const response = await http.put<ApiResponse<void>>(`/api/admin/users/${userId}/role`, { role })
    extractData(response)
  },

  async updateUserQuota(userId: string, quota: {
    dailyTokenLimit: number
    monthlyTokenLimit: number
  }): Promise<void> {
    const response = await http.put<ApiResponse<void>>(`/api/admin/users/${userId}/quota`, quota)
    extractData(response)
  },

  async deleteUser(userId: string): Promise<void> {
    const response = await http.delete<ApiResponse<void>>(`/api/admin/users/${userId}`)
    extractData(response)
  },

  // 成本监控
  async getBudget(): Promise<BudgetInfo> {
    const response = await http.get<ApiResponse<BudgetInfo>>('/api/admin/cost/budget')
    return extractData(response)
  },

  async getModelCostStatistics(): Promise<CostStatistics[]> {
    const response = await http.get<ApiResponse<CostStatistics[]>>('/api/admin/cost/model-statistics')
    return extractData(response)
  },

  async getTopUsers(params: { limit: number }): Promise<UserCostRanking[]> {
    const response = await http.get<ApiResponse<UserCostRanking[]>>('/api/admin/cost/top-users', { params })
    return extractData(response)
  },

  async getCostTrend(params: { days: number }): Promise<TrendData> {
    const response = await http.get<ApiResponse<TrendData>>('/api/admin/cost/trend', { params })
    return extractData(response)
  }
}

// ==================== 测试管理 API ====================

export interface TestJobStatus {
  jobId: string
  status: 'running' | 'completed' | 'failed' | 'cancelled' | 'not_found'
  startTime: number
  endTime: number
  message: string
  progress: number
}

export interface TestResultSummary {
  testName: string
  status: 'passed' | 'failed' | 'running' | 'pending'
  duration: number
  assertions: {
    passed: number
    failed: number
    total: number
  }
  error?: string
  timestamp: string
}

export interface PerformanceResultSummary {
  simulation: string
  requests: number
  successRate: number
  avgResponseTime: number
  maxResponseTime: number
  p95ResponseTime: number
  p99ResponseTime: number
  startTime: number
  endTime: number
}

export interface AIModelTestSummary {
  testCaseId: string
  testName: string
  category: string
  score: number
  passed: boolean
  details: Array<{
    assertion: string
    passed: boolean
    expected?: string
    actual?: string
  }>
  responseTime: number
  actualOutput: string
}

export interface TestStatsSummary {
  totalTests: number
  passed: number
  failed: number
  running: number
  passRate: number
  avgResponseTime: number
}

export const testApi = {
  // E2E 测试
  async runE2ETests(): Promise<{ jobId: string }> {
    const response = await http.post<ApiResponse<{ jobId: string }>>('/api/admin/test/e2e/run')
    return extractData(response)
  },

  async getE2EStatus(): Promise<TestResultSummary[]> {
    const response = await http.get<ApiResponse<TestResultSummary[]>>('/api/admin/test/e2e/status')
    return extractData(response)
  },

  async getE2EReport(): Promise<string> {
    const response = await http.get<ApiResponse<string>>('/api/admin/test/e2e/report')
    return extractData(response)
  },

  // 性能测试
  async runPerformanceTest(simulation: string): Promise<{ jobId: string }> {
    const response = await http.post<ApiResponse<{ jobId: string }>>('/api/admin/test/performance/run', { simulation })
    return extractData(response)
  },

  async getPerformanceResults(): Promise<PerformanceResultSummary[]> {
    const response = await http.get<ApiResponse<PerformanceResultSummary[]>>('/api/admin/test/performance/results')
    return extractData(response)
  },

  async getAvailableSimulations(): Promise<string[]> {
    const response = await http.get<ApiResponse<string[]>>('/api/admin/test/performance/simulations')
    return extractData(response)
  },

  // AI 模型测试
  async runAIModelTests(category?: string): Promise<{ jobId: string }> {
    const response = await http.post<ApiResponse<{ jobId: string }>>('/api/admin/test/ai/run', { category })
    return extractData(response)
  },

  async getAIModelResults(): Promise<AIModelTestSummary[]> {
    const response = await http.get<ApiResponse<AIModelTestSummary[]>>('/api/admin/test/ai/results')
    return extractData(response)
  },

  async getAICategories(): Promise<string[]> {
    const response = await http.get<ApiResponse<string[]>>('/api/admin/test/ai/categories')
    return extractData(response)
  },

  // 任务管理
  async getJobStatus(jobId: string): Promise<TestJobStatus> {
    const response = await http.get<ApiResponse<TestJobStatus>>(`/api/admin/test/job/${jobId}/status`)
    return extractData(response)
  },

  async cancelJob(jobId: string): Promise<void> {
    const response = await http.delete<ApiResponse<void>>(`/api/admin/test/job/${jobId}`)
    extractData(response)
  },

  // 测试统计
  async getStatsSummary(): Promise<TestStatsSummary> {
    const response = await http.get<ApiResponse<TestStatsSummary>>('/api/admin/test/stats/summary')
    return extractData(response)
  }
}

export default adminApi
