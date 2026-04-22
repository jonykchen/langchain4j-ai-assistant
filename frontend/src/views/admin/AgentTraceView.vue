<template>
  <div class="agent-trace-view">
    <!-- 统计卡片 -->
    <el-row :gutter="20" class="mb-4">
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="总追踪数" :value="statistics?.total || 0" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="已完成" :value="statistics?.completed || 0">
            <template #suffix>
              <el-tag type="success" size="small" class="ml-2">
                {{ formatPercent(statistics?.successRate) }}
              </el-tag>
            </template>
          </el-statistic>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="活跃中" :value="statistics?.active || 0" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="失败数" :value="statistics?.failed || 0">
            <template #suffix>
              <el-tag type="danger" size="small" class="ml-2">失败</el-tag>
            </template>
          </el-statistic>
        </el-card>
      </el-col>
    </el-row>

    <!-- 筛选栏 -->
    <el-card class="mb-4">
      <el-row :gutter="20">
        <el-col :span="6">
          <el-select v-model="filters.status" placeholder="执行状态" clearable>
            <el-option label="全部" value="" />
            <el-option label="运行中" value="RUNNING" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="失败" value="FAILED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-col>
        <el-col :span="6">
          <el-select v-model="filters.agentType" placeholder="Agent 类型" clearable>
            <el-option label="全部" value="" />
            <el-option label="ReAct" value="REACT" />
            <el-option label="Plan-Execute" value="PLAN_EXECUTE" />
          </el-select>
        </el-col>
        <el-col :span="6">
          <el-input v-model="filters.userId" placeholder="用户 ID" clearable />
        </el-col>
        <el-col :span="6">
          <el-button type="primary" @click="loadTraces">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- 活跃追踪提示 -->
    <el-alert
      v-if="activeTraces.length > 0"
      :title="`有 ${activeTraces.length} 个 Agent 正在执行`"
      type="info"
      :closable="false"
      class="mb-4"
    >
      <template #default>
        <el-tag v-for="trace in activeTraces" :key="trace.traceId" class="mr-2">
          {{ trace.agentType }} - {{ trace.traceId.slice(0, 8) }}
        </el-tag>
      </template>
    </el-alert>

    <!-- 追踪列表 -->
    <el-card>
      <el-table :data="traces" v-loading="loading" stripe>
        <el-table-column prop="traceId" label="Trace ID" width="180">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDetail(row)">
              {{ row.traceId.slice(0, 8) }}...
            </el-button>
          </template>
        </el-table-column>
        <el-table-column prop="agentType" label="类型" width="120">
          <template #default="{ row }">
            <el-tag :type="row.agentType === 'REACT' ? 'success' : 'warning'">
              {{ row.agentType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="goal" label="任务目标" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="iterations" label="迭代次数" width="100" />
        <el-table-column prop="executionTimeMs" label="耗时(ms)" width="100">
          <template #default="{ row }">
            {{ row.executionTimeMs || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="tokenUsage.totalTokens" label="Tokens" width="100">
          <template #default="{ row }">
            {{ row.tokenUsage?.totalTokens || '-' }}
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
    </el-card>

    <!-- 追踪详情对话框 -->
    <el-dialog
      v-model="detailVisible"
      :title="`追踪详情 - ${selectedTrace?.traceId?.slice(0, 8)}`"
      width="80%"
      destroy-on-close
    >
      <el-descriptions :column="2" border v-if="selectedTrace">
        <el-descriptions-item label="Trace ID">{{ selectedTrace.traceId }}</el-descriptions-item>
        <el-descriptions-item label="会话 ID">{{ selectedTrace.sessionId }}</el-descriptions-item>
        <el-descriptions-item label="用户 ID">{{ selectedTrace.userId }}</el-descriptions-item>
        <el-descriptions-item label="Agent 类型">{{ selectedTrace.agentType }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusType(selectedTrace.status)">{{ selectedTrace.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="迭代次数">{{ selectedTrace.iterations }}</el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ formatTime(selectedTrace.startTime) }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{ formatTime(selectedTrace.endTime) }}</el-descriptions-item>
        <el-descriptions-item label="执行耗时">{{ selectedTrace.executionTimeMs }} ms</el-descriptions-item>
        <el-descriptions-item label="Token 使用">
          Prompt: {{ selectedTrace.tokenUsage?.promptTokens || 0 }} /
          Completion: {{ selectedTrace.tokenUsage?.completionTokens || 0 }}
        </el-descriptions-item>
        <el-descriptions-item label="任务目标" :span="2">
          {{ selectedTrace.goal }}
        </el-descriptions-item>
        <el-descriptions-item label="最终输出" :span="2" v-if="selectedTrace.finalOutput">
          <pre class="output-pre">{{ selectedTrace.finalOutput }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="错误信息" :span="2" v-if="selectedTrace.errorMessage">
          <el-text type="danger">{{ selectedTrace.errorMessage }}</el-text>
        </el-descriptions-item>
      </el-descriptions>
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
            <el-card>
              <template #header>
                <div class="flex justify-between items-center">
                  <span>
                    <el-tag :type="getSpanType(span.type)" size="small" class="mr-2">
                      {{ span.type }}
                    </el-tag>
                    {{ span.name }}
                  </span>
                  <span class="text-gray-500">
                    {{ span.durationMs ? span.durationMs + ' ms' : '' }}
                  </span>
                </div>
              </template>
              <div v-if="span.input" class="mb-2">
                <el-text tag="div" class="mb-1"><b>输入:</b></el-text>
                <pre class="text-sm bg-gray-50 p-2 rounded overflow-auto max-h-40">{{ truncate(span.input, 500) }}</pre>
              </div>
              <div v-if="span.output" class="mb-2">
                <el-text tag="div" class="mb-1"><b>输出:</b></el-text>
                <pre class="text-sm bg-gray-50 p-2 rounded overflow-auto max-h-40">{{ truncate(span.output, 500) }}</pre>
              </div>
              <div v-if="span.error" class="text-red-500">
                <el-text type="danger"><b>错误:</b> {{ span.error }}</el-text>
              </div>
            </el-card>
          </el-timeline-item>
        </el-timeline>
      </div>
      <el-empty v-else description="暂无追踪数据" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { observabilityApi, type AgentTrace, type AgentSpan, type TraceStatistics } from '@/api/observability'

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
    traces.value = Array.isArray(result) ? result : Array.isArray((result as any)?.data) ? (result as any).data : []
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
    spans.value = Array.isArray(result) ? result : Array.isArray((result as any)?.data) ? (result as any).data : []
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

<style scoped>
.output-pre {
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 200px;
  overflow: auto;
  background: #f5f7fa;
  padding: 10px;
  border-radius: 4px;
  font-size: 13px;
}

.mb-2 {
  margin-bottom: 8px;
}

.mr-2 {
  margin-right: 8px;
}

.ml-2 {
  margin-left: 8px;
}
</style>
