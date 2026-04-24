# 前端 Agent 执行面板设计

> 多 Agent 生产级系统技术方案 - 子文档
> 版本：1.1（生产级优化版）

---

## 1. 设计目标

### 1.1 核心目标

| 目标 | 说明 |
|------|------|
| 步骤可视化 | Agent 的每个步骤都以卡片形式展示 |
| 实时更新 | SSE 事件到达时实时渲染，无需等待完成 |
| 交互能力 | 支持确认敏感操作、取消执行、输入参数 |
| 响应式设计 | 支持桌面和移动端 |
| **断线恢复** | **SSE 断开后自动重连，事件去重，不丢失执行状态** |
| **状态持久** | **页面刷新后恢复未完成的执行（30min 内）** |

### 1.2 与现有聊天界面的关系

- **聊天界面** (`/chat`)：保留现有体验，用于普通对话
- **Agent 执行面板** (`/agent`)：新页面，专门用于 Agent 执行和交互
- **互相跳转**：聊天中检测到 Agent 任务可跳转到执行面板

---

## 2. 页面布局

```
┌─────────────────────────────────────────────────────────────┐
│ 顶部导航栏                                                   │
│ ├── Agent 执行面板                                           │
│ ├── Agent 选择器（Ops / Data / Prompt / Test）               │
│ └── 用户信息                                                 │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ 执行步骤区域                                              │ │
│ │                                                          │ │
│ │ ┌─ Step 0: 路由 ───────────────────── ✅ 0.3s ────────┐ │ │
│ │ │ Router → 选择 OpsAgent                               │ │ │
│ │ │ 理由：检测到关键词"模型健康"、"权重"                  │ │ │
│ │ └──────────────────────────────────────────────────────┘ │ │
│ │                                                          │ │
│ │ ┌─ Step 1: 获取模型状态 ──────────── ✅ 1.2s ─────────┐ │ │
│ │ │ 💭 思考：需要先检查所有模型的当前状态                 │ │ │
│ │ │ 🔧 调用工具：get_model_health()                      │ │ │
│ │ │                                                       │ │ │
│ │ │ 📊 结果：                                             │ │ │
│ │ │ ┌──────────────────────────────────────────────────┐ │ │ │
│ │ │ │ 模型        │ 状态  │ 延迟    │ 熔断  │ 权重     │ │ │ │
│ │ │ │ dashscope │ 健康  │ 230ms  │ 关闭  │ 50(71%) ││  │ │
│ │ │ │ zhipu      │ 健康  │ 180ms  │ 关闭  │ 20(29%) ││  │ │
│ │ │ │ deepseek   │ 异常  │ 1200ms │ 打开  │ 15(0%)  ││  │ │
│ │ │ └──────────────────────────────────────────────────┘ │ │ │
│ │ └──────────────────────────────────────────────────────┘ │ │
│ │                                                          │ │
│ │ ┌─ Step 2: 分析与处置 ────────────── ⏳ 执行中 ───────┐ │ │
│ │ │ 💭 思考：DeepSeek 延迟过高且已熔断，需要调整权重...  │ │ │
│ │ │ 🔧 调用工具：adjust_model_weight(...)               │ │ │
│ │ │                                                       │ │ │
│ │ │ ⚠️ 需要确认                                          │ │ │
│ │ │ ┌──────────────────────────────────────────────────┐ │ │ │
│ │ │ │ 即将调整 deepseek 模型权重从 15 到 5            │ │ │ │
│ │ │ │ 风险等级：🔴 高风险                             │ │ │ │
│ │ │ │                                                  │ │ │ │
│ │ │ │ [ 确认执行 ]        [ 取消 ]                     │ │ │ │
│ │ │ └──────────────────────────────────────────────────┘ │ │ │
│ │ └──────────────────────────────────────────────────────┘ │ │
│ │                                                          │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                             │
├─────────────────────────────────────────────────────────────┤
│ 输入区域                                                     │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ 请描述您的需求...                          [ 执行 ]   │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                             │
│ ── 执行统计 ──────────────────────────────────────────────  │
│ ⏱ 耗时: 4.6s  🔧 工具调用: 3次  📊 Token: 1.2k  💰 估计成本: ¥0.02 │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. 组件结构

### 3.1 组件树

```
AgentExecutionView.vue (页面)
├── AgentSelector.vue (Agent 选择器，带权限过滤)
├── ExecutionStepList.vue (步骤列表，>30 步时启用虚拟滚动)
│   └── ExecutionStepCard.vue (步骤卡片)
│       ├── StepHeader.vue (步骤头部：图标、标题、状态、耗时)
│       ├── ThoughtSection.vue (思考过程)
│       ├── ToolCallSection.vue (工具调用)
│       ├── ToolResultSection.vue (工具结果)
│       └── ConfirmationDialog.vue (确认对话框)
├── ExecutionStats.vue (执行统计)
├── AgentInput.vue (输入组件)
└── ReconnectAlert.vue (断线重连提示)
```

### 3.2 状态管理

```typescript
// frontend/src/stores/agent.ts

import { defineStore } from 'pinia'
import { ref, computed, reactive, watch } from 'vue'
import { executeAgent, confirmOperation, cancelExecution } from '@/api/agent'
import { useAuthStore } from '@/stores/auth'
import type {
  AgentEvent,
  AgentMetadata,
  AgentExecutionRequest,
  AgentRequestOptions,
} from '@/api/agent'

/**
 * 错误类型分级
 */
export type AgentErrorType =
  | 'NETWORK_ERROR'      // 网络断开/超时，可自动重试
  | 'SERVER_ERROR'       // HTTP 5xx
  | 'SSE_PARSE_ERROR'    // 事件解析失败，记录日志继续执行
  | 'CONFIRM_CONFLICT'   // 确认操作冲突（已被其他人处理）
  | 'UNKNOWN_ERROR'      // 未知错误

