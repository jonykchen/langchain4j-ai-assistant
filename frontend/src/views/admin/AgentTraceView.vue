<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Timer,
  Cpu,
  Document,
  Tools,
  QuestionFilled,
  Operation,
  View,
  Promotion,
  CaretRight,
  CopyDocument,
  Check,
  Close,
  Minus,
  Plus
} from '@element-plus/icons-vue'
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
const selectedSpanId = ref<string | null>(null)
const expandedIterations = ref<Set<number>>(new Set([0])) // 默认展开第一个迭代
const detailTab = ref('input')

// 步骤时间线滚动容器
const timelineContainer = ref<HTMLElement | null>(null)

const filters = reactive({
  status: '',
  agentType: '',
  userId: ''
})

// 将 spans 按迭代分组
const groupedSpans = computed(() => {
  if (!spans.value.length) return []

  // 按 startTime 排序
  const sortedSpans = [...spans.value].sort((a, b) =>
    new Date(a.startTime).getTime() - new Date(b.startTime).getTime()
  )

  // 识别迭代边界（LLM_CALL 通常是一个迭代的开始）
  const groups: { iteration: number; spans: AgentSpan[]; startTime: Date; endTime: Date; duration: number }[] = []
  let currentIteration = -1

  for (const span of sortedSpans) {
    // LLM_CALL 或 THOUGHT 作为新迭代的开始
    if (span.type === 'LLM_CALL' || (span.type === 'THOUGHT' && !groups.some(g => g.spans.some(s => s.type === 'LLM_CALL')))) {
      currentIteration++
      groups.push({
        iteration: currentIteration,
        spans: [],
        startTime: new Date(span.startTime),
        endTime: new Date(span.endTime),
        duration: span.durationMs || 0
      })
    }

    if (groups.length === 0) {
      // 第一个步骤
      currentIteration = 0
      groups.push({
        iteration: 0,
        spans: [],
        startTime: new Date(span.startTime),
        endTime: new Date(span.endTime),
        duration: span.durationMs || 0
      })
    }

    const currentGroup = groups[groups.length - 1]
    currentGroup.spans.push(span)

    // 更新迭代的时间范围
    const spanStart = new Date(span.startTime)
    const spanEnd = new Date(span.endTime)
    if (spanStart < currentGroup.startTime) currentGroup.startTime = spanStart
    if (spanEnd > currentGroup.endTime) currentGroup.endTime = spanEnd
    currentGroup.duration += span.durationMs || 0
  }

  return groups
})

// 选中的 Span 详情
const selectedSpan = computed(() => {
  if (!selectedSpanId.value) return null
  return spans.value.find(s => s.spanId === selectedSpanId.value)
})

// 计算时间轴的总时长
const totalDuration = computed(() => {
  if (!spans.value.length) return 0
  const sortedSpans = [...spans.value].sort((a, b) =>
    new Date(a.startTime).getTime() - new Date(b.startTime).getTime()
  )
  const start = new Date(sortedSpans[0]?.startTime).getTime()
  const end = new Date(sortedSpans[sortedSpans.length - 1]?.endTime).getTime()
  return end - start
})

// 计算每个 span 在时间轴上的位置百分比
const getSpanPosition = (span: AgentSpan) => {
  if (!spans.value.length || totalDuration.value === 0) return { left: 0, width: 0 }

  const sortedSpans = [...spans.value].sort((a, b) =>
    new Date(a.startTime).getTime() - new Date(b.startTime).getTime()
  )
  const baseTime = new Date(sortedSpans[0]?.startTime).getTime()

  const spanStart = new Date(span.startTime).getTime()
  const spanEnd = new Date(span.endTime).getTime()

  const left = ((spanStart - baseTime) / totalDuration.value) * 100
  const width = Math.max(((spanEnd - spanStart) / totalDuration.value) * 100, 1)

  return { left: Math.max(left, 0), width: Math.min(width, 100 - left) }
}

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

