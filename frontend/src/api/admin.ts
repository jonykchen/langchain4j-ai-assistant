import http from '@/utils/http'

// 注意：http 拦截器已处理 ApiResponse 格式，直接返回 data
// 业务错误（code !== 200）会作为 Promise reject 抛出

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

export const adminApi = {
  // 仪表盘
  getMetrics: () => http.get<DashboardMetrics>('/api/admin/dashboard/metrics'),

  getTrend: (params: { days: number }) => http.get<TrendData>('/api/admin/dashboard/trend', { params }),

  getModelDistribution: () => http.get<ModelDistribution[]>('/api/admin/dashboard/model-distribution'),

  // 用户管理
  getUsers: (params: {
    page: number
    size: number
    search?: string
    role?: string
    provider?: string
  }) => http.get<PageResponse<UserAdminVO>>('/api/admin/users', { params }),

  getUserDetail: (userId: string) => http.get<UserAdminVO>(`/api/admin/users/${userId}`),

  getUserUsageStats: (userId: string) => http.get<{
    todayTokens: number
    todayCost: number
    monthTokens: number
    monthCost: number
  }>(`/api/admin/users/${userId}/usage`),

  updateUserRole: (userId: string, role: string) => http.put<void>(`/api/admin/users/${userId}/role`, { role }),

  updateUserQuota: (userId: string, quota: {
    dailyTokenLimit: number
    monthlyTokenLimit: number
  }) => http.put<void>(`/api/admin/users/${userId}/quota`, quota),

  deleteUser: (userId: string) => http.delete<void>(`/api/admin/users/${userId}`),

  // 成本监控
  getBudget: () => http.get<BudgetInfo>('/api/admin/cost/budget'),

  getModelCostStatistics: () => http.get<CostStatistics[]>('/api/admin/cost/model-statistics'),

  getTopUsers: (params: { limit: number }) => http.get<UserCostRanking[]>('/api/admin/cost/top-users', { params }),

  getCostTrend: (params: { days: number }) => http.get<TrendData>('/api/admin/cost/trend', { params })
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

// ==================== 新增：历史查询和导出 ====================

export interface TestJobHistory {
  id: string
  testType: 'E2E' | 'PERFORMANCE' | 'AI_MODEL'
  status: string
  startTime: string
  endTime: string
  message: string
  progress: number
  triggeredBy: string
  createdAt: string
}

export interface TestResultHistoryQuery {
  type?: string
  status?: string
  from?: string
  to?: string
  category?: string
  passed?: boolean
  page?: number
  size?: number
}

export interface TestComparisonResult {
  job1: { jobId: string; testType: string; startTime: string; status: string }
  job2: { jobId: string; testType: string; startTime: string; status: string }
  testType: string
  diffs: Array<{
    testName: string
    field: string
    value1: any
    value2: any
    changed: boolean
    changeDirection: string
  }>
  summary: { totalTests: number; improved: number; degraded: number; unchanged: number }
}

export interface E2ETestResultDetail {
  id: number
  jobId: string
  testName: string
  status: string
  durationMs: number
  assertionsPassed: number
  assertionsFailed: number
  errorMessage: string
  createdAt: string
}

export interface PerformanceTestResultDetail {
  id: number
  jobId: string
  simulation: string
  requests: number
  successRate: number
  avgResponseTime: number
  maxResponseTime: number
  p95ResponseTime: number
  p99ResponseTime: number
  startTime: string
  endTime: string
  createdAt: string
}

export interface AIModelTestResultDetail {
  id: number
  jobId: string
  testCaseId: string
  testName: string
  category: string
  score: number
  passed: boolean
  details: any[]
  responseTime: number
  actualOutput: string
  createdAt: string
}

export const testApi = {
  // E2E 测试
  runE2ETests: () => http.post<{ jobId: string }>('/api/admin/test/e2e/run'),

  getE2EStatus: () => http.get<TestResultSummary[]>('/api/admin/test/e2e/status'),

  getE2EReport: () => http.get<string>('/api/admin/test/e2e/report'),

  // 性能测试
  runPerformanceTest: (simulation: string) => http.post<{ jobId: string }>('/api/admin/test/performance/run', { simulation }),

  getPerformanceResults: () => http.get<PerformanceResultSummary[]>('/api/admin/test/performance/results'),

  getAvailableSimulations: () => http.get<string[]>('/api/admin/test/performance/simulations'),

  // AI 模型测试
  runAIModelTests: (category?: string) => http.post<{ jobId: string }>('/api/admin/test/ai/run', { category }),

  getAIModelResults: () => http.get<AIModelTestSummary[]>('/api/admin/test/ai/results'),

  getAICategories: () => http.get<string[]>('/api/admin/test/ai/categories'),

  // 任务管理
  getJobStatus: (jobId: string) => http.get<TestJobStatus>(`/api/admin/test/job/${jobId}/status`),

  cancelJob: (jobId: string) => http.delete<void>(`/api/admin/test/job/${jobId}`),

  // 测试统计
  getStatsSummary: () => http.get<TestStatsSummary>('/api/admin/test/stats/summary'),

  // ==================== 历史查询 ====================

  getTestJobHistory: (params: TestResultHistoryQuery) =>
    http.get<PageResponse<TestJobHistory>>('/api/admin/test/jobs', { params }),

  getTestJobDetail: (jobId: string) =>
    http.get<TestJobHistory>(`/api/admin/test/jobs/${jobId}`),

  getTestJobResults: (jobId: string) =>
    http.get<{
      e2e: E2ETestResultDetail[]
      performance: PerformanceTestResultDetail[]
      aiModel: AIModelTestResultDetail[]
    }>(`/api/admin/test/jobs/${jobId}/results`),

  getE2EHistory: (params: { jobId?: string; status?: string; page?: number; size?: number }) =>
    http.get<E2ETestResultDetail[]>('/api/admin/test/results/e2e', { params }),

  getPerformanceHistory: (params: { jobId?: string; page?: number; size?: number }) =>
    http.get<PerformanceTestResultDetail[]>('/api/admin/test/results/performance', { params }),

  getAIModelHistory: (params: { jobId?: string; category?: string; passed?: boolean; page?: number; size?: number }) =>
    http.get<AIModelTestResultDetail[]>('/api/admin/test/results/ai', { params }),

  // ==================== 导出 ====================

  exportTestResults: (params: { type: string; format: string; from?: string; to?: string }) =>
    http.get<Blob>('/api/admin/test/export', { params, responseType: 'blob' }),

  // ==================== 对比 ====================

  compareTestResults: (jobId1: string, jobId2: string) =>
    http.post<TestComparisonResult>('/api/admin/test/compare', { jobId1, jobId2 }),

  // ==================== 清理 ====================

  cleanupTestJobs: (retentionDays: number = 90) =>
    http.delete<{ deleted: number }>('/api/admin/test/cleanup', { params: { retentionDays } })
}

export default adminApi