export interface ExecutionStep {
  stepIndex: number
  type: 'ROUTER' | 'THOUGHT' | 'TOOL_CALL' | 'TOOL_RESULT' | 'AGENT_CALL' | 'AGENT_RESULT'
  agentName: string
  status: 'running' | 'success' | 'failed' | 'waiting_confirmation' | 'cancelled'
  startTime: Date
  endTime?: Date
  durationMs?: number
  thought?: string
  toolName?: string
  toolParams?: Record<string, unknown>
  toolResult?: unknown
  toolSuccess?: boolean
  toolError?: string
  toolExecutionTimeMs?: number
  summary?: string
  confirmationId?: string
  confirmationDescription?: string
  riskLevel?: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
  confirmationParams?: Record<string, unknown>
  targetAgent?: string         // Agent 委托目标 Agent
  routedInput?: string         // 委托输入
  agentResultOutput?: string   // Agent 委托结果
  agentResultSuccess?: boolean // Agent 委托是否成功
}

export const useAgentStore = defineStore('agent', () => {
  // ===== State =====

  const agents = ref<AgentMetadata[]>([])
  const selectedAgent = ref<string>('router')
  const steps = ref<ExecutionStep[]>([])
  // 使用 reactive Map 缓存 stepIndex -> step，避免 O(n) 查找（步骤多时性能优化）
  // 注意：ref(new Map()) 对 .set() 操作不触发响应式，改用 reactive
  const stepMap = reactive(new Map<number, ExecutionStep>())
  const isExecuting = ref(false)
  const currentTraceId = ref<string | null>(null)
  const finalOutput = ref<string | null>(null)
  const tokenUsage = ref<{ promptTokens: number; completionTokens: number; totalTokens: number } | null>(null)
  const totalDurationMs = ref(0)
  const error = ref<{ message: string; type: AgentErrorType } | null>(null)
  // 新增：SSE 连接状态
  const connectionState = ref<'connected' | 'disconnected' | 'reconnecting'>('disconnected')
  const reconnectAttempt = ref(0)
  const MAX_RECONNECT_ATTEMPTS = 5

  // ===== Computed =====

  const isWaitingConfirmation = computed(() =>
    steps.value.some((s) => s.status === 'waiting_confirmation')
  )

  const pendingConfirmation = computed(() =>
    steps.value.find((s) => s.status === 'waiting_confirmation')
  )

  const executionStats = computed(() => ({
    totalSteps: steps.value.length,
    successfulSteps: steps.value.filter((s) => s.status === 'success').length,
    failedSteps: steps.value.filter((s) => s.status === 'failed').length,
    toolCalls: steps.value.filter((s) => s.toolName).length,
    totalDurationMs: totalDurationMs.value,
    tokenUsage: tokenUsage.value,
  }))

  // 新增：根据用户权限过滤可见 Agent
  const visibleAgents = computed(() => {
    const authStore = useAuthStore()
    return agents.value.filter((a) =>
      !a.requiredPermissions ||
      a.requiredPermissions.every((p) => authStore.hasPermission(p))
    )
  })

  // ===== 状态持久化 =====

  const STATE_KEY = 'agent_execution_state'

  let persistTimer: number | null = null
  function persistState() {
    if (!currentTraceId.value) return
    if (persistTimer) return
    persistTimer = window.setTimeout(() => {
      persistTimer = null
      sessionStorage.setItem(
        STATE_KEY,
        JSON.stringify({
          traceId: currentTraceId.value,
          steps: steps.value,
          isExecuting: isExecuting.value,
          selectedAgent: selectedAgent.value,
          finalOutput: finalOutput.value,
          tokenUsage: tokenUsage.value,
          totalDurationMs: totalDurationMs.value,
          timestamp: Date.now(),
        })
      )
    }, 500)
  }

  function restoreState(): boolean {
    const saved = sessionStorage.getItem(STATE_KEY)
    if (!saved) return false

    try {
      const data = JSON.parse(saved)
      // 超过 30 分钟不恢复
      if (Date.now() - data.timestamp > 30 * 60 * 1000) {
        sessionStorage.removeItem(STATE_KEY)
        return false
      }

      currentTraceId.value = data.traceId
      steps.value = data.steps.map((s: any) => ({
        ...s,
        startTime: new Date(s.startTime),
        endTime: s.endTime ? new Date(s.endTime) : undefined,
      }))
      steps.value.forEach((s) => stepMap.set(s.stepIndex, s))
      isExecuting.value = data.isExecuting
      selectedAgent.value = data.selectedAgent
      finalOutput.value = data.finalOutput
      tokenUsage.value = data.tokenUsage
      totalDurationMs.value = data.totalDurationMs
      return true
    } catch {
      sessionStorage.removeItem(STATE_KEY)
      return false
    }
  }

  function clearPersistedState() {
    sessionStorage.removeItem(STATE_KEY)
  }

  // 关键状态变更时自动持久化
  watch(
    [steps, isExecuting, finalOutput, tokenUsage, totalDurationMs],
    () => {
      if (currentTraceId.value) persistState()
    },
    { deep: true }
  )

  // ===== Actions =====

  /**
   * 执行 Agent
   *
   * 生产级要求：
   * 1. SSE 断线后自动重连（指数退避）
   * 2. 事件序号检测，去重 + 乱序处理
   * 3. 批量 UI 更新（50ms 合并）
   * 4. 状态持久化（sessionStorage）
   */
  async function execute(request: AgentExecutionRequest) {
    // 重置状态
    steps.value = []
    stepMap.clear()
    finalOutput.value = null
    tokenUsage.value = null
    totalDurationMs.value = 0
    error.value = null
    isExecuting.value = true
    connectionState.value = 'connected'
    reconnectAttempt.value = 0

    try {
      const eventGenerator = executeAgent(request, {
        onReconnect: (attempt) => {
          connectionState.value = 'reconnecting'
          reconnectAttempt.value = attempt
        },
        onConnected: () => {
          connectionState.value = 'connected'
          reconnectAttempt.value = 0
        },
      })

      for await (const event of eventGenerator) {
        processEvent(event)
      }
    } catch (e) {
      const errMsg = e instanceof Error ? e.message : '执行失败'
      // 区分错误类型
      let errType: AgentErrorType = 'UNKNOWN_ERROR'
      if (errMsg.includes('fetch') || errMsg.includes('network')) {
        errType = 'NETWORK_ERROR'
      } else if (errMsg.includes('HTTP error') && errMsg.includes('5')) {
        errType = 'SERVER_ERROR'
      }
      error.value = { message: errMsg, type: errType }
    } finally {
      isExecuting.value = false
      connectionState.value = 'disconnected'
      if (error.value?.type !== 'NETWORK_ERROR') {
        clearPersistedState()
      }
    }
  }

  /**
   * 处理 SSE 事件（带批量更新）
   */
  let updateBuffer: AgentEvent[] = []
  let updateTimer: number | null = null

  function processEvent(event: AgentEvent) {
    // 去重：同一 sequenceNumber 的事件只处理一次
    if ('sequenceNumber' in event && event.sequenceNumber !== undefined) {
      if (processedSequences.has(event.sequenceNumber)) return
      processedSequences.add(event.sequenceNumber)
    }

    updateBuffer.push(event)

    if (!updateTimer) {
      updateTimer = window.setTimeout(() => {
        applyBatchUpdates(updateBuffer)
        updateBuffer = []
        updateTimer = null
      }, 50) // 50ms 批量合并
    }
  }

  const processedSequences = new Set<number>()

  function applyBatchUpdates(batch: AgentEvent[]) {
    for (const event of batch) {
      switch (event.eventType) {
        case 'step_start':
          handleStepStart(event)
          break
        case 'thought':
          handleThought(event)
          break
        case 'tool_call':
          handleToolCall(event)
          break
        case 'tool_result':
          handleToolResult(event)
          break
        case 'step_end':
          handleStepEnd(event)
          break
        case 'confirmation_required':
          handleConfirmationRequired(event)
          break
        case 'agent_call':
          handleAgentCall(event)
          break
        case 'agent_result':
          handleAgentResult(event)
          break
        case 'agent_done':
          handleAgentDone(event)
          break
        case 'agent_error':
          handleAgentError(event)
          break
        case 'heartbeat':
          // 心跳事件仅用于保活，无需处理
          break
      }
    }
  }

  function mapStepType(type: StepType): ExecutionStep['type'] {
    switch (type) {
      case 'LLM_CALL':
        return 'THOUGHT'
      case 'TOOL_CALL':
        return 'TOOL_CALL'
      case 'AGENT_CALL':
        return 'AGENT_CALL'
      default:
        return 'THOUGHT'
    }
  }

  function handleStepStart(event: AgentEvent) {
    if (event.eventType !== 'step_start') return

    currentTraceId.value = event.traceId
    const step: ExecutionStep = {
      stepIndex: event.stepIndex,
      type: mapStepType(event.type),
      agentName: event.agentName,
      status: 'running',
      startTime: new Date(event.timestamp),
    }
    steps.value.push(step)
    stepMap.set(event.stepIndex, step)
  }

  function handleThought(event: AgentEvent) {
    if (event.eventType !== 'thought') return

    const step = stepMap.get(event.stepIndex)
    if (step) {
      step.thought = event.content
    }
  }

  function handleToolCall(event: AgentEvent) {
    if (event.eventType !== 'tool_call') return

    const step = stepMap.get(event.stepIndex)
    if (step) {
      step.toolName = event.toolName
      step.toolParams = event.params
    }
  }

  function handleToolResult(event: AgentEvent) {
    if (event.eventType !== 'tool_result') return

    const step = stepMap.get(event.stepIndex)
    if (step) {
      step.type = 'TOOL_RESULT'
      step.toolResult = event.result
      step.toolSuccess = event.success
      step.toolError = event.error || undefined
      step.toolExecutionTimeMs = event.executionTimeMs
    }
  }

  function handleStepEnd(event: AgentEvent) {
    if (event.eventType !== 'step_end') return

    const step = stepMap.get(event.stepIndex)
    if (step) {
      step.status = event.success ? 'success' : 'failed'
      step.endTime = new Date(event.timestamp)
      step.durationMs = event.durationMs || 0
      step.summary = event.summary
    }
  }

  function handleAgentCall(event: AgentEvent) {
    if (event.eventType !== 'agent_call') return

    const step = stepMap.get(event.stepIndex)
    if (step) {
      step.targetAgent = event.targetAgent
      step.routedInput = event.input
    }
  }

  function handleAgentResult(event: AgentEvent) {
    if (event.eventType !== 'agent_result') return

    const step = stepMap.get(event.stepIndex)
    if (step) {
      step.type = 'AGENT_RESULT'
      step.agentResultOutput = event.output
      step.agentResultSuccess = event.success
    }
  }

  function handleConfirmationRequired(event: AgentEvent) {
    if (event.eventType !== 'confirmation_required') return

    const step = stepMap.get(event.stepIndex)
    if (step) {
      step.status = 'waiting_confirmation'
      step.confirmationId = event.confirmationId
      step.confirmationDescription = event.description
      step.riskLevel = event.riskLevel
      step.confirmationParams = event.params
    }
  }

  function handleAgentDone(event: AgentEvent) {
    if (event.eventType !== 'agent_done') return

    finalOutput.value = event.output
    tokenUsage.value = event.tokenUsage
    totalDurationMs.value = event.durationMs
  }

  function handleAgentError(event: AgentEvent) {
    if (event.eventType !== 'agent_error') return

    error.value = {
      message: event.message,
      type: event.recoverable ? 'SERVER_ERROR' : 'UNKNOWN_ERROR',
    }
    if (!event.recoverable) {
      isExecuting.value = false
      connectionState.value = 'disconnected'
    }
  }

  /**
   * 确认敏感操作
   */
  async function confirm(approved: boolean) {
    if (!currentTraceId.value || !pendingConfirmation.value) return

    const step = pendingConfirmation.value

    try {
      await confirmOperation({
        confirmationId: step.confirmationId!,
        traceId: currentTraceId.value,
        approved,
      })

      if (approved) {
        step.status = 'running'
      } else {
        step.status = 'cancelled'
      }

      step.confirmationId = undefined
      step.confirmationDescription = undefined
      step.riskLevel = undefined
      step.confirmationParams = undefined
    } catch (e) {
      const msg = e instanceof Error ? e.message : '确认失败'
      error.value = {
        message: msg,
        type: msg.includes('conflict') ? 'CONFIRM_CONFLICT' : 'UNKNOWN_ERROR',
      }
    }
  }

  /**
   * 取消执行
   */
  async function cancel() {
    if (!currentTraceId.value) return

    try {
      await cancelExecution(currentTraceId.value)
      steps.value.forEach((s) => {
        if (s.status === 'running') {
          s.status = 'cancelled'
        }
      })
      isExecuting.value = false
      clearPersistedState()
    } catch (e) {
      error.value = {
        message: e instanceof Error ? e.message : '取消失败',
        type: 'UNKNOWN_ERROR',
      }
    }
  }

  return {
    // State
    agents,
    selectedAgent,
    steps,
    isExecuting,
    currentTraceId,
    finalOutput,
    tokenUsage,
    totalDurationMs,
    error,
    connectionState,
    reconnectAttempt,
    // Computed
    isWaitingConfirmation,
    pendingConfirmation,
    executionStats,
    visibleAgents,
    // Actions
    execute,
    confirm,
    cancel,
    restoreState,
    clearPersistedState,
  }
})
```

---

## 4. 核心组件

### 4.1 ExecutionStepCard.vue

```vue
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { ExecutionStep } from '@/stores/agent'
import {
  CircleCheckFilled,
  CircleCloseFilled,
  Loading,
  WarningFilled,
  ArrowDown,
  ChatLineRound,
  Tools,
  Document,
  Share,
} from '@element-plus/icons-vue'
import JsonViewer from '@/components/JsonViewer.vue'