const showSpanView = async (trace: AgentTrace) => {
  selectedTrace.value = trace
  selectedSpanId.value = null
  expandedIterations.value = new Set([0])
  try {
    const result = await observabilityApi.getTraceSpans(trace.traceId)
    // 兼容后端返回分页对象 {data: [...], total, ...} 或直接返回数组的情况
    const resultData = result as unknown as { data?: AgentSpan[] }
    spans.value = Array.isArray(result) ? result : Array.isArray(resultData?.data) ? resultData.data : []
    spanViewVisible.value = true

    // 默认选中第一个 span
    await nextTick()
    if (spans.value.length > 0) {
      selectedSpanId.value = spans.value[0].spanId
    }
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

// 获取步骤类型配置（图标、颜色、标签）
const getSpanTypeConfig = (type: string) => {
  const configs: Record<string, { icon: typeof QuestionFilled; color: string; bgColor: string; label: string; description: string }> = {
    'THOUGHT': {
      icon: Document,
      color: '#60a5fa',
      bgColor: 'rgba(96, 165, 250, 0.1)',
      label: '思考',
      description: 'Agent 推理思考过程'
    },
    'LLM_CALL': {
      icon: Cpu,
      color: '#34d399',
      bgColor: 'rgba(52, 211, 153, 0.1)',
      label: 'LLM 调用',
      description: '大语言模型响应生成'
    },
    'ACTION': {
      icon: Promotion,
      color: '#fbbf24',
      bgColor: 'rgba(251, 191, 36, 0.1)',
      label: '行动',
      description: 'Agent 决定的下一步行动'
    },
    'TOOL_EXECUTE': {
      icon: Tools,
      color: '#f472b6',
      bgColor: 'rgba(244, 114, 182, 0.1)',
      label: '工具执行',
      description: '调用外部工具完成任务'
    },
    'OBSERVATION': {
      icon: View,
      color: '#a78bfa',
      bgColor: 'rgba(167, 139, 250, 0.1)',
      label: '观察',
      description: '工具执行结果观察'
    }
  }
  return configs[type] || {
    icon: Operation,
    color: '#9ca3af',
    bgColor: 'rgba(156, 163, 175, 0.1)',
    label: type,
    description: '未知步骤类型'
  }
}

// 切换迭代展开/折叠
const toggleIteration = (iteration: number) => {
  if (expandedIterations.value.has(iteration)) {
    expandedIterations.value.delete(iteration)
  } else {
    expandedIterations.value.add(iteration)
  }
  expandedIterations.value = new Set(expandedIterations.value)
}

// 展开/折叠所有迭代
const toggleAllIterations = (expand: boolean) => {
  if (expand) {
    expandedIterations.value = new Set(groupedSpans.value.map(g => g.iteration))
  } else {
    expandedIterations.value = new Set()
  }
}

// 选择 Span
const selectSpan = (span: AgentSpan) => {
  selectedSpanId.value = span.spanId
}

// 复制内容到剪贴板
const copyToClipboard = async (text: string) => {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败')
  }
}

const formatTime = (time: string) => {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}

const formatTimeShort = (time: string) => {
  if (!time) return '-'
  return new Date(time).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
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

// 格式化 JSON 内容（带语法高亮）
const formatJsonContent = (content: string) => {
  if (!content) return ''
  try {
    const parsed = JSON.parse(content)
    return JSON.stringify(parsed, null, 2)
  } catch {
    return content
  }
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

    <!-- Span 可视化对话框 - 生产级设计 -->
    <el-dialog
      v-model="spanViewVisible"
      :title="`执行追踪 - ${selectedTrace?.agentType || ''}`"
      width="95%"
      top="2vh"
      destroy-on-close
      class="span-view-dialog"
      :close-on-click-modal="false"
    >
      <div v-if="spans.length > 0" class="span-view-container">
        <!-- 顶部概览区 -->
        <div class="trace-overview">
          <div class="overview-left">
            <div class="overview-title">{{ selectedTrace?.goal || 'Agent 执行追踪' }}</div>
            <div class="overview-meta">
              <el-tag :type="getStatusType(selectedTrace?.status || '')" size="small" effect="dark">
                <el-icon class="mr-1"><Timer /></el-icon>
                {{ selectedTrace?.status }}
              </el-tag>
              <span class="meta-item">
                <el-icon><Cpu /></el-icon>
                {{ selectedTrace?.iterations }} 次迭代
              </span>
              <span class="meta-item">
                <el-icon><Timer /></el-icon>
                {{ formatDuration(selectedTrace?.executionTimeMs) }}
              </span>
              <span class="meta-item">
                <el-icon><Document /></el-icon>
                {{ formatTokens(selectedTrace?.tokenUsage?.totalTokens) }} Tokens
              </span>
            </div>
          </div>
          <div class="overview-actions">
            <el-button size="small" @click="toggleAllIterations(true)">
              <el-icon><Plus /></el-icon>
              全部展开
            </el-button>
            <el-button size="small" @click="toggleAllIterations(false)">
              <el-icon><Minus /></el-icon>
              全部折叠
            </el-button>
          </div>
        </div>

        <!-- 时间轴概览（甘特图风格） -->
        <div class="timeline-overview">
          <div class="timeline-header">
            <span class="timeline-title">执行时间线</span>
            <span class="timeline-total">总耗时: {{ formatDuration(totalDuration || selectedTrace?.executionTimeMs) }}</span>
          </div>
          <div class="timeline-bars">
            <div
              v-for="group in groupedSpans"
              :key="group.iteration"
              class="timeline-iteration"
              @click="toggleIteration(group.iteration)"
            >
              <span class="iteration-label">迭代 {{ group.iteration + 1 }}</span>
              <div class="iteration-bar">
                <div
                  v-for="span in group.spans"
                  :key="span.spanId"
                  class="span-segment"
                  :style="{
                    left: getSpanPosition(span).left + '%',
                    width: getSpanPosition(span).width + '%',
                    backgroundColor: getSpanTypeConfig(span.type).color
                  }"
                  :title="`${getSpanTypeConfig(span.type).label}: ${span.name}`"
                  @click.stop="selectSpan(span)"
                />
              </div>
            </div>
          </div>
          <div class="timeline-legend">
            <span v-for="(config, type) in { THOUGHT: getSpanTypeConfig('THOUGHT'), LLM_CALL: getSpanTypeConfig('LLM_CALL'), TOOL_EXECUTE: getSpanTypeConfig('TOOL_EXECUTE'), OBSERVATION: getSpanTypeConfig('OBSERVATION') }" :key="type" class="legend-item">
              <span class="legend-dot" :style="{ backgroundColor: config.color }"></span>
              {{ config.label }}
            </span>
          </div>
        </div>

        <!-- 主内容区：左侧步骤树 + 右侧详情 -->
        <div class="span-content-area">
          <!-- 左侧：步骤树 -->
          <div class="step-tree-panel" ref="timelineContainer">
            <div class="panel-header">
              <span class="panel-title">执行步骤</span>
              <span class="step-count">{{ spans.length }} 个步骤</span>
            </div>

            <div class="step-groups">
              <div
                v-for="group in groupedSpans"
                :key="group.iteration"
                class="step-group"
              >
                <!-- 迭代头部 -->
                <div
                  class="group-header"
                  :class="{ active: expandedIterations.has(group.iteration) }"
                  @click="toggleIteration(group.iteration)"
                >
                  <el-icon class="expand-icon" :class="{ expanded: expandedIterations.has(group.iteration) }">
                    <CaretRight />
                  </el-icon>
                  <div class="group-info">
                    <span class="group-title">迭代 {{ group.iteration + 1 }}</span>
                    <span class="group-meta">
                      {{ group.spans.length }} 步骤 · {{ formatDuration(group.duration) }}
                    </span>
                  </div>
                  <el-tag size="small" effect="plain">{{ group.spans.length }}</el-tag>
                </div>

                <!-- 迭代内的步骤 -->
                <transition name="expand">
                  <div v-if="expandedIterations.has(group.iteration)" class="group-steps">
                    <div
                      v-for="span in group.spans"
                      :key="span.spanId"
                      class="step-item"
                      :class="{ selected: selectedSpanId === span.spanId, failed: !span.success }"
                      @click="selectSpan(span)"
                    >
                      <div class="step-connector">
                        <div class="connector-line"></div>
                        <div class="connector-dot" :style="{ backgroundColor: getSpanTypeConfig(span.type).color }"></div>
                      </div>
                      <div class="step-content">
                        <div class="step-header">
                          <span class="step-type" :style="{ color: getSpanTypeConfig(span.type).color }">
                            <el-icon><component :is="getSpanTypeConfig(span.type).icon" /></el-icon>
                            {{ getSpanTypeConfig(span.type).label }}
                          </span>
                          <span class="step-time">{{ formatDuration(span.durationMs) }}</span>
                        </div>
                        <div class="step-name">{{ span.name }}</div>
                        <el-icon v-if="!span.success" class="step-error-icon"><Close /></el-icon>
                        <el-icon v-else-if="selectedSpanId === span.spanId" class="step-check-icon"><Check /></el-icon>
                      </div>
                    </div>
                  </div>
                </transition>
              </div>
            </div>
          </div>

          <!-- 右侧：步骤详情 -->
          <div class="step-detail-panel">
            <template v-if="selectedSpan">
              <div class="detail-header">
                <div class="detail-title-row">
                  <span class="detail-type-badge" :style="{ backgroundColor: getSpanTypeConfig(selectedSpan.type).bgColor, color: getSpanTypeConfig(selectedSpan.type).color }">
                    <el-icon><component :is="getSpanTypeConfig(selectedSpan.type).icon" /></el-icon>
                    {{ getSpanTypeConfig(selectedSpan.type).label }}
                  </span>
                  <el-tag :type="selectedSpan.success ? 'success' : 'danger'" size="small">
                    {{ selectedSpan.success ? '成功' : '失败' }}
                  </el-tag>
                </div>
                <h3 class="detail-title">{{ selectedSpan.name }}</h3>
                <p class="detail-description">{{ getSpanTypeConfig(selectedSpan.type).description }}</p>
              </div>

              <div class="detail-metrics">
                <div class="metric-card">
                  <span class="metric-value">{{ formatTimeShort(selectedSpan.startTime) }}</span>
                  <span class="metric-label">开始时间</span>
                </div>
                <div class="metric-card">
                  <span class="metric-value">{{ formatDuration(selectedSpan.durationMs) }}</span>
                  <span class="metric-label">执行耗时</span>
                </div>
                <div class="metric-card">
                  <span class="metric-value">{{ selectedSpan.promptTokens || '-' }}</span>
                  <span class="metric-label">Prompt Tokens</span>
                </div>
                <div class="metric-card">
                  <span class="metric-value">{{ selectedSpan.completionTokens || '-' }}</span>
                  <span class="metric-label">Completion Tokens</span>
                </div>
              </div>

              <div class="detail-tabs">
                <el-tabs v-model="detailTab">
                  <el-tab-pane label="输入参数" name="input">
                    <div v-if="selectedSpan.input" class="code-block">
                      <div class="code-toolbar">
                        <span class="code-label">输入内容</span>
                        <el-button size="small" text @click="copyToClipboard(selectedSpan.input)">
                          <el-icon><CopyDocument /></el-icon>
                          复制
                        </el-button>
                      </div>
                      <pre class="code-content"><code>{{ formatJsonContent(selectedSpan.input) }}</code></pre>
                    </div>
                    <EmptyState v-else title="无输入参数" description="此步骤没有输入数据" size="small" />
                  </el-tab-pane>

                  <el-tab-pane label="输出结果" name="output">
                    <div v-if="selectedSpan.output" class="code-block">
                      <div class="code-toolbar">
                        <span class="code-label">输出内容</span>
                        <el-button size="small" text @click="copyToClipboard(selectedSpan.output)">
                          <el-icon><CopyDocument /></el-icon>
                          复制
                        </el-button>
                      </div>
                      <pre class="code-content"><code>{{ formatJsonContent(selectedSpan.output) }}</code></pre>
                    </div>
                    <EmptyState v-else title="无输出结果" description="此步骤没有产生输出数据" size="small" />
                  </el-tab-pane>

                  <el-tab-pane v-if="selectedSpan.error" label="错误信息" name="error">
                    <div class="error-block">
                      <el-alert type="error" :closable="false" show-icon>
                        <template #title>
                          <span class="error-title">执行失败</span>
                        </template>
                        <div class="error-message">{{ selectedSpan.error }}</div>
                      </el-alert>
                    </div>
                  </el-tab-pane>

                  <el-tab-pane v-if="selectedSpan.attributes" label="属性" name="attributes">
                    <div class="attributes-grid">
                      <div v-for="(value, key) in selectedSpan.attributes" :key="key" class="attribute-item">
                        <span class="attribute-key">{{ key }}</span>
                        <span class="attribute-value">{{ JSON.stringify(value) }}</span>
                      </div>
                    </div>
                  </el-tab-pane>
                </el-tabs>
              </div>
            </template>

            <div v-else class="no-selection">
              <el-icon class="empty-icon"><Operation /></el-icon>
              <p class="empty-text">在左侧选择一个步骤查看详情</p>
            </div>
          </div>
        </div>
      </div>

      <EmptyState v-else title="暂无追踪数据" description="该追踪没有记录任何执行步骤" />

      <template #footer>
        <el-button @click="spanViewVisible = false">关闭</el-button>
        <el-button type="primary" @click="selectedTrace && evaluateTrace(selectedTrace)" :loading="false">
          执行评测
        </el-button>
      </template>
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

/* ==================== Span View Dialog 样式 ==================== */

.span-view-dialog {
  :deep(.el-dialog__body) {
    padding: 0;
    max-height: 85vh;
    overflow: hidden;
  }

  :deep(.el-dialog__header) {
    padding: 16px 24px;
    border-bottom: 1px solid var(--border-light);
    margin-right: 0;
  }
}

.span-view-container {
  display: flex;
  flex-direction: column;
  height: calc(85vh - 120px);
  overflow: hidden;
}

/* 顶部概览区 */
.trace-overview {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 16px 24px;
  background: linear-gradient(135deg, rgba(64, 158, 255, 0.05), rgba(103, 194, 58, 0.05));
  border-bottom: 1px solid var(--border-light);
  flex-shrink: 0;
}

.overview-left {
  flex: 1;
  min-width: 0;
}

.overview-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 8px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 600px;
}

.overview-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: center;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--text-secondary);
}

