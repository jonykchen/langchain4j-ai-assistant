<script setup lang="ts">
/**
 * Agent 执行面板页面
 *
 * <p>展示 Agent 执行步骤、确认敏感操作、查看执行统计。
 * 使用组件化拆分，增强可维护性。
 *
 * <h2>组件结构</h2>
 * <ul>
 *   <li>AgentSelector - Agent 下拉选择器</li>
 *   <li>ExecutionStepCard - 步骤卡片</li>
 *   <li>ExecutionStats - 执行统计</li>
 *   <li>ReconnectAlert - 断线重连提示</li>
 * </ul>
 *
 * <h2>增强功能</h2>
 * <ul>
 *   <li>页面刷新后 30min 内恢复执行状态</li>
 *   <li>SSE 断线自动重连提示</li>
 *   <li>步骤卡片折叠/展开</li>
 *   <li>JSON 结果格式化展示</li>
 * </ul>
 *
 * @author jonychen
 */
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAgentStore } from '@/stores/agent'
import { storeToRefs } from 'pinia'
import type { RiskLevel } from '@/types/agent'
import AgentSelector from '@/components/agent/AgentSelector.vue'
import ExecutionStepCard from '@/components/agent/ExecutionStepCard.vue'
import ExecutionStats from '@/components/agent/ExecutionStats.vue'
import ReconnectAlert from '@/components/agent/ReconnectAlert.vue'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'

const router = useRouter()
const agentStore = useAgentStore()
const {
  activeExecution,
  activeTraceId,
  runningExecutions,
  historyExecutions,
  availableAgents,
  isLoadingAgents,
  connectionState,
  reconnectAttempt,
  isExecuting
} = storeToRefs(agentStore)

// 用户输入
const userInput = ref('')

// 选中的 Agent
const selectedAgent = ref<string>()

// 自动滚动标记
const stepsContainer = ref<HTMLElement>()
let autoScrollObserver: MutationObserver | null = null

// 风险等级颜色映射
const riskLevelColors: Record<RiskLevel, string> = {
  LOW: 'success',
  MEDIUM: 'warning',
  HIGH: 'danger',
  CRITICAL: 'danger'
}

// 风险等级中文
const riskLevelLabels: Record<RiskLevel, string> = {
  LOW: '低风险',
  MEDIUM: '中风险',
  HIGH: '高风险',
  CRITICAL: '极高风险'
}

/** 执行 Agent */
function handleExecute() {
  if (!userInput.value.trim() || isExecuting.value) return
  agentStore.startExecution(userInput.value.trim())
  userInput.value = ''
}

/** 确认操作 */
function handleConfirm(approved: boolean) {
  if (activeExecution.value?.pendingConfirmation) {
    agentStore.approveConfirmation(activeExecution.value.traceId, approved)
  }
}

/** 取消执行 */
function handleCancel() {
  agentStore.cancelCurrentExecution()
}

/** 选择执行记录 */
function handleSelectExecution(traceId: string) {
  agentStore.selectExecution(traceId)
}

/** 清除历史 */
function handleClearHistory() {
  agentStore.clearHistory()
}

/** 新任务 */
function handleNewTask() {
  userInput.value = ''
  agentStore.selectExecution('')
}

/** Agent 选择 */
function handleAgentSelect(_agent: unknown) {
  // 可用于后续指定 Agent 执行
}

/** 手动重连（当前为提示，实际由 SSE 客户端自动处理） */
function handleReconnect() {
  // SSE 客户端已内置自动重连，此处可手动触发重新执行
  if (activeExecution.value?.status === 'error') {
    agentStore.startExecution(activeExecution.value.userInput)
  }
}

/** 关闭重连提示 */
function handleDismissReconnect() {
  // 提示会自动消失
}

