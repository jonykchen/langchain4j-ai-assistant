<script setup lang="ts">
/**
 * Agent 执行面板页面（生产级重构版）
 *
 * <p>采用固定头部 + Tab 分区 + Sticky 底部操作栏的信息架构。
 * 使用 CSS 变量体系支持主题切换。
 *
 * <h2>布局结构</h2>
 * <ul>
 *   <li>ReconnectAlert - Inline Banner（非 fixed）</li>
 *   <li>Sidebar - 执行历史列表（移动端可收起）</li>
 *   <li>Execution Workspace - 固定 Header + Stats + Tabs + Sticky Confirmation</li>
 * </ul>
 *
 * @author jonychen
 */
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, Document, Menu, ChatDotRound, Cpu, Promotion, Timer, Warning, Loading } from '@element-plus/icons-vue'
import { useAgentStore } from '@/stores/agent'
import { storeToRefs } from 'pinia'
import type { RiskLevel } from '@/types/agent'
import AgentSelector from '@/components/agent/AgentSelector.vue'
import ExecutionStepCard from '@/components/agent/ExecutionStepCard.vue'
import VirtualStepList from '@/components/agent/VirtualStepList.vue'
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

// Tab 控制
const activeTab = ref<'steps' | 'output' | 'logs'>('steps')

// 移动端侧边栏可见性
const sidebarVisible = ref(true)

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

// Quick Actions
const quickActions = [
  '查询上周 token 消耗',
  '分析系统性能瓶颈',
  '生成数据报表',
  '检查 Agent 执行状态'
]

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
  activeTab.value = 'steps'
}

/** 清除历史 */
function handleClearHistory() {
  agentStore.clearHistory()
}

/** 新任务 */
function handleNewTask() {
  userInput.value = ''
  agentStore.selectExecution('')
  activeTab.value = 'steps'
}

/** Agent 选择 */
function handleAgentSelect(_agent: unknown) {
  // 可用于后续指定 Agent 执行
}

/** 快捷指令 */
function handleQuickAction(text: string) {
  userInput.value = text
}

/** 手动重连 */
function handleReconnect() {
  if (activeExecution.value?.status === 'error') {
    agentStore.startExecution(activeExecution.value.userInput)
  }
}