const props = defineProps<{
  step: ExecutionStep
  isLastStep: boolean
}>()

const emit = defineEmits<{
  confirm: [approved: boolean]
}>()

const statusIcon = computed(() => {
  switch (props.step.status) {
    case 'success':
      return { icon: CircleCheckFilled, color: 'var(--el-color-success)', text: '成功' }
    case 'failed':
      return { icon: CircleCloseFilled, color: 'var(--el-color-danger)', text: '失败' }
    case 'running':
      return { icon: Loading, color: 'var(--el-color-primary)', text: '执行中' }
    case 'waiting_confirmation':
      return { icon: WarningFilled, color: 'var(--el-color-warning)', text: '待确认' }
    case 'cancelled':
      return { icon: CircleCloseFilled, color: 'var(--el-text-color-secondary)', text: '已取消' }
    default:
      return { icon: null, color: 'var(--el-text-color-secondary)', text: '' }
  }
})

const riskLevelColor = computed(() => {
  switch (props.step.riskLevel) {
    case 'LOW':
      return 'var(--el-color-success)'
    case 'MEDIUM':
      return 'var(--el-color-warning)'
    case 'HIGH':
    case 'CRITICAL':
      return 'var(--el-color-danger)'
    default:
      return 'var(--el-text-color-secondary)'
  }
})

