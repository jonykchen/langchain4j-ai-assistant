import http from '@/utils/http'

// ==================== Types ====================

export interface AgentTrace {
  id: number
  traceId: string
  sessionId: string
  userId: string
  agentType: string
  goal: string
  status: string
  startTime: string
  endTime: string
  executionTimeMs: number
  iterations: number
  finalOutput: string
  errorMessage: string
  tokenUsage: TokenUsage
  spans: SpanData[]
  metadata: Record<string, unknown>
  createdAt: string
}

export interface TokenUsage {
  promptTokens: number
  completionTokens: number
  totalTokens: number
}

export interface SpanData {
  spanId: string
  type: string
  name: string
  input: string
  output: string
  startTime: string
  endTime: string
  durationMs: number
  success: boolean
  error: string
  promptTokens: number
  completionTokens: number
}

export interface AgentSpan {
  id: number
  traceId: string
  spanId: string
  parentSpanId: string
  type: string
  name: string
  input: string
  output: string
  startTime: string
  endTime: string
  durationMs: number
  success: boolean
  error: string
  promptTokens: number
  completionTokens: number
  attributes: Record<string, unknown>
}

export interface TraceStatistics {
  total: number
  completed: number
  failed: number
  active: number
  successRate: number
}

export interface PromptTemplate {
  id: number
  name: string
  version: string
  description: string
  content: string
  variables: string
  tags: string
  active: boolean
  production: boolean
  createdBy: string
  createdAt: string
  updatedAt: string
  abTestEnabled: boolean
  abTestVariantName: string
  abTestTrafficPercentage: number
  abTestBaselineVersion: string
  totalUses: number
  successCount: number
  failureCount: number
  avgResponseTime: number
  avgTokenUsage: number
}

export interface EvaluationResult {
  traceId: string
  overallScore: number
  recommendation: string
  evaluatorResults: EvaluatorResult[]
  metrics: EvaluationMetrics
}

export interface EvaluatorResult {
  evaluatorId: string
  passed: boolean
  score: number
  details: Record<string, unknown>
  issues: string[]
  recommendation: string
}

export interface EvaluationMetrics {
  taskCompletionRate: number
  avgIterations: number
  toolSuccessRate: number
  avgResponseTimeMs: number
  qualityScore: number
  errorRate: number
}

export interface EvaluationReport {
  reportId: string
  generatedAt: string
  totalTraces: number
  avgOverallScore: number
  passRate: number
  results: EvaluationResult[]
}

export interface SnapshotStatistics {
  total: number
  resumable: number
  active: number
  expired: number
}

// ==================== API ====================

export const observabilityApi = {
  // Agent 追踪
  getTraces: (params: { userId?: string; status?: string; agentType?: string; limit?: number }) =>
    http.get<AgentTrace[]>('/api/admin/observability/traces', { params }),

  getTraceDetail: (traceId: string) =>
    http.get<AgentTrace>(`/api/admin/observability/traces/${traceId}`),

  getTraceSpans: (traceId: string) =>
    http.get<AgentSpan[]>(`/api/admin/observability/traces/${traceId}/spans`),

  getActiveTraces: (userId?: string) =>
    http.get<AgentTrace[]>('/api/admin/observability/traces/active', { params: { userId } }),

  getTraceStatistics: () =>
    http.get<TraceStatistics>('/api/admin/observability/traces/statistics'),

  // Prompt 管理
  getPrompts: () =>
    http.get<PromptTemplate[]>('/api/admin/observability/prompts'),

  getPromptNames: () =>
    http.get<string[]>('/api/admin/observability/prompts/names'),

  getPromptVersions: (name: string) =>
    http.get<PromptTemplate[]>(`/api/admin/observability/prompts/${encodeURIComponent(name)}/versions`),

  createPrompt: (data: { name: string; version?: string; description?: string; content: string; tags?: string; createdBy?: string }) =>
    http.post<PromptTemplate>('/api/admin/observability/prompts', data),

  createVersion: (name: string, data: { content: string; description?: string }) =>
    http.post<PromptTemplate>(`/api/admin/observability/prompts/${encodeURIComponent(name)}/versions`, data),

  activateVersion: (name: string, version: string) =>
    http.post(`/api/admin/observability/prompts/${encodeURIComponent(name)}/versions/${encodeURIComponent(version)}/activate`),

  promoteToProduction: (name: string, version: string) =>
    http.post(`/api/admin/observability/prompts/${encodeURIComponent(name)}/versions/${encodeURIComponent(version)}/promote`),

  rollbackVersion: (name: string, version: string) =>
    http.post(`/api/admin/observability/prompts/${encodeURIComponent(name)}/rollback/${encodeURIComponent(version)}`),

  configureABTest: (name: string, config: { baselineVersion: string; variantVersion: string; variantName: string; trafficPercentage: number }) =>
    http.post(`/api/admin/observability/prompts/${encodeURIComponent(name)}/ab-test`, config),

  stopABTest: (name: string) =>
    http.post(`/api/admin/observability/prompts/${encodeURIComponent(name)}/ab-test/stop`),

  // 评测
  evaluateTrace: (traceId: string) =>
    http.post<EvaluationResult>(`/api/admin/observability/evaluation/evaluate/${traceId}`),

  evaluateBatch: (traceIds: string[]) =>
    http.post<EvaluationResult[]>('/api/admin/observability/evaluation/batch', traceIds),

  generateReport: (traceIds: string[]) =>
    http.post<EvaluationReport>('/api/admin/observability/evaluation/report', traceIds),

  getEvaluators: () =>
    http.get<{ name: string; description: string }[]>('/api/admin/observability/evaluation/evaluators'),

  // 状态快照
  getSessionSnapshots: (sessionId: string) =>
    http.get<unknown[]>(`/api/admin/observability/snapshots/session/${sessionId}`),

  getResumableSnapshot: (sessionId: string) =>
    http.get<unknown | null>(`/api/admin/observability/snapshots/session/${sessionId}/resumable`),

  getSnapshotStatistics: () =>
    http.get<SnapshotStatistics>('/api/admin/observability/snapshots/statistics'),

  cleanupExpiredSnapshots: () =>
    http.post<number>('/api/admin/observability/snapshots/cleanup')
}