.overview-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

/* 时间线概览 */
.timeline-overview {
  padding: 16px 24px;
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border-light);
  flex-shrink: 0;
}

.timeline-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.timeline-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.timeline-total {
  font-size: 12px;
  color: var(--text-tertiary);
}

.timeline-bars {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.timeline-iteration {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  padding: 4px 0;
}

.iteration-label {
  font-size: 11px;
  color: var(--text-tertiary);
  width: 50px;
  flex-shrink: 0;
}

.iteration-bar {
  flex: 1;
  height: 16px;
  background: var(--bg-primary);
  border-radius: 4px;
  position: relative;
  overflow: hidden;
}

.span-segment {
  position: absolute;
  top: 2px;
  bottom: 2px;
  border-radius: 2px;
  cursor: pointer;
  transition: opacity 0.2s;
}

.span-segment:hover {
  opacity: 0.8;
}

.timeline-legend {
  display: flex;
  gap: 16px;
  margin-top: 8px;
  justify-content: center;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--text-tertiary);
}

.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

/* 主内容区 */
.span-content-area {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

/* 左侧步骤树 */
.step-tree-panel {
  width: 320px;
  min-width: 280px;
  border-right: 1px solid var(--border-light);
  display: flex;
  flex-direction: column;
  background: var(--bg-primary);
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-light);
  background: var(--bg-secondary);
  flex-shrink: 0;
}

