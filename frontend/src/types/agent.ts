/**
 * Agent 类型定义
 *
 * 与后端 AgentEvent sealed interface 对应
 */

/** 风险等级 */
export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

/** 步骤类型 */
export type StepType = 'THOUGHT' | 'TOOL_CALL' | 'TOOL_RESULT' | 'LLM_CALL' | 'AGENT_CALL'

/** Token 使用量 */
export interface TokenUsage {
  promptTokens: number
  completionTokens: number
  totalTokens: number
}

/** 基础事件字段 */
interface BaseAgentEvent {
  traceId: string
  sequenceNumber: number
  timestamp: string
}

/** 步骤开始事件 */
export interface StepStartEvent extends BaseAgentEvent {
  eventType: 'step_start'
  stepIndex: number
  type: StepType
  agentName: string
}

/** 步骤结束事件 */
export interface StepEndEvent extends BaseAgentEvent {
  eventType: 'step_end'
  stepIndex: number
  success: boolean
  summary: string
  durationMs: number
}

/** 思考过程事件 */
export interface ThoughtEvent extends BaseAgentEvent {
  eventType: 'thought'
  stepIndex: number
  content: string
}

/** 工具调用事件 */
export interface ToolCallEvent extends BaseAgentEvent {
  eventType: 'tool_call'
  stepIndex: number
  toolName: string
  params: Record<string, unknown>
}

/** 工具结果事件 */
export interface ToolResultEvent extends BaseAgentEvent {
  eventType: 'tool_result'
  stepIndex: number
  toolName: string
  result: unknown
  success: boolean
  error: string | null
  executionTimeMs: number
}

/** Agent 委托事件 */
export interface AgentCallEvent extends BaseAgentEvent {
  eventType: 'agent_call'
  stepIndex: number
  targetAgent: string
  input: string
}

/** Agent 返回结果事件 */
export interface AgentResultEvent extends BaseAgentEvent {
  eventType: 'agent_result'
  stepIndex: number
  agentName: string
  output: string
  success: boolean
}

/** 需要用户确认事件 */
export interface ConfirmationRequiredEvent extends BaseAgentEvent {
  eventType: 'confirmation_required'
  stepIndex: number
  confirmationId: string
  operation: string
  description: string
  riskLevel: RiskLevel
  params: Record<string, unknown>
}

/** 执行完成事件 */
export interface AgentDoneEvent extends BaseAgentEvent {
  eventType: 'agent_done'
  agentName: string
  output: string
  totalSteps: number
  tokenUsage: TokenUsage
  durationMs: number
}

/** 执行错误事件 */
export interface AgentErrorEvent extends BaseAgentEvent {
  eventType: 'agent_error'
  errorCode: string
  message: string
  details: string | null
  recoverable: boolean
}

/** 心跳事件 */
export interface HeartbeatEvent extends BaseAgentEvent {
  eventType: 'heartbeat'
}

/** Agent 事件联合类型 */
export type AgentEvent =
  | StepStartEvent
  | StepEndEvent
  | ThoughtEvent
  | ToolCallEvent
  | ToolResultEvent
  | AgentCallEvent
  | AgentResultEvent
  | ConfirmationRequiredEvent
  | AgentDoneEvent
  | AgentErrorEvent
  | HeartbeatEvent

/** Agent 元信息 */
export interface AgentMetadata {
  name: string
  agentType: string
  displayName: string
  description: string
  version: string
  capabilities: string[]
  requiredPermissions: string[]
  maxIterations: number
  timeout: string
  supportsStreaming: boolean
}

/** 执行请求 */
export interface ExecuteRequest {
  sessionId?: string
  userInput: string
  params?: Record<string, unknown>
  options?: AgentRequestOptions
}

/** 执行选项 */
export interface AgentRequestOptions {
  maxIterations?: number
  timeout?: number
  requireConfirmation?: boolean
  debugMode?: boolean
}

/** 确认请求 */
export interface ConfirmRequest {
  traceId: string
  confirmationId: string
  approved: boolean
}

/** 执行步骤（用于展示） */
export interface ExecutionStep {
  id: string
  index: number
  type: StepType
  status: 'running' | 'success' | 'error'
  startTime: Date
  endTime?: Date
  content?: string
  toolName?: string
  toolParams?: Record<string, unknown>
  toolResult?: unknown
  error?: string
}

/** 执行状态 */
export type ExecutionStatus = 'idle' | 'running' | 'waiting_confirmation' | 'completed' | 'error' | 'cancelled'

/** 执行记录 */
export interface ExecutionRecord {
  traceId: string
  status: ExecutionStatus
  agentName: string
  userInput: string
  steps: ExecutionStep[]
  output?: string
  startTime: Date
  endTime?: Date
  tokenUsage?: TokenUsage
  durationMs?: number
  errorMessage?: string
  pendingConfirmation?: {
    confirmationId: string
    operation: string
    description: string
    riskLevel: RiskLevel
  }
}
