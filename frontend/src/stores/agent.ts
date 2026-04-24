/**
 * Pinia Store - Agent 执行状态管理
 *
 * 职责：
 * 1. 管理 Agent 执行记录
 * 2. 处理 SSE 事件流
 * 3. 管理确认操作
 */

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
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

  // ==================== Actions ====================

  /** 加载可用 Agent 列表 */
  async function loadAgents() {
    isLoadingAgents.value = true
    try {
      availableAgents.value = await listAgents()
    } catch (e) {
      console.error('[AgentStore] 加载 Agent 列表失败:', e)
    } finally {
      isLoadingAgents.value = false
    }
  }

  /** 执行 Agent */
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

    try {
      const eventStream = executeAgent({ userInput })

      for await (const event of eventStream) {
        // 检查执行是否已被取消
        const current = executions.value.find(e => e.traceId === traceId)
        if (!current || current.status === 'cancelled') break

        handleEvent(traceId, event)
      }
    } catch (e) {
      updateExecution(traceId, {
        status: 'error',
        errorMessage: e instanceof Error ? e.message : String(e)
      })
    }
  }

  /** 处理 SSE 事件 */
  function handleEvent(traceId: string, event: AgentEvent) {
    const record = executions.value.find(e => e.traceId === traceId)
    if (!record) return

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
          // 如果没有对应的 tool_call，直接添加
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
        break
      }

      case 'agent_done': {
        record.status = 'completed'
        record.agentName = event.agentName || record.agentName
        record.output = event.output
        record.endTime = new Date(event.timestamp)
        record.tokenUsage = event.tokenUsage
        record.durationMs = event.durationMs
        break
      }

      case 'agent_error': {
        record.status = event.recoverable ? 'error' : 'error'
        record.errorMessage = event.message
        record.endTime = new Date(event.timestamp)
        break
      }

      case 'heartbeat': {
        // 心跳无需处理，仅保活
        break
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
    }

    executions.value = [...executions.value]
  }

  /** 取消执行 */
  async function cancelCurrentExecution(traceId?: string) {
    const targetTraceId = traceId || activeTraceId.value
    if (!targetTraceId) return

    try {
      await cancelExecution(targetTraceId)
    } catch (e) {
      console.error('[AgentStore] 取消执行失败:', e)
    }

    updateExecution(targetTraceId, { status: 'cancelled' })
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
  }

  return {
    // State
    executions,
    availableAgents,
    activeTraceId,
    isLoadingAgents,
    // Getters
    activeExecution,
    runningExecutions,
    historyExecutions,
    // Actions
    loadAgents,
    startExecution,
    approveConfirmation,
    cancelCurrentExecution,
    selectExecution,
    clearHistory
  }
})