.panel-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.step-count {
  font-size: 12px;
  color: var(--text-tertiary);
}

.step-groups {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.step-group {
  margin-bottom: 4px;
}

.group-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px 8px 16px;
  cursor: pointer;
  transition: background-color 0.2s;
  border-radius: 4px;
  margin: 0 8px;
}

.group-header:hover {
  background: var(--bg-tertiary);
}

.group-header.active {
  background: rgba(64, 158, 255, 0.1);
}

.expand-icon {
  transition: transform 0.2s;
  color: var(--text-tertiary);
}

.expand-icon.expanded {
  transform: rotate(90deg);
}

.group-info {
  flex: 1;
  min-width: 0;
}

.group-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
}

.group-meta {
  font-size: 11px;
  color: var(--text-tertiary);
  display: block;
  margin-top: 2px;
}

.group-steps {
  padding-left: 20px;
}

.step-item {
  display: flex;
  align-items: flex-start;
  padding: 8px 12px 8px 0;
  cursor: pointer;
  transition: background-color 0.15s;
  border-radius: 4px;
  margin: 2px 8px 2px 0;
  position: relative;
}

.step-item:hover {
  background: var(--bg-tertiary);
}

.step-item.selected {
  background: rgba(64, 158, 255, 0.12);
}

.step-item.failed .step-name {
  color: var(--color-danger);
}