/** 格式化耗时 */
function formatDuration(ms?: number): string {
  if (!ms) return '-'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(2)}s`
}

/** 格式化时间 */
function formatTime(date?: Date): string {
  if (!date) return '-'
  return date.toLocaleTimeString()
}

/** 自动滚动到底部 */
function setupAutoScroll() {
  if (!stepsContainer.value) return

  autoScrollObserver = new MutationObserver(() => {
    if (stepsContainer.value) {
      stepsContainer.value.scrollTop = stepsContainer.value.scrollHeight
    }
  })

  autoScrollObserver.observe(stepsContainer.value, {
    childList: true,
    subtree: true
  })
}

// 生命周期
onMounted(() => {
  agentStore.loadAgents()

  // 尝试恢复执行状态
  const restored = agentStore.restoreState()
  if (restored) {
    console.info('[AgentExecutionView] 恢复了执行状态')
  }

  // 设置自动滚动
  setTimeout(setupAutoScroll, 100)
})

onUnmounted(() => {
  if (autoScrollObserver) {
    autoScrollObserver.disconnect()
    autoScrollObserver = null
  }
})
</script>

<template>
  <div class="agent-view">
    <!-- 断线重连提示 -->
    <ReconnectAlert
      :is-reconnecting="connectionState === 'reconnecting'"
      :reconnect-attempts="reconnectAttempt"
      :is-connected="connectionState === 'connected'"
      @reconnect="handleReconnect"
      @dismiss="handleDismissReconnect"
    />

    <!-- 左侧：执行列表 -->
    <aside class="agent-sidebar">
      <header class="sidebar-header">
        <h2>Agent 执行</h2>
        <el-button text @click="router.push('/')">
          <el-icon><ArrowLeft /></el-icon>
          返回聊天
        </el-button>
      </header>

      <!-- 运行中的执行 -->
      <div v-if="runningExecutions.length > 0" class="section">
        <h3 class="section-title">执行中</h3>
        <div
          v-for="exec in runningExecutions"
          :key="exec.traceId"
          :class="['execution-item', { active: exec.traceId === activeTraceId }]"
          @click="handleSelectExecution(exec.traceId)"
        >
          <div class="exec-header">
            <el-tag size="small" :type="exec.status === 'waiting_confirmation' ? 'warning' : 'primary'">
              {{ exec.status === 'waiting_confirmation' ? '等待确认' : '运行中' }}
            </el-tag>
            <span class="exec-time">{{ formatTime(exec.startTime) }}</span>
          </div>
          <div class="exec-input">{{ exec.userInput }}</div>
        </div>
      </div>

      <!-- 历史记录 -->
      <div v-if="historyExecutions.length > 0" class="section">
        <div class="section-header">
          <h3 class="section-title">历史记录</h3>
          <el-button text size="small" @click="handleClearHistory">清空</el-button>
        </div>
        <div
          v-for="exec in historyExecutions"
          :key="exec.traceId"
          :class="['execution-item', { active: exec.traceId === activeTraceId }]"
          @click="handleSelectExecution(exec.traceId)"
        >
          <div class="exec-header">
            <el-tag
              size="small"
              :type="exec.status === 'completed' ? 'success' : 'danger'"
            >
              {{ exec.status === 'completed' ? '完成' : '失败' }}
            </el-tag>
            <span class="exec-time">{{ formatTime(exec.startTime) }}</span>
          </div>
          <div class="exec-input">{{ exec.userInput }}</div>
          <div v-if="exec.durationMs" class="exec-duration">
            耗时: {{ formatDuration(exec.durationMs) }}
          </div>
        </div>
      </div>
    </aside>

    <!-- 右侧：执行详情 -->
    <main class="agent-main">
      <!-- 无执行时显示输入 -->
      <div v-if="!activeExecution" class="empty-state">
        <h2>Agent 执行面板</h2>
        <p class="description">选择一个 Agent 执行任务，或输入您的指令</p>

        <!-- Agent 选择器（权限过滤） -->
        <div class="agent-selector-area">
          <AgentSelector
            v-model="selectedAgent"
            :agents="availableAgents"
            :loading="isLoadingAgents"
            @select="handleAgentSelect"
          />
        </div>

        <!-- 输入框 -->
        <div class="input-area">
          <el-input
            v-model="userInput"
            type="textarea"
            :rows="3"
            placeholder="输入您的指令，例如：查询上周 token 消耗"
            @keydown.ctrl.enter="handleExecute"
          />
          <el-button
            type="primary"
            :disabled="!userInput.trim()"
            @click="handleExecute"
          >
            开始执行
          </el-button>
        </div>
      </div>

      <!-- 执行详情 -->
      <div v-else class="execution-detail">
        <!-- 头部 -->
        <header class="detail-header">
          <div class="header-info">
            <h2>{{ activeExecution.userInput }}</h2>
            <div class="header-meta">
              <el-tag>{{ activeExecution.agentName || '路由中...' }}</el-tag>
              <el-tag :type="activeExecution.status === 'completed' ? 'success' : activeExecution.status === 'error' ? 'danger' : 'primary'">
                {{ activeExecution.status }}
              </el-tag>
              <span v-if="activeExecution.durationMs">
                耗时: {{ formatDuration(activeExecution.durationMs) }}
              </span>
            </div>
          </div>
          <div class="header-actions">
            <el-button
              v-if="isExecuting || activeExecution.status === 'waiting_confirmation'"
              type="danger"
              @click="handleCancel"
            >
              取消执行
            </el-button>
            <el-button @click="handleNewTask">
              新任务
            </el-button>
          </div>
        </header>

        <!-- 确认提示 -->
        <div v-if="activeExecution.pendingConfirmation" class="confirmation-panel">
          <el-alert
            :title="`需要确认: ${activeExecution.pendingConfirmation.operation}`"
            :type="riskLevelColors[activeExecution.pendingConfirmation.riskLevel]"
            show-icon
            :closable="false"
          >
            <template #default>
              <p>{{ activeExecution.pendingConfirmation.description }}</p>
              <p>
                <strong>风险等级：</strong>
                <el-tag :type="riskLevelColors[activeExecution.pendingConfirmation.riskLevel]" size="small">
                  {{ riskLevelLabels[activeExecution.pendingConfirmation.riskLevel] }}
                </el-tag>
              </p>
            </template>
          </el-alert>
          <div class="confirmation-actions">
            <el-button type="danger" @click="handleConfirm(false)">拒绝</el-button>
            <el-button type="primary" @click="handleConfirm(true)">确认执行</el-button>
          </div>
        </div>

        <!-- 执行统计 -->
        <div v-if="activeExecution.steps.length > 0" class="stats-area">
          <ExecutionStats :execution="activeExecution" />
        </div>

        <!-- 步骤列表 -->
        <div ref="stepsContainer" class="steps-container">
          <h3 class="steps-title">执行步骤</h3>
          <div class="steps-list">
            <ExecutionStepCard
              v-for="(step, index) in activeExecution.steps"
              :key="step.id"
              :step="step"
              :is-last-step="index === activeExecution.steps.length - 1"
              @confirm="handleConfirm"
            />
          </div>
        </div>

        <!-- 最终输出 -->
        <div v-if="activeExecution.output" class="output-panel">
          <h3>执行结果</h3>
          <div class="output-content">
            <MarkdownRenderer :content="activeExecution.output" />
          </div>
        </div>

        <!-- 错误信息 -->
        <div v-if="activeExecution.errorMessage" class="error-panel">
          <el-alert type="error" :title="activeExecution.errorMessage" show-icon :closable="false" />
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.agent-view {
  display: flex;
  height: 100vh;
  background: #f5f7fa;
}

/* 左侧边栏 */
.agent-sidebar {
  width: 280px;
  background: #fff;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.sidebar-header h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.section {
  padding: 12px;
  border-bottom: 1px solid #e4e7ed;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.section-title {
  margin: 0 0 8px 0;
  font-size: 13px;
  color: #909399;
}

.execution-item {
  padding: 10px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 6px;
  background: #f5f7fa;
  transition: background 0.2s;
}

.execution-item:hover {
  background: #ecf5ff;
}

.execution-item.active {
  background: #ecf5ff;
  border: 1px solid #409eff;
}

.exec-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.exec-time {
  font-size: 12px;
  color: #909399;
}

.exec-input {
  font-size: 13px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.exec-duration {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

/* 主内容区 */
.agent-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
}

.empty-state h2 {
  margin: 0 0 8px 0;
  color: #303133;
}

.description {
  color: #909399;
  margin: 0 0 24px 0;
}

.agent-selector-area {
  width: 100%;
  max-width: 500px;
  margin-bottom: 16px;
}

.input-area {
  width: 100%;
  max-width: 500px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* 执行详情 */
.execution-detail {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.detail-header {
  padding: 16px 20px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.header-info h2 {
  margin: 0 0 8px 0;
  font-size: 16px;
  font-weight: 600;
}

.header-meta {
  display: flex;
  gap: 8px;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 8px;
}

/* 确认面板 */
.confirmation-panel {
  padding: 16px 20px;
  background: #fdf6ec;
  border-bottom: 1px solid #e6a23c;
}

.confirmation-actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

/* 统计区域 */
.stats-area {
  padding: 12px 20px;
  border-bottom: 1px solid #e4e7ed;
}

/* 步骤列表 */
.steps-container {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
}

.steps-title {
  margin: 0 0 12px 0;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.steps-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* 输出面板 */
.output-panel {
  padding: 16px 20px;
  background: #fff;
  border-top: 1px solid #e4e7ed;
}

.output-panel h3 {
  margin: 0 0 12px 0;
  font-size: 14px;
  font-weight: 600;
}

.output-content {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 6px;
}

/* 错误面板 */
.error-panel {
  padding: 16px 20px;
}

/* 移动端适配 */
@media (max-width: 768px) {
  .agent-view {
    flex-direction: column;
  }

  .agent-sidebar {
    width: 100%;
    height: auto;
    max-height: 200px;
    border-right: none;
    border-bottom: 1px solid #e4e7ed;
  }

  .detail-header {
    flex-direction: column;
    gap: 8px;
  }

  .header-actions {
    width: 100%;
  }

  .header-actions .el-button {
    flex: 1;
  }
}
</style>
