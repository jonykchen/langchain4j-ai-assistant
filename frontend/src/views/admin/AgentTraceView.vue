<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { observabilityApi, type AgentTrace, type AgentSpan, type TraceStatistics } from '@/api/observability'
import { PageContainer, StatsCard, EmptyState } from '@/components/layout'
import PathBreadcrumb from '@/components/PathBreadcrumb.vue'

const loading = ref(false)
const traces = ref<AgentTrace[]>([])
const activeTraces = ref<AgentTrace[]>([])
const spans = ref<AgentSpan[]>([])
const selectedTrace = ref<AgentTrace | null>(null)
const detailVisible = ref(false)
const spanViewVisible = ref(false)
const statistics = ref<TraceStatistics | null>(null)

const filters = reactive({
  status: '',
  agentType: '',
  userId: ''
})

const loadTraces = async () => {
  loading.value = true
  try {
    const result = await observabilityApi.getTraces({
      status: filters.status,
      agentType: filters.agentType,
      userId: filters.userId,
      limit: 50
    })
    // 兼容后端返回分页对象 {data: [...], total, ...} 或直接返回数组的情况
    const resultData = result as unknown as { data?: AgentTrace[] }
    traces.value = Array.isArray(result) ? result : Array.isArray(resultData?.data) ? resultData.data : []
  } catch (error) {
    ElMessage.error('加载追踪列表失败')
  } finally {
    loading.value = false
  }
}

const loadActiveTraces = async () => {
  try {
    const result = await observabilityApi.getActiveTraces()
    activeTraces.value = Array.isArray(result) ? result : []
  } catch (error) {
    console.error('加载活跃追踪失败', error)
  }
}

const loadStatistics = async () => {
  try {
    statistics.value = await observabilityApi.getTraceStatistics()
  } catch (error) {
    console.error('加载统计信息失败', error)
  }
}

const showDetail = (trace: AgentTrace) => {
  selectedTrace.value = trace
  detailVisible.value = true
}

const showSpanView = async (trace: AgentTrace) => {
  selectedTrace.value = trace
  try {
    const result = await observabilityApi.getTraceSpans(trace.traceId)
    // 兼容后端返回分页对象 {data: [...], total, ...} 或直接返回数组的情况
    const resultData = result as unknown as { data?: AgentSpan[] }
    spans.value = Array.isArray(result) ? result : Array.isArray(resultData?.data) ? resultData.data : []
    spanViewVisible.value = true
  } catch (error) {
    ElMessage.error('加载追踪详情失败')
  }
}

const evaluateTrace = async (trace: AgentTrace) => {
  try {
    const result = await observabilityApi.evaluateTrace(trace.traceId)
    ElMessage.success(`评测完成，得分: ${(result.overallScore * 100).toFixed(1)}%`)
  } catch (error) {
    ElMessage.error('评测失败')
  }
}

const resetFilters = () => {
  filters.status = ''
  filters.agentType = ''
  filters.userId = ''
  loadTraces()
}

const getStatusType = (status: string) => {
  const types: Record<string, string> = {
    'RUNNING': 'primary',
    'COMPLETED': 'success',
    'FAILED': 'danger',
    'CANCELLED': 'info'
  }
  return types[status] || 'info'
}

const getSpanType = (type: string) => {
  const types: Record<string, string> = {
    'THOUGHT': 'primary',
    'ACTION': 'warning',
    'OBSERVATION': 'info',
    'LLM_CALL': 'success',
    'TOOL_EXECUTE': 'warning'
  }
  return types[type] || 'info'
}

const formatTime = (time: string) => {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}

const formatPercent = (rate?: number) => {
  if (rate === undefined) return '0%'
  return (rate * 100).toFixed(1) + '%'
}