.step-connector {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-right: 8px;
  padding-top: 4px;
}

.connector-line {
  width: 1px;
  flex: 1;
  background: var(--border-light);
}

.connector-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  border: 2px solid var(--bg-primary);
  flex-shrink: 0;
}

.step-content {
  flex: 1;
  min-width: 0;
  position: relative;
}

.step-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 2px;
}

.step-type {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  font-weight: 500;
}

.step-time {
  font-size: 10px;
  color: var(--text-tertiary);
  font-variant-numeric: tabular-nums;
}

.step-name {
  font-size: 12px;
  color: var(--text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.step-error-icon {
  position: absolute;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  color: var(--color-danger);
}

.step-check-icon {
  position: absolute;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  color: var(--color-primary);
}

/* 右侧详情面板 */
.step-detail-panel {
  flex: 1;
  min-width: 400px;
  display: flex;
  flex-direction: column;
  background: var(--bg-primary);
  overflow: hidden;
}

.detail-header {
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-light);
  flex-shrink: 0;
}

.detail-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.detail-type-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}

.detail-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 4px 0;
}

.detail-description {
  font-size: 12px;
  color: var(--text-tertiary);
  margin: 0;
}

.detail-metrics {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  padding: 12px 20px;
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border-light);
  flex-shrink: 0;
}