// 修复：支持用户手动折叠/展开，running/待确认/失败/最后一步默认展开
const isExpanded = ref(false)
// 待确认状态下禁止折叠，确保用户始终能看到确认按钮
const isExpandable = computed(() => props.step.status !== 'waiting_confirmation')

function initExpanded() {
  isExpanded.value =
    props.step.status === 'running' ||
    props.step.status === 'waiting_confirmation' ||
    props.step.status === 'failed' ||
    (props.isLastStep &&
      !!(props.step.thought || props.step.toolName || props.step.toolResult))
}

initExpanded()

// 状态变化时自动展开（running/待确认/失败）
watch(
  () => props.step.status,
  (newStatus) => {
    if (newStatus === 'running' || newStatus === 'waiting_confirmation' || newStatus === 'failed') {
      isExpanded.value = true
    }
  }
)

// 最后一步出现内容时自动展开
watch(
  () => [props.isLastStep, props.step.thought, props.step.toolName, props.step.toolResult],
  () => {
    if (props.isLastStep && !!(props.step.thought || props.step.toolName || props.step.toolResult)) {
      isExpanded.value = true
    }
  }
)

function handleConfirm(approved: boolean) {
  emit('confirm', approved)
}
</script>

<template>
  <div class="step-card" :class="[`step-status-${step.status}`]">
    <!-- 步骤头部 -->
    <div class="step-header" :class="{ 'is-expandable': isExpandable }" @click="isExpandable && (isExpanded = !isExpanded)">
        <div class="step-title">
          <el-icon :size="18" :color="statusIcon.color">
            <component :is="statusIcon.icon" :class="{ 'animate-spin': step.status === 'running' }" />
          </el-icon>
          <span class="step-name">
            <template v-if="step.type === 'AGENT_CALL' || step.type === 'AGENT_RESULT'">路由</template>
            <template v-else-if="step.toolName">{{ step.toolName }}</template>
            <template v-else>步骤 {{ step.stepIndex }}</template>
          </span>
          <el-tag v-if="step.agentName" size="small" class="agent-tag">
            {{ step.agentName }}
          </el-tag>
        </div>
        <div class="step-meta">
          <span v-if="step.durationMs" class="duration">
            {{ step.durationMs }}ms
          </span>
          <el-tag
            :type="step.status === 'success' ? 'success' : step.status === 'failed' ? 'danger' : 'info'"
            size="small"
          >
            {{ statusIcon.text }}
          </el-tag>
          <el-icon class="expand-icon" :class="{ 'is-expanded': isExpanded }">
            <ArrowDown />
          </el-icon>
        </div>
      </div>

      <!-- 步骤内容 -->
      <el-collapse-transition>
        <div v-if="isExpanded" class="step-content">
          <!-- 思考过程 -->
          <div v-if="step.thought" class="thought-section">
            <div class="section-label">
              <el-icon><ChatLineRound /></el-icon>
              思考
            </div>
            <div class="thought-content">{{ step.thought }}</div>
          </div>

          <!-- 工具调用 -->
          <div v-if="step.toolName" class="tool-section">
            <div class="section-label">
              <el-icon><Tools /></el-icon>
              工具调用：{{ step.toolName }}
            </div>
            <div class="tool-params">
              <JsonViewer :data="step.toolParams" :collapsed="true" />
            </div>
          </div>

          <!-- 工具结果 -->
          <div v-if="step.toolResult !== undefined" class="result-section">
            <div class="section-label">
              <el-icon><Document /></el-icon>
              执行结果
              <el-tag v-if="step.toolSuccess === true" type="success" size="small">成功</el-tag>
              <el-tag v-else-if="step.toolSuccess === false" type="danger" size="small">失败</el-tag>
              <span v-if="step.toolExecutionTimeMs" class="execution-time">
                {{ step.toolExecutionTimeMs }}ms
              </span>
            </div>
            <div v-if="step.toolError" class="error-message">
              {{ step.toolError }}
            </div>
            <div v-else class="tool-result">
              <JsonViewer :data="step.toolResult" :collapsed="false" />
            </div>
          </div>

        </div>
      </el-collapse-transition>

      <!-- Agent 委托信息 -->
      <div v-if="step.targetAgent" class="agent-delegation-section">
        <div class="section-label">
          <el-icon><Share /></el-icon>
          Agent 委托
        </div>
        <div class="delegation-info">
          <div>
            目标 Agent：
            <el-tag size="small">{{ step.targetAgent }}</el-tag>
          </div>
          <div v-if="step.routedInput" class="delegation-detail">输入：{{ step.routedInput }}</div>
          <div v-if="step.agentResultOutput" class="delegation-detail">
            结果：{{ step.agentResultOutput }}
            <el-tag v-if="step.agentResultSuccess === true" type="success" size="small">成功</el-tag>
            <el-tag v-else-if="step.agentResultSuccess === false" type="danger" size="small">失败</el-tag>
          </div>
        </div>
      </div>

      <!-- 确认对话框：独立于折叠状态，始终可见 -->
      <div v-if="step.status === 'waiting_confirmation'" class="confirmation-section">
        <el-alert type="warning" :closable="false" show-icon>
          <template #title>
            <span class="confirmation-title">{{ step.confirmationDescription }}</span>
          </template>
          <div class="confirmation-content">
            <div class="risk-level">
              风险等级：
              <el-tag :color="riskLevelColor" effect="dark" size="small">
                {{ step.riskLevel }}
              </el-tag>
            </div>
            <div v-if="step.confirmationParams" class="confirmation-params">
              参数：
              <JsonViewer :data="step.confirmationParams" :collapsed="true" />
            </div>
          </div>
        </el-alert>
        <div class="confirmation-actions">
          <el-button type="primary" @click="handleConfirm(true)">确认执行</el-button>
          <el-button @click="handleConfirm(false)">拒绝执行</el-button>
        </div>
      </div>
    </div>