const formatDuration = (ms?: number) => {
  if (!ms) return '-'
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`
  return `${(ms / 60000).toFixed(1)}m`
}

const formatTokens = (tokens?: number) => {
  if (!tokens) return '-'
  if (tokens >= 1000) return `${(tokens / 1000).toFixed(1)}K`
  return tokens.toString()
}

const truncate = (text: string, maxLength: number) => {
  if (!text) return ''
  return text.length > maxLength ? text.slice(0, maxLength) + '...' : text
}

// 自动刷新活跃追踪
let refreshInterval: ReturnType<typeof setInterval> | undefined

const startPolling = () => {
  if (refreshInterval) return
  refreshInterval = setInterval(() => {
    // 页面不可见时跳过轮询，节省资源
    if (document.visibilityState !== 'visible') return
    loadActiveTraces()
    loadStatistics()
  }, 5000)
}

const stopPolling = () => {
  if (refreshInterval) {
    clearInterval(refreshInterval)
    refreshInterval = undefined
  }
}

onMounted(() => {
  loadTraces()
  loadActiveTraces()
  loadStatistics()
  startPolling()
})

onUnmounted(() => {
  stopPolling()
})
</script>

<template>
  <PageContainer :loading="loading">
    <!-- 统计卡片 -->
    <div class="stats-grid">
      <StatsCard
        title="总追踪数"
        :value="statistics?.total || 0"
        icon="DataLine"
        icon-type="requests"
      />
      <StatsCard
        title="已完成"
        :value="statistics?.completed || 0"
        icon="CircleCheck"
        icon-type="success"
        :trend="statistics?.successRate && statistics.successRate > 0.8 ? 'up' : 'flat'"
        :trend-value="formatPercent(statistics?.successRate)"
      />
      <StatsCard
        title="活跃中"
        :value="statistics?.active || 0"
        icon="Loading"
        icon-type="active"
      />
      <StatsCard
        title="失败数"
        :value="statistics?.failed || 0"
        icon="CircleClose"
        icon-type="errors"
      />
    </div>

    <!-- 筛选栏 -->
    <el-card class="modern-card filter-card">
      <div class="filter-row">
        <el-select v-model="filters.status" placeholder="执行状态" clearable class="filter-select">
          <el-option label="全部" value="" />
          <el-option label="运行中" value="RUNNING" />
          <el-option label="已完成" value="COMPLETED" />
          <el-option label="失败" value="FAILED" />
          <el-option label="已取消" value="CANCELLED" />
        </el-select>
        <el-select v-model="filters.agentType" placeholder="Agent 类型" clearable class="filter-select">
          <el-option label="全部" value="" />
          <el-option label="ReAct" value="REACT" />
          <el-option label="Plan-Execute" value="PLAN_EXECUTE" />
        </el-select>
        <el-input v-model="filters.userId" placeholder="用户 ID" clearable class="filter-input" />
        <el-button type="primary" @click="loadTraces">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
    </el-card>

    <!-- 活跃追踪提示 -->
    <el-alert
      v-if="activeTraces.length > 0"
      :title="`有 ${activeTraces.length} 个 Agent 正在执行`"
      type="info"
      :closable="false"
      class="active-alert"
    >
      <template #default>
        <div class="active-tags">
          <el-tag v-for="trace in activeTraces" :key="trace.traceId" size="small">
            {{ trace.agentType }} - {{ trace.traceId.slice(0, 8) }}
          </el-tag>
        </div>
      </template>
    </el-alert>

    <!-- 追踪列表 -->
    <el-card class="modern-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">追踪列表</span>
        </div>
      </template>
      <el-table :data="traces" stripe>
        <el-table-column prop="traceId" label="Trace ID" width="180">
          <template #default="{ row }">
            <PathBreadcrumb :value="row.traceId" separator="uuid" :max-items="3" />
          </template>
        </el-table-column>
        <el-table-column prop="agentType" label="类型" width="120" align="center">
          <template #default="{ row }">
            <span class="status-badge" :class="row.agentType === 'REACT' ? 'success' : 'warning'">
              {{ row.agentType }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="goal" label="任务目标" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <span class="status-badge" :class="getStatusType(row.status)">{{ row.status }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="iterations" label="迭代次数" width="100" align="center" />
        <el-table-column prop="executionTimeMs" label="耗时(ms)" width="100" align="right">
          <template #default="{ row }">
            <span class="value-number">{{ row.executionTimeMs || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="tokenUsage.totalTokens" label="Tokens" width="100" align="right">
          <template #default="{ row }">
            <span class="value-number">{{ row.tokenUsage?.totalTokens || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="startTime" label="开始时间" width="180">
          <template #default="{ row }">
            {{ formatTime(row.startTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="showSpanView(row)">
              追踪详情
            </el-button>
            <el-button link type="success" @click="evaluateTrace(row)">
              评测
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="traces.length === 0" class="table-empty">
        <EmptyState title="暂无追踪数据" description="Agent 执行追踪将在这里显示" />
      </div>
    </el-card>

    <!-- 追踪详情对话框 -->
    <el-dialog
      v-model="detailVisible"
      title="追踪详情"
      width="720px"
      destroy-on-close
      class="trace-detail-dialog"
    >
      <div v-if="selectedTrace" class="trace-detail">
        <!-- 头部：状态 + 关键指标 -->
        <div class="trace-header">
          <div class="trace-id-section">
            <PathBreadcrumb :value="selectedTrace.traceId" separator="uuid" :max-items="3" class="trace-id-breadcrumb" />
            <el-tag :type="getStatusType(selectedTrace.status)" size="small" effect="dark">
              {{ selectedTrace.status }}
            </el-tag>
            <el-tag type="info" size="small" effect="plain">{{ selectedTrace.agentType }}</el-tag>
          </div>
          <div class="trace-metrics">
            <div class="metric-item">
              <span class="metric-value">{{ selectedTrace.iterations }}</span>
              <span class="metric-label">迭代</span>
            </div>
            <div class="metric-item">
              <span class="metric-value">{{ formatDuration(selectedTrace.executionTimeMs) }}</span>
              <span class="metric-label">耗时</span>
            </div>
            <div class="metric-item">
              <span class="metric-value">{{ formatTokens(selectedTrace.tokenUsage?.totalTokens) }}</span>
              <span class="metric-label">Tokens</span>
            </div>
          </div>
        </div>

        <!-- 任务目标 -->
        <div class="trace-section">
          <div class="section-label">任务目标</div>
          <div class="section-content goal-text">{{ selectedTrace.goal }}</div>
        </div>

        <!-- 最终输出 -->
        <div v-if="selectedTrace.finalOutput" class="trace-section">
          <div class="section-label">最终输出</div>
          <el-scrollbar max-height="200px">
            <pre class="output-pre">{{ selectedTrace.finalOutput }}</pre>
          </el-scrollbar>
        </div>

        <!-- 错误信息 -->
        <div v-if="selectedTrace.errorMessage" class="trace-section error-section">
          <div class="section-label">错误信息</div>
          <el-alert type="error" :closable="false" show-icon>
            {{ selectedTrace.errorMessage }}
          </el-alert>
        </div>

        <!-- 详细信息折叠面板 -->
        <el-collapse class="detail-collapse">
          <el-collapse-item title="详细信息" name="details">
            <div class="detail-grid">
              <div class="detail-item" style="grid-column: span 2">
                <span class="detail-label">Trace ID</span>
                <PathBreadcrumb :value="selectedTrace.traceId" separator="uuid" :max-items="8" :expandable="true" />
              </div>
              <div class="detail-item" style="grid-column: span 2">
                <span class="detail-label">会话 ID</span>
                <PathBreadcrumb :value="selectedTrace.sessionId" separator="uuid" :max-items="6" :expandable="true" />
              </div>
              <div class="detail-item">
                <span class="detail-label">用户</span>
                <span class="detail-value">{{ selectedTrace.userId || '-' }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">开始时间</span>
                <span class="detail-value">{{ formatTime(selectedTrace.startTime) }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">结束时间</span>
                <span class="detail-value">{{ formatTime(selectedTrace.endTime) }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">Token 明细</span>
                <span class="detail-value">
                  Prompt: {{ selectedTrace.tokenUsage?.promptTokens || 0 }} /
                  Completion: {{ selectedTrace.tokenUsage?.completionTokens || 0 }}
                </span>
              </div>
            </div>
          </el-collapse-item>
        </el-collapse>
      </div>
    </el-dialog>

    <!-- Span 可视化对话框 -->
    <el-dialog
      v-model="spanViewVisible"
      title="执行追踪可视化"
      width="90%"
      destroy-on-close
    >
      <div v-if="spans.length > 0">
        <el-timeline>
          <el-timeline-item
            v-for="span in spans"
            :key="span.spanId"
            :timestamp="formatTime(span.startTime)"
            :type="span.success ? 'primary' : 'danger'"
            placement="top"
          >
            <el-card class="span-card">
              <template #header>
                <div class="span-header">
                  <span>
                    <span class="status-badge" :class="getSpanType(span.type)" size="small">{{ span.type }}</span>
                    {{ span.name }}
                  </span>
                  <span class="span-duration">
                    {{ span.durationMs ? span.durationMs + ' ms' : '' }}
                  </span>
                </div>
              </template>
              <div v-if="span.input" class="span-section">
                <el-text tag="div" class="span-label">输入:</el-text>
                <pre class="span-content">{{ truncate(span.input, 500) }}</pre>
              </div>
              <div v-if="span.output" class="span-section">
                <el-text tag="div" class="span-label">输出:</el-text>
                <pre class="span-content">{{ truncate(span.output, 500) }}</pre>
              </div>
              <div v-if="span.error" class="span-error">
                <el-text type="danger"><b>错误:</b> {{ span.error }}</el-text>
              </div>
            </el-card>
          </el-timeline-item>
        </el-timeline>
      </div>
      <EmptyState v-else title="暂无追踪数据" />
    </el-dialog>
  </PageContainer>
</template>

<style scoped>
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: var(--card-spacing);
  margin-bottom: var(--section-spacing);
}

.filter-card {
  margin-bottom: var(--card-spacing);
}

.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-md);
  align-items: center;
}

.filter-select {
  width: 150px;
}

.filter-input {
  width: 200px;
}

.active-alert {
  margin-bottom: var(--card-spacing);
}

.active-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
  margin-top: var(--space-sm);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: var(--font-size-base);
  font-weight: 600;
  color: var(--text-primary);
}

.value-number {
  font-weight: 600;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}

.table-empty {
  padding: var(--space-xl);
}

.trace-descriptions {
  margin-bottom: 0;
}

/* 追踪详情对话框样式 */
.trace-detail-dialog .el-dialog__body {
  padding: 0;
}

.trace-detail {
  padding: 0;
}

.trace-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 20px 24px;
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border-light);
}

.trace-id-section {
  display: flex;
  align-items: center;
  gap: 12px;
}

.trace-id-breadcrumb {
  font-size: 18px;
  color: var(--text-primary);
}

.trace-id {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  font-family: var(--font-mono);
}

.trace-metrics {
  display: flex;
  gap: 24px;
}

.metric-item {
  text-align: center;
}

.metric-value {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
}

.metric-label {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-top: 2px;
}

.trace-section {
  padding: 16px 24px;
  border-bottom: 1px solid var(--border-light);
}

.trace-section:last-of-type {
  border-bottom: none;
}

.section-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.section-content {
  color: var(--text-primary);
  line-height: 1.6;
}

.goal-text {
  font-size: 14px;
}

.error-section {
  background: var(--color-danger-light);
}

.detail-collapse {
  border: none;
  margin: 0;
}

.detail-collapse .el-collapse-item__header {
  padding: 12px 24px;
  background: var(--bg-secondary);
  font-weight: 500;
  color: var(--text-secondary);
  border: none;
}

.detail-collapse .el-collapse-item__wrap {
  border: none;
}

.detail-collapse .el-collapse-item__content {
  padding: 16px 24px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px 24px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.detail-label {
  font-size: 12px;
  color: var(--text-tertiary);
}

.detail-value {
  font-size: 13px;
  color: var(--text-primary);
}

.detail-value.mono {
  font-family: var(--font-mono);
  font-size: 12px;
}

@media (max-width: 640px) {
  .trace-header {
    flex-direction: column;
    gap: 12px;
  }

  .trace-metrics {
    justify-content: flex-start;
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }
}

.output-pre {
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 200px;
  overflow: auto;
  background: var(--bg-secondary);
  padding: var(--space-md);
  border-radius: var(--radius-md);
  font-size: 13px;
  margin: 0;
}

.span-card {
  margin-bottom: 0;
}

.span-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.span-duration {
  font-size: var(--font-size-sm);
  color: var(--text-tertiary);
}

.span-section {
  margin-bottom: var(--space-md);
}

.span-section:last-child {
  margin-bottom: 0;
}

.span-label {
  font-size: var(--font-size-sm);
  font-weight: 600;
  margin-bottom: var(--space-xs);
}

.span-content {
  background: var(--bg-secondary);
  padding: var(--space-md);
  border-radius: var(--radius-md);
  font-size: var(--font-size-sm);
  max-height: 150px;
  overflow: auto;
  margin: var(--space-xs) 0 0 0;
}

.span-error {
  margin-top: var(--space-md);
}

@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }

  .filter-row {
    flex-direction: column;
    align-items: stretch;
  }

  .filter-select,
  .filter-input {
    width: 100%;
  }
}
</style>