.mr-1 {
  margin-right: 4px;
}

.metric-card {
  text-align: center;
  padding: 8px;
  background: var(--bg-primary);
  border-radius: 6px;
}

.metric-card .metric-value {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 2px;
}

.metric-card .metric-label {
  font-size: 10px;
  color: var(--text-tertiary);
}

.detail-tabs {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.detail-tabs :deep(.el-tabs__header) {
  margin: 0;
  padding: 0 20px;
  background: var(--bg-secondary);
}

.detail-tabs :deep(.el-tabs__content) {
  flex: 1;
  overflow: auto;
  padding: 16px 20px;
}

.detail-tabs :deep(.el-tab-pane) {
  height: 100%;
}

.code-block {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.code-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: var(--bg-tertiary);
  border-radius: 6px 6px 0 0;
  border: 1px solid var(--border-light);
  border-bottom: none;
}

.code-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-secondary);
}

.code-content {
  flex: 1;
  margin: 0;
  padding: 12px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-light);
  border-radius: 0 0 6px 6px;
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1.5;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--text-primary);
}

.error-block {
  padding: 0;
}

.error-title {
  font-weight: 600;
}

.error-message {
  font-size: 12px;
  margin-top: 8px;
}

.attributes-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px;
}

.attribute-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.attribute-key {
  font-size: 11px;
  color: var(--text-tertiary);
  font-weight: 500;
}

.attribute-value {
  font-size: 12px;
  color: var(--text-primary);
  font-family: var(--font-mono);
  word-break: break-all;
}

.no-selection {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-tertiary);
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
  color: var(--text-quaternary);
}

.empty-text {
  font-size: 14px;
  margin: 0;
}

/* 过渡动画 */
.expand-enter-active,
.expand-leave-active {
  transition: all 0.2s ease-out;
  overflow: hidden;
}

.expand-enter-from,
.expand-leave-to {
  opacity: 0;
  max-height: 0;
}

.expand-enter-to,
.expand-leave-from {
  opacity: 1;
  max-height: 500px;
}

/* ==================== 追踪详情对话框样式 ==================== */

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

.detail-collapse :deep(.el-collapse-item__header) {
  padding: 12px 24px;
  background: var(--bg-secondary);
  font-weight: 500;
  color: var(--text-secondary);
  border: none;
}

.detail-collapse :deep(.el-collapse-item__wrap) {
  border: none;
}

.detail-collapse :deep(.el-collapse-item__content) {
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

@media (max-width: 1024px) {
  .span-content-area {
    flex-direction: column;
  }

  .step-tree-panel {
    width: 100%;
    max-height: 200px;
    border-right: none;
    border-bottom: 1px solid var(--border-light);
  }

  .step-detail-panel {
    min-width: 0;
  }

  .detail-metrics {
    grid-template-columns: repeat(2, 1fr);
  }
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

  .trace-overview {
    flex-direction: column;
    gap: 12px;
  }

  .overview-actions {
    width: 100%;
    justify-content: flex-start;
  }
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
</style>