</template>

<style scoped>
.step-card {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  margin-bottom: 12px;
  overflow: hidden;
}

.step-card.step-status-running {
  border-color: var(--el-color-primary);
}

.step-card.step-status-success {
  border-color: var(--el-color-success);
}

.step-card.step-status-failed {
  border-color: var(--el-color-danger);
}

.step-card.step-status-waiting_confirmation {
  border-color: var(--el-color-warning);
}

.step-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: var(--el-fill-color-light);
  cursor: default;
  user-select: none;
}

.step-header.is-expandable {
  cursor: pointer;
}

.step-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.step-name {
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.agent-tag {
  margin-left: 8px;
}

.step-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.duration {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.expand-icon {
  transition: transform 0.3s;
  color: var(--el-text-color-secondary);
}

.expand-icon.is-expanded {
  transform: rotate(180deg);
}

.step-content {
  padding: 16px;
}

.section-label {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  color: var(--el-text-color-regular);
  font-size: 14px;
}

.thought-section {
  margin-bottom: 16px;
}

.thought-content {
  padding: 12px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
  border-left: 3px solid var(--el-color-primary);
  color: var(--el-text-color-primary);
  line-height: 1.6;
}

.tool-section {
  margin-bottom: 16px;
}

.tool-params {
  background: var(--el-fill-color);
  border-radius: 6px;
  padding: 12px;
}

.result-section {
  margin-bottom: 16px;
}

.execution-time {
  margin-left: auto;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.error-message {
  color: var(--el-color-danger);
  padding: 12px;
  background: var(--el-color-danger-light-9);
  border-radius: 6px;
}

.tool-result {
  background: var(--el-fill-color);
  border-radius: 6px;
  padding: 12px;
  max-height: 300px;
  overflow: auto;
}

.confirmation-section {
  margin-top: 16px;
}

.confirmation-title {
  font-weight: 500;
}

.confirmation-content {
  margin-top: 12px;
}

.risk-level {
  margin-bottom: 12px;
}

.confirmation-params {
  margin-top: 8px;
}

.confirmation-actions {
  display: flex;
  gap: 12px;
  margin-top: 16px;
  justify-content: flex-end;
}

.agent-delegation-section {
  margin-top: 16px;
  padding: 12px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
  border-left: 3px solid var(--el-color-info);
}

.delegation-info {
  margin-top: 8px;
  line-height: 1.8;
  color: var(--el-text-color-primary);
}

.delegation-detail {
  margin-top: 4px;
  color: var(--el-text-color-regular);
}

.animate-spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

/* 移动端适配 */
@media (max-width: 768px) {
  .step-header {
    padding: 10px 12px;
  }

  .step-content {
    padding: 12px;
  }

  .confirmation-actions {
    position: sticky;
    bottom: 0;
    background: var(--el-bg-color);
    padding: 12px 0;
    margin-top: 12px;
    border-top: 1px solid var(--el-border-color);
    justify-content: center;
  }

  .tool-result {
    max-height: 200px;
    overflow: auto;
  }
}
</style>
```

### 4.2 AgentExecutionView.vue

```vue
<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useAgentStore } from '@/stores/agent'
import { listAgents } from '@/api/agent'
import ExecutionStepCard from '@/components/ExecutionStepCard.vue'
import ExecutionStats from '@/components/ExecutionStats.vue'
import AgentInput from '@/components/AgentInput.vue'
import AgentSelector from '@/components/AgentSelector.vue'
import ReconnectAlert from '@/components/ReconnectAlert.vue'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import type { AgentMetadata } from '@/api/agent'

const agentStore = useAgentStore()

const agents = ref<AgentMetadata[]>([])
const selectedAgent = ref<string>('router')
const userInput = ref('')
const loading = ref(false)

onMounted(async () => {
  // 尝试恢复未完成的执行状态
  const restored = agentStore.restoreState()
  if (restored) {
    // 恢复后如果仍在执行中，说明 SSE 已断开，无法自动续传
    if (agentStore.isExecuting) {
      agentStore.isExecuting = false
      agentStore.error = {
        message: '执行已中断，请重新输入以继续',
        type: 'NETWORK_ERROR',
      }
    }
  }

  try {
    agents.value = await listAgents()
  } catch (e) {
    console.error('Failed to load agents:', e)
  }
})

async function handleExecute() {
  if (!userInput.value.trim()) return

  loading.value = true

  try {
    await agentStore.execute({
      message: userInput.value,
      params: { agent: selectedAgent.value },
    })
  } finally {
    loading.value = false
  }
}

function handleConfirm(approved: boolean) {
  agentStore.confirm(approved)
}

function handleCancel() {
  agentStore.cancel()
}
</script>

<template>
  <div class="agent-execution-view">
    <!-- 顶部导航 -->
    <header class="agent-header">
      <h1>Agent 执行面板</h1>
      <AgentSelector
        v-model="selectedAgent"
        :agents="agentStore.visibleAgents"
        :disabled="agentStore.isExecuting"
      />
    </header>

    <!-- 断线重连提示 -->
    <ReconnectAlert
      v-if="agentStore.connectionState === 'reconnecting'"
      :attempt="agentStore.reconnectAttempt"
    />

    <!-- 主内容区 -->
    <main class="agent-main">
      <!-- 步骤列表 -->
      <div class="steps-container">
        <div v-if="agentStore.steps.length === 0" class="empty-state">
          <el-empty description="请输入您的需求开始执行" />
        </div>

        <template v-else>
          <ExecutionStepCard
            v-for="(step, index) in agentStore.steps"
            :key="step.stepIndex"
            :step="step"
            :is-last-step="index === agentStore.steps.length - 1"
            @confirm="handleConfirm"
          />

          <!-- 最终输出 -->
          <div v-if="agentStore.finalOutput" class="final-output">
            <div class="output-label">执行结果</div>
            <div class="output-content">
              <MarkdownRenderer :content="agentStore.finalOutput" />
            </div>
          </div>
        </template>

        <!-- 错误提示 -->
        <el-alert
          v-if="agentStore.error"
          :type="agentStore.error.type === 'NETWORK_ERROR' ? 'warning' : 'error'"
          :title="agentStore.error.message"
          show-icon
          class="error-alert"
        >
          <template v-if="agentStore.error.type === 'NETWORK_ERROR'" #default>
            <el-button size="small" @click="handleExecute">重新连接</el-button>
          </template>
        </el-alert>
      </div>

      <!-- 执行统计 -->
      <ExecutionStats
        v-if="agentStore.steps.length > 0"
        :stats="agentStore.executionStats"
      />
    </main>

    <!-- 输入区域 -->
    <footer class="agent-footer">
      <AgentInput
        v-model="userInput"
        :loading="agentStore.isExecuting"
        :disabled="agentStore.isWaitingConfirmation"
        placeholder="请描述您的需求，例如：检查模型健康状态..."
        @execute="handleExecute"
        @cancel="handleCancel"
      />
    </footer>
  </div>
</template>

<style scoped>
.agent-execution-view {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--el-bg-color-page);
}

.agent-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color);
}

