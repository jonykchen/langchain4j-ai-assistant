<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAgentStore } from '@/stores/agent'
import { storeToRefs } from 'pinia'
import type { ExecutionStep, RiskLevel } from '@/types/agent'

const router = useRouter()
const agentStore = useAgentStore()
const { activeExecution, activeTraceId, runningExecutions, historyExecutions, availableAgents, isLoadingAgents } = storeToRefs(agentStore)

// 用户输入
const userInput = ref('')

// 是否正在执行
const isExecuting = computed(() => activeExecution.value?.status === 'running')

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

// 步骤类型图标
const stepTypeIcons: Record<string, string> = {
  THOUGHT: 'Cpu',
  TOOL_CALL: 'Connection',
  TOOL_RESULT: 'Document',
  LLM_CALL: 'ChatDotRound',
  AGENT_CALL: 'User'
}

// 步骤类型中文
const stepTypeLabels: Record<string, string> = {
  THOUGHT: '思考',
  TOOL_CALL: '工具调用',
  TOOL_RESULT: '工具结果',
  LLM_CALL: 'LLM 调用',
  AGENT_CALL: 'Agent 委托'
}

function handleExecute() {
  if (!userInput.value.trim() || isExecuting.value) return
  agentStore.startExecution(userInput.value.trim())
  userInput.value = ''
}

function handleConfirm(approved: boolean) {
  if (activeExecution.value?.pendingConfirmation) {
    agentStore.approveConfirmation(activeExecution.value.traceId, approved)
  }
}

function handleCancel() {
  agentStore.cancelCurrentExecution()
}

function handleSelectExecution(traceId: string) {
  agentStore.selectExecution(traceId)
}

function handleClearHistory() {
  agentStore.clearHistory()
}

function formatDuration(ms?: number): string {
  if (!ms) return '-'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(2)}s`
}

function formatTime(date?: Date): string {
  if (!date) return '-'
  return date.toLocaleTimeString()
}

function getStepStatusType(step: ExecutionStep): string {
  if (step.status === 'error') return 'danger'
  if (step.status === 'success') return 'success'
  return 'primary'
}

onMounted(() => {
  agentStore.loadAgents()
})
</script>

<template>
  <div class="agent-view">
    <!-- 左侧：执行列表 -->
    <aside class="agent-sidebar">
      <header class="sidebar-header">
        <h2>Agent 执行</h2>
        <el-button
          text
          @click="router.push('/')"
        >
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

        <!-- Agent 选择 -->
        <div class="agent-selector">
          <el-select
            v-loading="isLoadingAgents"
            placeholder="选择 Agent（可选）"
            clearable
          >
            <el-option
              v-for="agent in availableAgents"
              :key="agent.name"
              :label="agent.displayName"
              :value="agent.name"
            >
              <div class="agent-option">
                <span class="agent-name">{{ agent.displayName }}</span>
                <span class="agent-desc">{{ agent.description }}</span>
              </div>
            </el-option>
          </el-select>
        </div>

        <!-- 输入框 -->
        <div class="input-area">
          <el-input
            v-model="userInput"
            type="textarea"
            :rows="3"
            placeholder="输入您的指令，例如：检查所有模型的健康状态"
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
            <el-button @click="userInput = ''; agentStore.selectExecution('')">
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

        <!-- 步骤列表 -->
        <div class="steps-container">
          <h3 class="steps-title">执行步骤</h3>
          <div class="steps-list">
            <div
              v-for="step in activeExecution.steps"
              :key="step.id"
              class="step-item"
            >
              <div class="step-header">
                <div class="step-icon">
                  <el-icon :size="16">
                    <component :is="stepTypeIcons[step.type] || 'Document'" />
                  </el-icon>
                </div>
                <span class="step-type">{{ stepTypeLabels[step.type] || step.type }}</span>
                <el-tag :type="getStepStatusType(step)" size="small">
                  {{ step.status }}
                </el-tag>
              </div>

              <!-- 思考内容 -->
              <div v-if="step.content" class="step-content">
                <pre v-if="step.type === 'THOUGHT'" class="thought-content">{{ step.content }}</pre>
                <span v-else>{{ step.content }}</span>
              </div>

              <!-- 工具调用 -->
              <div v-if="step.toolName" class="tool-info">
                <div class="tool-name">
                  <el-icon><Connection /></el-icon>
                  {{ step.toolName }}
                </div>
                <div v-if="step.toolParams && Object.keys(step.toolParams).length > 0" class="tool-params">
                  <strong>参数：</strong>
                  <code>{{ JSON.stringify(step.toolParams, null, 2) }}</code>
                </div>
                <div v-if="step.toolResult !== undefined" class="tool-result">
                  <strong>结果：</strong>
                  <pre>{{ typeof step.toolResult === 'string' ? step.toolResult : JSON.stringify(step.toolResult, null, 2) }}</pre>
                </div>
              </div>

              <!-- 错误信息 -->
              <div v-if="step.error" class="step-error">
                <el-alert type="error" :closable="false">
                  {{ step.error }}
                </el-alert>
              </div>
            </div>
          </div>
        </div>

        <!-- 最终输出 -->
        <div v-if="activeExecution.output" class="output-panel">
          <h3>执行结果</h3>
          <div class="output-content">
            <pre>{{ activeExecution.output }}</pre>
          </div>
          <div v-if="activeExecution.tokenUsage" class="token-usage">
            <span>Prompt Tokens: {{ activeExecution.tokenUsage.promptTokens }}</span>
            <span>Completion Tokens: {{ activeExecution.tokenUsage.completionTokens }}</span>
            <span>Total: {{ activeExecution.tokenUsage.totalTokens }}</span>
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

.agent-selector {
  width: 100%;
  max-width: 500px;
  margin-bottom: 16px;
}

.agent-option {
  display: flex;
  flex-direction: column;
}

.agent-name {
  font-weight: 500;
}

.agent-desc {
  font-size: 12px;
  color: #909399;
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

.step-item {
  background: #fff;
  border-radius: 8px;
  padding: 12px;
  border: 1px solid #e4e7ed;
}

.step-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.step-icon {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #409eff;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.step-type {
  font-weight: 500;
  color: #303133;
}

.step-content {
  margin-top: 8px;
}

.thought-content {
  background: #f5f7fa;
  padding: 10px;
  border-radius: 4px;
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-word;
}

.tool-info {
  margin-top: 8px;
  padding: 8px;
  background: #f5f7fa;
  border-radius: 4px;
}

.tool-name {
  display: flex;
  align-items: center;
  gap: 4px;
  font-weight: 500;
  color: #409eff;
}

.tool-params,
.tool-result {
  margin-top: 8px;
  font-size: 13px;
}

.tool-params code,
.tool-result pre {
  background: #fff;
  padding: 8px;
  border-radius: 4px;
  display: block;
  margin-top: 4px;
  overflow-x: auto;
}

.step-error {
  margin-top: 8px;
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

.output-content pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}

.token-usage {
  margin-top: 8px;
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #909399;
}

/* 错误面板 */
.error-panel {
  padding: 16px 20px;
}
</style>
