/**
 * Pinia Store - Agent 执行状态管理（生产级增强）
 *
 * <p>职责：
 * <ol>
 *   <li>管理 Agent 执行记录</li>
 *   <li>处理 SSE 事件流（带批量更新）</li>
 *   <li>管理确认操作</li>
 *   <li>状态持久化（sessionStorage，30分钟有效）</li>
 *   <li>前端可靠性指标上报</li>
 * </ol>
 *
 * <h2>生产级增强</h2>
 * <ul>
 *   <li>批量 UI 更新（50ms 合并事件）</li>
 *   <li>事件去重（基于 sequenceNumber）</li>
 *   <li>状态持久化（页面刷新后恢复）</li>
 *   <li>错误分级处理</li>
 *   <li>可靠性监控（重连/事件间隙/恢复失败）</li>
 * </ul>
 *
 * @author jonychen
 */

import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import type {
  AgentEvent,
  AgentMetadata,
  ExecutionRecord,
  ExecutionStep
} from '@/types/agent'
import {
  executeAgent,
  confirmOperation,
  cancelExecution,
  listAgents
} from '@/api/agent'
import { recordStateRestoreFailure } from '@/utils/frontendReliability'

/** 错误类型分级 */
export type AgentErrorType =
  | 'NETWORK_ERROR'      // 网络断开/超时，可自动重试
  | 'SERVER_ERROR'       // HTTP 5xx
  | 'SSE_PARSE_ERROR'    // 事件解析失败
  | 'CONFIRM_CONFLICT'   // 确认操作冲突
  | 'UNKNOWN_ERROR'     // 未知错误

/** 错误信息 */
export interface AgentError {
  message: string
  type: AgentErrorType
}

/** SSE 连接状态 */
export type ConnectionState = 'connected' | 'disconnected' | 'reconnecting'

function generateId(): string {
  return Date.now().toString(36) + Math.random().toString(36).substring(2)
}