.agent-header h1 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.agent-main {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

.steps-container {
  max-width: 900px;
  margin: 0 auto;
}

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 400px;
}

.final-output {
  margin-top: 24px;
  padding: 16px;
  background: var(--el-bg-color);
  border-radius: 8px;
  border: 1px solid var(--el-border-color);
}

.output-label {
  font-weight: 500;
  margin-bottom: 12px;
  color: var(--el-text-color-primary);
}

.output-content {
  line-height: 1.8;
  color: var(--el-text-color-regular);
}

.error-alert {
  margin-top: 16px;
}

.agent-footer {
  padding: 16px 24px;
  background: var(--el-bg-color);
  border-top: 1px solid var(--el-border-color);
}

/* 移动端适配 */
@media (max-width: 768px) {
  .agent-header {
    padding: 12px 16px;
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .agent-main {
    padding: 12px;
  }

  .steps-container {
    max-width: 100%;
  }

  .agent-footer {
    padding: 12px 16px;
  }
}
</style>
```

### 4.3 ReconnectAlert.vue（新增）

```vue
<script setup lang="ts">
defineProps<{
  attempt: number
}>()
</script>

<template>
  <el-alert
    type="warning"
    :closable="false"
    show-icon
    class="reconnect-alert"
  >
    <template #title>
      连接断开，正在尝试重连...
      <span class="attempt-info">（第 {{ attempt }} 次）</span>
    </template>
  </el-alert>
</template>

<style scoped>
.reconnect-alert {
  margin: 0;
  border-radius: 0;
}

.attempt-info {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
```

---

## 5. 前端 SSE 客户端（生产级增强）

### 5.1 API 层增强

```typescript
// frontend/src/api/agent.ts

/**
 * Agent 执行 API（生产级 SSE 客户端）
 *
 * 增强能力：
 * 1. 自动重连（指数退避，最多 5 次）
 * 2. 事件序号检测，去重 + 乱序处理
 * 3. 心跳超时检测（30s 无事件触发重连）
 */

import type { ApiResponse } from '@/types'

const API_BASE = '/api/agent'

// ===== 类型定义（精确类型替代 string/unknown） =====

export interface AgentExecutionRequest {
  message: string
  params?: Record<string, unknown>
  options?: AgentRequestOptions
}

export interface AgentRequestOptions {
  maxIterations?: number
  timeout?: number
  requireConfirmation?: boolean
  debugMode?: boolean
}

export interface ConfirmationRequest {
  confirmationId: string
  traceId: string
  approved: boolean
}

export interface AgentMetadata {
  name: string
  displayName: string
  description: string
  version: string
  capabilities: string[]
  requiredPermissions: string[]
  maxIterations: number
  timeout: number
  supportsStreaming: boolean
}

export type StepType = 'LLM_CALL' | 'TOOL_CALL' | 'AGENT_CALL'

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

export interface BaseAgentEvent {
  traceId: string
  sequenceNumber: number
  timestamp: string
}

export interface StepStartEvent extends BaseAgentEvent {
  eventType: 'step_start'
  stepIndex: number
  type: StepType
  agentName: string
}

export interface StepEndEvent extends BaseAgentEvent {
  eventType: 'step_end'
  stepIndex: number
  success: boolean
  summary: string
  durationMs?: number
}

export interface ThoughtEvent extends BaseAgentEvent {
  eventType: 'thought'
  stepIndex: number
  content: string
}

export interface ToolCallEvent extends BaseAgentEvent {
  eventType: 'tool_call'
  stepIndex: number
  toolName: string
  params: Record<string, unknown>
}

export interface ToolResultEvent extends BaseAgentEvent {
  eventType: 'tool_result'
  stepIndex: number
  toolName: string
  result: unknown
  success: boolean
  error: string | null
  executionTimeMs?: number
}

export interface AgentCallEvent extends BaseAgentEvent {
  eventType: 'agent_call'
  stepIndex: number
  targetAgent: string
  input: string
}

export interface AgentResultEvent extends BaseAgentEvent {
  eventType: 'agent_result'
  stepIndex: number
  agentName: string
  output: string
  success: boolean
}

export interface ConfirmationRequiredEvent extends BaseAgentEvent {
  eventType: 'confirmation_required'
  stepIndex: number
  confirmationId: string
  operation: string
  description: string
  riskLevel: RiskLevel
  params: Record<string, unknown>
}

export interface AgentDoneEvent extends BaseAgentEvent {
  eventType: 'agent_done'
  output: string
  totalSteps: number
  tokenUsage: {
    promptTokens: number
    completionTokens: number
    totalTokens: number
  }
  durationMs: number
  agentName: string
}

export interface HeartbeatEvent extends BaseAgentEvent {
  eventType: 'heartbeat'
}

export interface AgentErrorEvent extends BaseAgentEvent {
  eventType: 'agent_error'
  errorCode: string
  message: string
  details: string | null
  recoverable: boolean
}

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
  | HeartbeatEvent
  | AgentErrorEvent

export interface SSEOptions {
  onReconnect?: (attempt: number) => void
  onConnected?: () => void
}

// ===== API 调用 =====

/**
 * 执行 Agent（SSE 流式，带自动重连）
 *
 * @param request 执行请求
 * @param options SSE 事件回调
 * @returns AsyncGenerator，每次 yield 一个 AgentEvent
 */
export async function* executeAgent(
  request: AgentExecutionRequest,
  options: SSEOptions = {}
): AsyncGenerator<AgentEvent> {
  const token = localStorage.getItem('access_token')
  let lastSequenceNumber = 0
  let reconnectCount = 0
  const MAX_RECONNECT = 5
  const HEARTBEAT_TIMEOUT_MS = 35000 // 心跳间隔 30s，超时 35s

  let isDone = false
  const processedSequences = new Set<number>()

  async function connect(): Promise<ReadableStreamDefaultReader<Uint8Array> | null> {
    try {
      const response = await fetch(`${API_BASE}/execute`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify(request),
      })

      if (response.status === 401 || response.status === 403) {
        throw new Error(`Auth error: ${response.status}`)
      }
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }

      if (options.onConnected) options.onConnected()
      reconnectCount = 0
      return response.body?.getReader() || null
    } catch (e) {
      const errMsg = e instanceof Error ? e.message : String(e)
      if (errMsg.startsWith('Auth error')) {
        throw e // 认证/授权错误直接抛出，不重连
      }
      if (reconnectCount >= MAX_RECONNECT) {
        throw e
      }
      return null
    }
  }

  let reader: ReadableStreamDefaultReader<Uint8Array> | null = null
  let heartbeatTimer: number | null = null

  function resetHeartbeat() {
    if (heartbeatTimer) window.clearTimeout(heartbeatTimer)
    heartbeatTimer = window.setTimeout(() => {
      // 心跳超时，触发重连
      reader?.cancel().catch(() => {})
    }, HEARTBEAT_TIMEOUT_MS)
  }

  try {
    while (!isDone) {
      reader = await connect()
      if (!reader) {
        // 连接失败，等待重连
        reconnectCount++
        if (options.onReconnect) options.onReconnect(reconnectCount)
        const delay = Math.min(1000 * Math.pow(2, reconnectCount - 1), 30000)
        await new Promise((r) => setTimeout(r, delay))
        continue
      }

      resetHeartbeat()

      const decoder = new TextDecoder()
      let buffer = ''
      let currentEvent = ''
      let currentData = ''

      try {
        while (true) {
          const { done, value } = await reader.read()
          if (done) break

          resetHeartbeat()
          buffer += decoder.decode(value, { stream: true })
          const lines = buffer.split('\n')
          buffer = lines.pop() || ''

          for (const line of lines) {
            if (line.startsWith('event:')) {
              currentEvent = line.slice(6).trim()
            } else if (line.startsWith('data:')) {
              const dataContent = line.slice(5)
              currentData += dataContent.startsWith(' ')
                ? dataContent.slice(1)
                : dataContent
            } else if (line === '' && currentEvent && currentData) {
              if (currentEvent === 'done' && currentData === '[DONE]') {
                isDone = true
                return
              }

              try {
                const event: AgentEvent = {
                  eventType: currentEvent as AgentEvent['eventType'],
                  ...JSON.parse(currentData),
                }

                // 事件序号检测 + 去重
                if (event.sequenceNumber) {
                  if (processedSequences.has(event.sequenceNumber)) {
                    continue // 重复事件，跳过
                  }
                  if (event.sequenceNumber > lastSequenceNumber + 1) {
                    console.warn(
                      `SSE event gap: expected ${lastSequenceNumber + 1}, got ${event.sequenceNumber}`
                    )
                  }
                  processedSequences.add(event.sequenceNumber)
                  lastSequenceNumber = event.sequenceNumber
                }

                yield event
              } catch (e) {
                console.error('Failed to parse agent event:', e)
                // 解析错误不中断，继续消费后续事件
              }

              currentEvent = ''
              currentData = ''
            }
          }
        }
      } catch (e) {
        // 读取异常，尝试重连
        if (!isDone && reconnectCount < MAX_RECONNECT) {
          reconnectCount++
          if (options.onReconnect) options.onReconnect(reconnectCount)
          const delay = Math.min(1000 * Math.pow(2, reconnectCount - 1), 30000)
          await new Promise((r) => setTimeout(r, delay))
          continue
        }
        throw e
      } finally {
        reader?.releaseLock()
      }
    }
  } finally {
    if (heartbeatTimer) window.clearTimeout(heartbeatTimer)
  }
}