/** 关闭重连提示 */
function handleDismissReconnect() {
  // 预留
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
    if (stepsContainer.value && activeTab.value === 'steps') {
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
  agentStore.restoreState()

  // 设置自动滚动
  setTimeout(setupAutoScroll, 100)

  // 移动端默认收起侧边栏
  if (window.innerWidth < 768) {
    sidebarVisible.value = false
  }
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
    <!-- 断线重连提示（Inline Banner） -->
    <ReconnectAlert
      :is-reconnecting="connectionState === 'reconnecting'"
      :reconnect-attempts="reconnectAttempt"
      :is-connected="connectionState === 'connected'"
      :connection-state="connectionState"
      @reconnect="handleReconnect"
      @dismiss="handleDismissReconnect"
    />

    <div class="agent-body">
      <!-- 左侧：执行列表 -->
      <aside class="agent-sidebar" :class="{ collapsed: !sidebarVisible }">
        <header class="sidebar-header">
          <h2>Agent 执行</h2>
          <el-button text @click="router.push('/')">
            <el-icon><ArrowLeft /></el-icon>
            <span class="hide-on-mobile">返回聊天</span>
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

        <!-- 空侧边栏提示 -->
        <div v-if="runningExecutions.length === 0 && historyExecutions.length === 0" class="sidebar-empty">
          <el-icon :size="32" color="var(--text-tertiary)"><Document /></el-icon>
          <p>暂无执行记录</p>
        </div>
      </aside>

      <!-- 移动端侧边栏遮罩 -->
      <div
        v-if="sidebarVisible"
        class="sidebar-backdrop"
        @click="sidebarVisible = false"
      />

      <!-- 右侧：执行详情 -->
      <main class="agent-main">
        <!-- 移动端头部 -->
        <div class="mobile-header">
          <el-button text @click="sidebarVisible = !sidebarVisible">
            <el-icon><Menu /></el-icon>
          </el-button>
          <span class="mobile-title">{{ activeExecution ? '执行详情' : 'Agent 执行' }}</span>
          <el-button text @click="router.push('/')">
            <el-icon><ChatDotRound /></el-icon>
          </el-button>
        </div>

        <!-- 无执行时显示空状态 -->
        <div v-if="!activeExecution" class="empty-state">
          <div class="empty-welcome">
            <div class="empty-icon">
              <el-icon :size="48" color="var(--color-primary)"><Cpu /></el-icon>
            </div>
            <h2>Agent 执行面板</h2>
            <p class="description">选择一个 Agent 执行任务，或输入您的指令</p>

            <div class="agent-selector-area">
              <AgentSelector
                v-model="selectedAgent"
                :agents="availableAgents"
                :loading="isLoadingAgents"
                @select="handleAgentSelect"
              />
            </div>

            <div class="quick-actions">
              <span class="quick-label">快速开始：</span>
              <div class="quick-chips">
                <el-button
                  v-for="action in quickActions"
                  :key="action"
                  size="small"
                  @click="handleQuickAction(action)"
                >
                  {{ action }}
                </el-button>
              </div>
            </div>
          </div>

          <div class="empty-input-bar">
            <div class="input-wrapper">
              <el-input
                v-model="userInput"
                type="textarea"
                :rows="2"
                placeholder="输入您的指令，按 Ctrl+Enter 执行..."
                @keydown.ctrl.enter="handleExecute"
              />
              <el-button
                type="primary"
                :disabled="!userInput.trim()"
                :loading="isExecuting"
                @click="handleExecute"
              >
                <el-icon><Promotion /></el-icon>
              </el-button>
            </div>
          </div>
        </div>

        <!-- 执行详情工作区 -->
        <div v-else class="execution-workspace">
          <!-- 头部 -->
          <header class="detail-header">
            <div class="header-info">
              <h2 class="header-title" :title="activeExecution.userInput">
                {{ activeExecution.userInput }}
              </h2>
              <div class="header-meta">
                <el-tag size="small">{{ activeExecution.agentName || '路由中...' }}</el-tag>
                <el-tag
                  size="small"
                  :type="activeExecution.status === 'completed' ? 'success' : activeExecution.status === 'error' ? 'danger' : 'primary'"
                >
                  {{ activeExecution.status }}
                </el-tag>
                <span v-if="activeExecution.durationMs" class="meta-time">
                  <el-icon><Timer /></el-icon>
                  {{ formatDuration(activeExecution.durationMs) }}
                </span>
              </div>
            </div>
            <div class="header-actions">
              <el-button
                v-if="isExecuting || activeExecution.status === 'waiting_confirmation'"
                type="danger"
                size="small"
                @click="handleCancel"
              >
                取消
              </el-button>
              <el-button size="small" @click="handleNewTask">
                新任务
              </el-button>
            </div>
          </header>

          <!-- 执行统计 -->
          <div v-if="activeExecution.steps.length > 0" class="stats-area">
            <ExecutionStats :execution="activeExecution" />
          </div>

          <!-- Tab 切换 -->
          <el-tabs v-model="activeTab" class="workspace-tabs" type="border-card">
            <el-tab-pane label="执行步骤" name="steps">
              <div ref="stepsContainer" class="tab-scroll-content">
                <VirtualStepList
                  v-if="activeExecution.steps.length > 30"
                  :steps="activeExecution.steps"
                />
                <div v-else class="steps-list">
                  <ExecutionStepCard
                    v-for="(step, index) in activeExecution.steps"
                    :key="step.id"
                    :step="step"
                    :step-index="index + 1"
                    :is-last-step="index === activeExecution.steps.length - 1"
                    @confirm="handleConfirm"
                  />
                </div>
                <div v-if="activeExecution.steps.length === 0" class="tab-empty">
                  <el-icon :size="24" color="var(--text-tertiary)"><Loading /></el-icon>
                  <p>等待执行步骤...</p>
                </div>
              </div>
            </el-tab-pane>

            <el-tab-pane label="执行结果" name="output">
              <div class="tab-scroll-content">
                <div v-if="activeExecution.output" class="output-content">
                  <MarkdownRenderer :content="activeExecution.output" />
                </div>
                <div v-else class="tab-empty">
                  <el-icon :size="24" color="var(--text-tertiary)"><Document /></el-icon>
                  <p>暂无执行结果</p>
                </div>
              </div>
            </el-tab-pane>

            <el-tab-pane label="日志" name="logs">
              <div class="tab-scroll-content">
                <div v-if="activeExecution.errorMessage" class="log-error">
                  <el-alert type="error" :title="activeExecution.errorMessage" show-icon :closable="false" />
                </div>
                <div v-else class="tab-empty">
                  <el-icon :size="24" color="var(--text-tertiary)"><Document /></el-icon>
                  <p>暂无日志</p>
                </div>
              </div>
            </el-tab-pane>
          </el-tabs>

          <!-- 底部确认操作栏（Sticky） -->
          <div v-if="activeExecution.pendingConfirmation" class="confirmation-sticky">
            <div class="confirmation-content">
              <div class="confirmation-info">
                <el-icon
                  :size="18"
                  :color="riskLevelColors[activeExecution.pendingConfirmation.riskLevel] === 'danger' ? 'var(--color-danger)' : 'var(--color-warning)'"
                >
                  <Warning />
                </el-icon>
                <div class="confirmation-text">
                  <div class="confirmation-title">
                    需要确认: {{ activeExecution.pendingConfirmation.operation }}
                  </div>
                  <div class="confirmation-desc">
                    {{ activeExecution.pendingConfirmation.description }}
                    <el-tag
                      :type="riskLevelColors[activeExecution.pendingConfirmation.riskLevel]"
                      size="small"
                      class="risk-tag"
                    >
                      {{ riskLevelLabels[activeExecution.pendingConfirmation.riskLevel] }}
                    </el-tag>
                  </div>
                </div>
              </div>
              <div class="confirmation-actions">
                <el-button type="danger" size="small" @click="handleConfirm(false)">拒绝</el-button>
                <el-button type="primary" size="small" @click="handleConfirm(true)">确认执行</el-button>
              </div>
            </div>
          </div>
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
.agent-view {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--bg-secondary);
  overflow: hidden;
}

.agent-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

/* ===== 左侧边栏 ===== */
.agent-sidebar {
  width: var(--sidebar-width);
  background: var(--bg-primary);
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  flex-shrink: 0;
  transition: transform 0.3s ease;
}

.sidebar-header {
  padding: var(--space-xl) var(--space-lg);
  border-bottom: 1px solid var(--border-color);
  display: flex;
  justify-content: space-between;
  align-items: center;
  position: sticky;
  top: 0;
  background: var(--bg-primary);
  z-index: var(--z-sticky);
}

.sidebar-header h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.section {
  padding: var(--space-xl) var(--space-lg);
  border-bottom: 1px solid var(--border-color);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.section-title {
  margin: 0 0 var(--space-md) 0;
  font-size: 13px;
  color: var(--text-tertiary);
  font-weight: 500;
}

.execution-item {
  padding: var(--space-md) var(--space-lg);
  border-radius: var(--radius-lg);
  cursor: pointer;
  margin-bottom: var(--space-md);
  background: var(--bg-secondary);
  transition: all 0.2s ease;
  border: 1px solid transparent;
  box-shadow: var(--shadow-sm);
}

.execution-item:hover {
  background: var(--color-primary-light);
  border-color: var(--color-primary);
}

.execution-item.active {
  background: var(--color-primary-light);
  border-color: var(--color-primary);
}

.exec-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.exec-time {
  font-size: 12px;
  color: var(--text-tertiary);
}

.exec-input {
  font-size: 13px;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.exec-duration {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-top: 4px;
}

.sidebar-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-tertiary);
  padding: var(--space-2xl);
}

/* ===== 主内容区 ===== */
.agent-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

.mobile-header {
  display: none;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-sm) var(--space-lg);
  background: var(--bg-primary);
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
}

