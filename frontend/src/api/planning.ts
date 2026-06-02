import http from '@/utils/http'

// ==================== Types ====================

/** 任务请求（适用于 Auto/ReAct/Plan-Execute 模式） */
export interface TaskRequest {
  goal?: string
  question?: string
  sessionId?: string
}

/** 步骤定义 */
export interface StepDef {
  description: string
  action?: string
  tool?: string
  params?: Record<string, unknown>
}

/** 预定义任务请求 */
export interface PredefinedTaskRequest {
  goal: string
  sessionId?: string
  steps: StepDef[]
}

/** 步骤执行结果 */
export interface StepResultInfo {
  success: boolean
  output: string | null
  error: string | null
  observation: string | null
}

/** 任务执行结果 */
export interface TaskResultInfo {
  taskId: string
  success: boolean
  output: string | null
  stepResults: StepResultInfo[]
  error: string | null
  executionTimeMs: number
  iterations: number
}

/** 执行历史项 */
export interface ExecutionHistoryItem {
  id: string
  timestamp: string
  strategy: 'auto' | 'react' | 'plan-execute' | 'predefined'
  goal: string
  result: TaskResultInfo
}

/** 执行策略类型 */
export type Strategy = 'auto' | 'react' | 'plan-execute' | 'predefined'

// ==================== API ====================

export const planningApi = {
  /** 自动选择策略执行任务 */
  execute: (request: TaskRequest) => http.post<TaskResultInfo>('/api/planning/execute', request),

  /** ReAct 模式执行（推理-行动循环） */
  executeReAct: (request: TaskRequest) => http.post<TaskResultInfo>('/api/planning/react', request),

  /** Plan-Execute 模式执行（先规划后执行） */
  executePlanExecute: (request: TaskRequest) =>
    http.post<TaskResultInfo>('/api/planning/plan-execute', request),

  /** 执行预定义任务 */
  executePredefinedTask: (request: PredefinedTaskRequest) =>
    http.post<TaskResultInfo>('/api/planning/task', request),
}