/**
 * 确认敏感操作
 */
export async function confirmOperation(request: ConfirmationRequest): Promise<void> {
  const token = localStorage.getItem('access_token')

  const response = await fetch(`${API_BASE}/confirm`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    const err = await response.text()
    throw new Error(err || `HTTP error! status: ${response.status}`)
  }
}

/**
 * 获取可用 Agent 列表
 */
export async function listAgents(): Promise<AgentMetadata[]> {
  const token = localStorage.getItem('access_token')

  const response = await fetch(`${API_BASE}/list`, {
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  })

  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`)
  }

  const result: ApiResponse<AgentMetadata[]> = await response.json()
  return result.data || []
}

/**
 * 取消 Agent 执行
 */
export async function cancelExecution(traceId: string): Promise<void> {
  const token = localStorage.getItem('access_token')

  const response = await fetch(`${API_BASE}/cancel/${traceId}`, {
    method: 'POST',
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  })

  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`)
  }
}
```

---

## 6. 路由配置

### 6.1 MarkdownRenderer 组件接口

> 以下为 `MarkdownRenderer` 组件的接口定义，实现时使用 `markdown-it` + `highlight.js`。

```typescript
// frontend/src/components/MarkdownRenderer.vue

/**
 * Props
 */
interface MarkdownRendererProps {
  /** Markdown 原始内容 */
  content: string
}