.mobile-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

/* ===== 空状态 ===== */
.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.empty-welcome {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-2xl);
  overflow-y: auto;
}

.empty-welcome h2 {
  margin: var(--space-lg) 0 var(--space-sm) 0;
  color: var(--text-primary);
  font-size: 20px;
}

.description {
  color: var(--text-tertiary);
  margin: 0 0 var(--space-xl) 0;
}

.agent-selector-area {
  width: 100%;
  max-width: 480px;
  margin-bottom: var(--space-lg);
}

.quick-actions {
  width: 100%;
  max-width: 480px;
}

.quick-label {
  font-size: 13px;
  color: var(--text-tertiary);
  margin-bottom: var(--space-sm);
  display: block;
}

.quick-chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
}

.quick-chips .el-button {
  border-radius: var(--radius-full);
}

.empty-input-bar {
  flex-shrink: 0;
  padding: var(--space-lg) var(--space-xl);
  background: var(--bg-primary);
  border-top: 1px solid var(--border-color);
}

.input-wrapper {
  display: flex;
  gap: var(--space-md);
  max-width: 800px;
  margin: 0 auto;
  width: 100%;
  align-items: flex-end;
}

.input-wrapper .el-textarea {
  flex: 1;
}

/* ===== 执行详情工作区 ===== */
.execution-workspace {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.detail-header {
  padding: var(--space-2xl) var(--space-2xl);
  background: var(--bg-primary);
  border-bottom: 1px solid var(--border-color);
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  flex-shrink: 0;
  gap: var(--space-xl);
}

.header-title {
  margin: 0 0 var(--space-md) 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.header-meta {
  display: flex;
  gap: var(--space-md);
  align-items: center;
  flex-wrap: wrap;
}

.meta-time {
  font-size: 12px;
  color: var(--text-tertiary);
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.header-actions {
  display: flex;
  gap: var(--space-sm);
  flex-shrink: 0;
}

.stats-area {
  padding: var(--space-xl) var(--space-2xl);
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
}

/* ===== Tabs ===== */
.workspace-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  margin-top: var(--space-sm);
}

.workspace-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
  padding: 0 var(--space-lg);
}

.workspace-tabs :deep(.el-tabs__content) {
  flex: 1;
  overflow: hidden;
  padding: 0;
}

.workspace-tabs :deep(.el-tab-pane) {
  height: 100%;
  overflow: hidden;
}

.tab-scroll-content {
  height: 100%;
  overflow-y: auto;
  padding: var(--space-2xl) var(--space-2xl);
}

.steps-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2xl);
}