export const useAgentStore = defineStore('agent', () => {
  // ==================== State ====================

  /** 所有执行记录 */
  const executions = ref<ExecutionRecord[]>([])

  /** 可用 Agent 列表 */
  const availableAgents = ref<AgentMetadata[]>([])

  /** 当前执行中的 traceId */
  const activeTraceId = ref<string | null>(null)

  /** 是否正在加载 Agent 列表 */
  const isLoadingAgents = ref(false)

  /** SSE 连接状态 */
  const connectionState = ref<ConnectionState>('disconnected')

  /** 当前重连次数 */
  const reconnectAttempt = ref(0)

  /** 错误信息 */
  const error = ref<AgentError | null>(null)

  // ==================== 持久化相关 ====================

  const STATE_KEY = 'agent_execution_state'
  const STATE_EXPIRE_MS = 30 * 60 * 1000 // 30分钟

  let persistTimer: ReturnType<typeof setTimeout> | null = null

  /**
   * 持久化状态到 sessionStorage
   *
   * <p>使用防抖策略，500ms 后写入，避免频繁写入。
   */
  function persistState() {
    if (!activeTraceId.value) return
    if (persistTimer) return

    persistTimer = setTimeout(() => {
      persistTimer = null
      const activeExec = executions.value.find(e => e.traceId === activeTraceId.value)
      if (!activeExec) return

      try {
        sessionStorage.setItem(
          STATE_KEY,
          JSON.stringify({
            traceId: activeTraceId.value,
            execution: activeExec,
            timestamp: Date.now()
          })
        )
        console.debug('[AgentStore] 状态已持久化, traceId:', activeTraceId.value)
      } catch (e) {
        console.warn('[AgentStore] 状态持久化失败:', e)
      }
    }, 500)
  }

  /**
   * 从 sessionStorage 恢复状态
   *
   * <p>仅恢复 30 分钟内的执行状态，超时自动清理。
   *
   * @returns 是否成功恢复
   */
  function restoreState(): boolean {
    const saved = sessionStorage.getItem(STATE_KEY)
    if (!saved) return false

    try {
      const data = JSON.parse(saved)

      // 检查是否超时
      if (Date.now() - data.timestamp > STATE_EXPIRE_MS) {
        console.info('[AgentStore] 持久化状态已过期，清理')
        sessionStorage.removeItem(STATE_KEY)
        // 记录状态恢复失败（过期）
        if (data.traceId) {
          recordStateRestoreFailure(data.traceId, 'expired')
        }
        return false
      }

      // 恢复状态
      activeTraceId.value = data.traceId

      // 修复日期类型
      const exec = data.execution
      if (exec) {
        exec.startTime = new Date(exec.startTime)
        if (exec.endTime) exec.endTime = new Date(exec.endTime)
        if (exec.steps) {
          exec.steps = exec.steps.map((s: ExecutionStep) => ({
            ...s,
            startTime: new Date(s.startTime),
            endTime: s.endTime ? new Date(s.endTime) : undefined
          }))
        }
        executions.value = [exec]
        console.info('[AgentStore] 状态恢复成功, traceId:', data.traceId)
        return true
      }

      return false
    } catch (e) {
      console.warn('[AgentStore] 状态恢复失败:', e)
      sessionStorage.removeItem(STATE_KEY)
      // 记录状态恢复失败（解析错误）
      recordStateRestoreFailure('unknown', 'parse_error')
      return false
    }
  }

  /**
   * 清除持久化状态
   */
  function clearPersistedState() {
    sessionStorage.removeItem(STATE_KEY)
    console.debug('[AgentStore] 持久化状态已清除')
  }

  // 关键状态变更时自动持久化
  watch(
    [executions, activeTraceId],
    () => {
      if (activeTraceId.value) persistState()
    },
    { deep: true }
  )

  // ==================== Getters ====================

  /** 当前活跃执行 */
  const activeExecution = computed(() =>
    executions.value.find(e => e.traceId === activeTraceId.value)
  )

  /** 执行中的记录 */
  const runningExecutions = computed(() =>
    executions.value.filter(e => e.status === 'running' || e.status === 'waiting_confirmation')
  )

  /** 历史记录（非运行中） */
  const historyExecutions = computed(() =>
    executions.value.filter(e => e.status !== 'running' && e.status !== 'waiting_confirmation')
  )

  /** 是否正在执行 */
  const isExecuting = computed(() =>
    activeExecution.value?.status === 'running' || activeExecution.value?.status === 'waiting_confirmation'
  )

  // ==================== Actions ====================

  /** 加载可用 Agent 列表 */
  async function loadAgents() {
    if (isLoadingAgents.value) return

    isLoadingAgents.value = true
    try {
      availableAgents.value = await listAgents()
      console.info('[AgentStore] Agent 列表加载完成, 数量:', availableAgents.value.length)
    } catch (e) {
      console.error('[AgentStore] 加载 Agent 列表失败:', e)
    } finally {
      isLoadingAgents.value = false
    }
  }

  /**
   * 执行 Agent
   *
   * <p>生产级特性：
   * <ul>
   *   <li>SSE 断线自动重连（指数退避）</li>
   *   <li>事件序号去重</li>
   *   <li>批量 UI 更新（50ms 合并）</li>
   *   <li>状态持久化</li>
   * </ul>
   */
  async function startExecution(userInput: string) {
    const traceId = generateId()

    // 创建执行记录
    const record: ExecutionRecord = {
      traceId,
      status: 'running',
      agentName: '',
      userInput,
      steps: [],
      startTime: new Date()
    }
    executions.value.unshift(record)
    activeTraceId.value = traceId
    connectionState.value = 'connected'
    reconnectAttempt.value = 0
    error.value = null

    console.info('[AgentStore] 开始执行, traceId:', traceId, 'input:', userInput)

    // 清理事件缓存
    processedSequences.clear()
    updateBuffer = []
    if (updateTimer) {
      clearTimeout(updateTimer)
      updateTimer = null
    }

    try {
      const eventStream = executeAgent({ userInput }, {
        onReconnect: (attempt) => {
          connectionState.value = 'reconnecting'
          reconnectAttempt.value = attempt
          console.warn('[AgentStore] SSE 重连中, 第', attempt, '次')
        },
        onConnected: () => {
          connectionState.value = 'connected'
          reconnectAttempt.value = 0
          console.info('[AgentStore] SSE 连接成功')
        }
      })

      for await (const event of eventStream) {
        // 检查执行是否已被取消
        const current = executions.value.find(e => e.traceId === traceId)
        if (!current || current.status === 'cancelled') {
          console.info('[AgentStore] 执行已取消, traceId:', traceId)
          break
        }

        handleEvent(traceId, event)
      }

      // 执行完成
      connectionState.value = 'disconnected'

    } catch (e) {
      const errMsg = e instanceof Error ? e.message : String(e)

      // 区分错误类型
      let errType: AgentErrorType = 'UNKNOWN_ERROR'
      if (errMsg.includes('fetch') || errMsg.includes('network') || errMsg.includes('Failed to fetch')) {
        errType = 'NETWORK_ERROR'
      } else if (errMsg.includes('HTTP error') && errMsg.includes('5')) {
        errType = 'SERVER_ERROR'
      }

      error.value = { message: errMsg, type: errType }

      updateExecution(traceId, {
        status: 'error',
        errorMessage: errMsg
      })

      console.error('[AgentStore] 执行失败:', errMsg)
    }
  }

  // ==================== 事件处理（带批量更新） ====================

  let updateBuffer: AgentEvent[] = []
  let updateTimer: ReturnType<typeof setTimeout> | null = null
  const processedSequences = new Set<number>()

  /**
   * 处理 SSE 事件
   *
   * <p>事件先进入缓冲区，50ms 后批量应用，减少渲染次数。
   */
  function handleEvent(traceId: string, event: AgentEvent) {
    // 去重：同一 sequenceNumber 的事件只处理一次
    if ('sequenceNumber' in event && event.sequenceNumber !== undefined) {
      if (processedSequences.has(event.sequenceNumber)) return
      processedSequences.add(event.sequenceNumber)
    }

    updateBuffer.push(event)

    if (!updateTimer) {
      updateTimer = setTimeout(() => {
        applyBatchUpdates(traceId, updateBuffer)
        updateBuffer = []
        updateTimer = null
      }, 50) // 50ms 批量合并
    }
  }

  /**
   * 批量应用事件更新
   */
  function applyBatchUpdates(traceId: string, batch: AgentEvent[]) {
    const record = executions.value.find(e => e.traceId === traceId)
    if (!record) return

    for (const event of batch) {
      switch (event.eventType) {
        case 'step_start': {
          record.agentName = event.agentName || record.agentName
          const step: ExecutionStep = {
            id: generateId(),
            index: event.stepIndex,
            type: event.type,
            status: 'running',
            startTime: new Date(event.timestamp)
          }
          addOrUpdateStep(record, step)
          break
        }

        case 'thought': {
          const step: ExecutionStep = {
            id: generateId(),
            index: event.stepIndex,
            type: 'THOUGHT',
            status: 'running',
            startTime: new Date(event.timestamp),
            content: event.content
          }
          addOrUpdateStep(record, step)
          break
        }

        case 'tool_call': {
          const step: ExecutionStep = {
            id: generateId(),
            index: event.stepIndex,
            type: 'TOOL_CALL',
            status: 'running',
            startTime: new Date(event.timestamp),
            toolName: event.toolName,
            toolParams: event.params
          }
          addOrUpdateStep(record, step)
          break
        }

        case 'tool_result': {
          const step = record.steps.find(s => s.index === event.stepIndex && s.type === 'TOOL_CALL')
          if (step) {
            step.status = event.success ? 'success' : 'error'
            step.endTime = new Date(event.timestamp)
            step.toolResult = event.result
            step.error = event.error || undefined
          } else {
            const newStep: ExecutionStep = {
              id: generateId(),
              index: event.stepIndex,
              type: 'TOOL_RESULT',
              status: event.success ? 'success' : 'error',
              startTime: new Date(event.timestamp),
              endTime: new Date(event.timestamp),
              toolName: event.toolName,
              toolResult: event.result,
              error: event.error || undefined
            }
            record.steps.push(newStep)
          }
          break
        }

        case 'step_end': {
          const step = record.steps.find(s => s.index === event.stepIndex)
          if (step) {
            step.status = event.success ? 'success' : 'error'
            step.endTime = new Date(event.timestamp)
            step.content = event.summary || step.content
          }
          break
        }

        case 'agent_call': {
          const step: ExecutionStep = {
            id: generateId(),
            index: event.stepIndex,
            type: 'AGENT_CALL',
            status: 'running',
            startTime: new Date(event.timestamp),
            content: `委托 ${event.targetAgent}: ${event.input}`
          }
          record.steps.push(step)
          break
        }

        case 'agent_result': {
          const step: ExecutionStep = {
            id: generateId(),
            index: event.stepIndex,
            type: 'TOOL_RESULT',
            status: event.success ? 'success' : 'error',
            startTime: new Date(event.timestamp),
            endTime: new Date(event.timestamp),
            content: event.output,
            toolName: event.agentName
          }
          record.steps.push(step)
          break
        }

        case 'confirmation_required': {
          record.status = 'waiting_confirmation'
          record.pendingConfirmation = {
            confirmationId: event.confirmationId,
            operation: event.operation,
            description: event.description,
            riskLevel: event.riskLevel
          }
          console.warn('[AgentStore] 需要确认操作:', event.operation, '风险:', event.riskLevel)
          break
        }

        case 'agent_done': {
          record.status = 'completed'
          record.agentName = event.agentName || record.agentName
          record.output = event.output
          record.endTime = new Date(event.timestamp)
          record.tokenUsage = event.tokenUsage
          record.durationMs = event.durationMs
          clearPersistedState()
          console.info('[AgentStore] 执行完成, 耗时:', event.durationMs, 'ms')
          break
        }

        case 'agent_error': {
          record.status = 'error'
          record.errorMessage = event.message
          record.endTime = new Date(event.timestamp)
          console.error('[AgentStore] Agent 错误:', event.message)
          break
        }

        case 'heartbeat': {
          // 心跳无需处理，仅保活
          break
        }
      }
    }

    // 触发响应式更新
    executions.value = [...executions.value]
  }

  /** 添加或更新步骤（同 index 同 type 更新） */
  function addOrUpdateStep(record: ExecutionRecord, step: ExecutionStep) {
    const existing = record.steps.findIndex(
      s => s.index === step.index && s.type === step.type
    )
    if (existing >= 0) {
      record.steps[existing] = { ...record.steps[existing], ...step }
    } else {
      record.steps.push(step)
    }
  }

  /** 确认操作 */
  async function approveConfirmation(traceId: string, approved: boolean) {
    const record = executions.value.find(e => e.traceId === traceId)
    if (!record?.pendingConfirmation) return

    console.info('[AgentStore] 确认操作, approved:', approved)

    try {
      const result = await confirmOperation({
        traceId,
        confirmationId: record.pendingConfirmation.confirmationId,
        approved,
        userId: '' // 后端会校验，这里暂传空
      })

      if (result.success) {
        record.pendingConfirmation = undefined
        record.status = 'running'
      }
    } catch (e) {
      console.error('[AgentStore] 确认操作失败:', e)
      error.value = {
        message: e instanceof Error ? e.message : String(e),
        type: 'CONFIRM_CONFLICT'
      }
    }

    executions.value = [...executions.value]
  }

  /** 取消执行 */
  async function cancelCurrentExecution(traceId?: string) {
    const targetTraceId = traceId || activeTraceId.value
    if (!targetTraceId) return

    console.info('[AgentStore] 取消执行, traceId:', targetTraceId)

    try {
      await cancelExecution(targetTraceId)
    } catch (e) {
      console.error('[AgentStore] 取消执行失败:', e)
    }

    updateExecution(targetTraceId, { status: 'cancelled' })
    clearPersistedState()
  }

  /** 更新执行记录 */
  function updateExecution(traceId: string, updates: Partial<ExecutionRecord>) {
    const record = executions.value.find(e => e.traceId === traceId)
    if (!record) return

    Object.assign(record, updates)
    executions.value = [...executions.value]
  }

  /** 切换活跃执行 */
  function selectExecution(traceId: string) {
    activeTraceId.value = traceId
  }

  /** 清除历史记录 */
  function clearHistory() {
    executions.value = executions.value.filter(
      e => e.status === 'running' || e.status === 'waiting_confirmation'
    )
    if (activeTraceId.value && !executions.value.find(e => e.traceId === activeTraceId.value)) {
      activeTraceId.value = null
    }
    clearPersistedState()
  }

  return {
    // State
    executions,
    availableAgents,
    activeTraceId,
    isLoadingAgents,
    connectionState,
    reconnectAttempt,
    error,
    // Getters
    activeExecution,
    runningExecutions,
    historyExecutions,
    isExecuting,
    // Actions
    loadAgents,
    startExecution,
    approveConfirmation,
    cancelCurrentExecution,
    selectExecution,
    clearHistory,
    restoreState,
    clearPersistedState
  }
})