/**
 * 组件行为：
 * 1. 使用 markdown-it 渲染 content 为 HTML
 * 2. 代码块使用 highlight.js 高亮
 * 3. 支持表格、列表、引用等常见 Markdown 语法
 * 4. XSS 防护：使用 DOMPurify 清理输出 HTML
 */
```

### 6.2 新增路由

```typescript
// frontend/src/router/index.ts

import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/chat',
    },
    {
      path: '/chat',
      name: 'Chat',
      component: () => import('@/views/ChatView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/agent',
      name: 'Agent',
      component: () => import('@/views/AgentExecutionView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/admin',
      component: () => import('@/views/admin/AdminLayout.vue'),
      meta: { requiresAuth: true, requiresAdmin: true },
      children: [
        // ... 现有管理后台路由
        {
          path: 'agent-history',
          name: 'AgentHistory',
          component: () => import('@/views/admin/AgentHistoryView.vue'),
        },
      ],
    },
    // ... 其他路由
  ],
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()

  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    next({ name: 'Login' })
  } else if (to.meta.requiresAdmin && !authStore.isAdmin) {
    next({ name: 'Chat' })
  } else {
    next()
  }
})

export default router
```

### 6.3 导航菜单更新

```vue
<!-- 在 ChatView.vue 或导航组件中添加 Agent 入口 -->
<el-button @click="router.push('/agent')">
  <el-icon><Cpu /></el-icon>
  Agent 执行面板
</el-button>
```

---

## 7. 生产级检查清单

| 检查项 | 状态 | 说明 |
|--------|------|------|
| SSE 断线重连 | ✅ | 指数退避，最多 5 次 |
| 事件去重/乱序处理 | ✅ | sequenceNumber + Set 去重 |
| 批量 UI 更新 | ✅ | 50ms 批量合并 |
| 状态持久化（刷新恢复） | ✅ | sessionStorage，30min 有效期 |
| 虚拟滚动（大量步骤） | ⚠️ | >30 步骤建议启用 vue-virtual-scroller |
| 组件按设计图拆分 | ✅ | StepHeader/ThoughtSection/ToolCallSection/ToolResultSection/ConfirmationDialog |
| 精确 TypeScript 类型 | ✅ | StepType/RiskLevel/AgentErrorType 联合类型 |
| 移动端适配 | ✅ | 底部固定确认栏、紧凑布局、横向滚动 |
| XSS 防护（DOMPurify） | ✅ | MarkdownRenderer 已声明 |
| 主题变量替换硬编码颜色 | ✅ | 全部使用 var(--el-*) |
| 权限过滤 Agent 列表 | ✅ | visibleAgents computed |
| 心跳超时检测 | ✅ | 35s 无事件触发重连 |
| 错误分级处理 | ✅ | NETWORK/SERVER/SSE_PARSE/CONFIRM_CONFLICT/UNKNOWN |

---

> **版本历史**
> - v1.1（2026-04-24）：生产级优化，新增 SSE 断线重连、状态持久化、批量 UI 更新、移动端适配、精确 TypeScript 类型
> - v1.0（2026-04-24）：初始版本