.tab-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--text-tertiary);
  gap: var(--space-sm);
}

.output-content {
  background: var(--bg-secondary);
  padding: var(--space-2xl);
  border-radius: var(--radius-lg);
  line-height: 1.7;
}

.log-error {
  padding: var(--space-xl);
}

/* ===== 底部确认操作栏 ===== */
.confirmation-sticky {
  flex-shrink: 0;
  background: var(--color-warning-light);
  border-top: 1px solid var(--color-warning);
  padding: var(--space-xl) var(--space-2xl);
  box-shadow: var(--shadow-sticky);
  z-index: var(--z-sticky);
}

.confirmation-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-lg);
  max-width: var(--max-content-width);
  margin: 0 auto;
  width: 100%;
}

.confirmation-info {
  display: flex;
  align-items: flex-start;
  gap: var(--space-md);
  flex: 1;
  min-width: 0;
}

.confirmation-text {
  flex: 1;
  min-width: 0;
}

.confirmation-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.confirmation-desc {
  font-size: 13px;
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-wrap: wrap;
}

.risk-tag {
  flex-shrink: 0;
}

.confirmation-actions {
  display: flex;
  gap: var(--space-sm);
  flex-shrink: 0;
}

/* ===== 移动端适配 ===== */
@media (max-width: 768px) {
  .agent-sidebar {
    position: fixed;
    left: 0;
    top: 0;
    bottom: 0;
    z-index: var(--z-drawer);
    box-shadow: var(--shadow-lg);
  }

  .agent-sidebar.collapsed {
    transform: translateX(-100%);
  }

  .sidebar-backdrop {
    position: fixed;
    inset: 0;
    background: var(--bg-overlay);
    z-index: var(--z-modal-backdrop);
  }

  .hide-on-mobile {
    display: none;
  }

  .mobile-header {
    display: flex;
  }

  .detail-header {
    flex-direction: column;
    gap: var(--space-md);
    padding: var(--space-lg);
  }

  .header-actions {
    width: 100%;
  }

  .header-actions .el-button {
    flex: 1;
  }

  .confirmation-content {
    flex-direction: column;
    align-items: stretch;
  }

  .confirmation-actions {
    justify-content: flex-end;
  }

  .input-wrapper {
    padding: 0 var(--space-md);
  }

  .tab-scroll-content {
    padding: var(--space-xl);
  }

  .detail-header {
    padding: var(--space-lg);
  }

  .stats-area {
    padding: var(--space-lg) var(--space-lg);
  }

  .confirmation-sticky {
    padding: var(--space-lg);
  }
}
</style>
