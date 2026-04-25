<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Search, Refresh, Delete, View, Timer, User, Monitor,
  Check, Close, Warning, Loading
} from '@element-plus/icons-vue'
import {
  queryAuditLogs,
  getAuditStats,
  cleanupAuditLogs,
  getExecutionDetail,
  type ExecutionHistoryVO,
  type ExecutionDetailVO,
  type AuditStats
} from '@/api/agent'

// 搜索条件
const searchQuery = ref('')
const agentNameFilter = ref('')
const eventTypeFilter = ref('')
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)

// 数据
const logs = ref<ExecutionHistoryVO[]>([])
const stats = ref<AuditStats | null>(null)
const loading = ref(false)

// 详情弹窗
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailData = ref<ExecutionDetailVO | null>(null)

// 定时刷新
let refreshTimer: ReturnType<typeof setInterval> | null = null

// 事件类型选项
const eventTypeOptions = [
  { label: '全部', value: '' },
  { label: '执行开始', value: 'EXECUTION_START' },
  { label: '执行结束', value: 'EXECUTION_END' },
  { label: '执行错误', value: 'EXECUTION_ERROR' },
  { label: '执行取消', value: 'EXECUTION_CANCEL' },
  { label: '工具调用', value: 'TOOL_CALL' },
  { label: '需要确认', value: 'CONFIRMATION_REQUIRED' },
  { label: '路由决策', value: 'ROUTING_DECISION' }
]

// Agent 名称选项
const agentNameOptions = [
  { label: '全部', value: '' },
  { label: 'RouterAgent', value: 'router' },
  { label: 'OpsAgent', value: 'ops' },
  { label: 'DataAgent', value: 'data' },
  { label: 'PromptAgent', value: 'prompt' },
  { label: 'TestAgent', value: 'test' },
  { label: 'ChatAgent', value: 'chat' }
]

// 状态颜色映射
const statusColors: Record<string, string> = {
  RUNNING: 'warning',
  SUCCESS: 'success',
  FAILED: 'danger',
  CANCELLED: 'info'
}

// 状态图标映射
const statusIcons: Record<string, any> = {
  RUNNING: Loading,
  SUCCESS: Check,
  FAILED: Close,
  CANCELLED: Warning
}

// 加载审计日志
const loadLogs = async () => {
  loading.value = true
  try {
    const result = await queryAuditLogs({
      page: currentPage.value - 1,
      size: pageSize.value,
      userId: searchQuery.value || undefined,
      agentName: agentNameFilter.value || undefined,
      eventType: eventTypeFilter.value || undefined
    })
    logs.value = result.data
    total.value = result.total
  } catch (e: any) {
    ElMessage.error('加载审计日志失败: ' + (e.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

// 加载统计
const loadStats = async () => {
  try {
    stats.value = await getAuditStats(24)
  } catch (e) {
    console.error('加载统计失败:', e)
  }
}

// 刷新数据
const handleRefresh = async () => {
  await Promise.all([loadLogs(), loadStats()])
  ElMessage.success('刷新成功')
}

// 查看详情
const handleViewDetail = async (traceId: string) => {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detailData.value = await getExecutionDetail(traceId)
  } catch (e: any) {
    ElMessage.error('加载详情失败: ' + (e.message || '未知错误'))
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

// 清理过期日志
const handleCleanup = async () => {
  try {
    const { value: days } = await ElMessageBox.prompt(
      '请输入要保留的天数，此时间之前的日志将被永久删除',
      '清理过期日志',
      {
        confirmButtonText: '确认清理',
        cancelButtonText: '取消',
        inputPattern: /^[1-9]\d*$/,
        inputErrorMessage: '请输入正整数',
        inputValue: '30'
      }
    )

    const result = await cleanupAuditLogs(Number(days))
    ElMessage.success(`清理完成，删除了 ${result.deletedCount} 条记录`)
    loadLogs()
    loadStats()
  } catch {
    // 用户取消
  }
}

// 格式化时间
const formatTime = (timestamp: string) => {
  if (!timestamp) return '-'
  return new Date(timestamp).toLocaleString('zh-CN')
}

// 格式化持续时间
const formatDuration = (ms: number) => {
  if (!ms) return '-'
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`
  return `${(ms / 60000).toFixed(1)}min`
}

// 搜索防抖
let searchDebounceTimer: ReturnType<typeof setTimeout> | null = null
const handleSearchChange = () => {
  if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
  searchDebounceTimer = setTimeout(() => {
    currentPage.value = 1
    loadLogs()
  }, 300)
}

// 筛选条件变化
const handleFilterChange = () => {
  currentPage.value = 1
  loadLogs()
}

// 分页变化
const handlePageChange = (page: number) => {
  currentPage.value = page
  loadLogs()
}

const handleSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  loadLogs()
}

// 计算成功率
const successRate = computed(() => {
  if (!stats.value) return 0
  const total = stats.value.successCount + stats.value.errorCount + stats.value.cancelCount
  if (total === 0) return 0
  return Math.round((stats.value.successCount / total) * 100)
})

onMounted(() => {
  loadLogs()
  loadStats()
  // 每 30 秒自动刷新统计
  refreshTimer = setInterval(loadStats, 30000)
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
})
</script>

<template>
  <div class="audit-view">
    <!-- 统计卡片 -->
    <div class="stats-cards" v-if="stats">
      <div class="stat-card">
        <div class="stat-icon total"><el-icon><Timer /></el-icon></div>
        <div class="stat-content">
          <div class="stat-value">{{ stats.totalExecutions }}</div>
          <div class="stat-label">总执行次数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon success"><el-icon><Check /></el-icon></div>
        <div class="stat-content">
          <div class="stat-value">{{ stats.successCount }}</div>
          <div class="stat-label">成功</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon error"><el-icon><Close /></el-icon></div>
        <div class="stat-content">
          <div class="stat-value">{{ stats.errorCount }}</div>
          <div class="stat-label">失败</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon rate">
          <el-progress
            type="circle"
            :percentage="successRate"
            :width="40"
            :stroke-width="4"
            :color="successRate >= 90 ? '#67c23a' : successRate >= 70 ? '#e6a23c' : '#f56c6c'"
          />
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ successRate }}%</div>
          <div class="stat-label">成功率</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon tool"><el-icon><Monitor /></el-icon></div>
        <div class="stat-content">
          <div class="stat-value">{{ stats.toolCalls }}</div>
          <div class="stat-label">工具调用</div>
        </div>
      </div>
    </div>

    <!-- 搜索和筛选 -->
    <div class="filter-bar">
      <el-input
        v-model="searchQuery"
        placeholder="搜索用户 ID..."
        :prefix-icon="Search"
        clearable
        style="width: 200px"
        @input="handleSearchChange"
      />
      <el-select
        v-model="agentNameFilter"
        placeholder="Agent 名称"
        clearable
        style="width: 140px"
        @change="handleFilterChange"
      >
        <el-option
          v-for="item in agentNameOptions"
          :key="item.value"
          :label="item.label"
          :value="item.value"
        />
      </el-select>
      <el-select
        v-model="eventTypeFilter"
        placeholder="事件类型"
        clearable
        style="width: 140px"
        @change="handleFilterChange"
      >
        <el-option
          v-for="item in eventTypeOptions"
          :key="item.value"
          :label="item.label"
          :value="item.value"
        />
      </el-select>
      <el-button :icon="Refresh" @click="handleRefresh">刷新</el-button>
      <el-button :icon="Delete" type="danger" plain @click="handleCleanup">清理</el-button>
    </div>

    <!-- 日志表格 -->
    <el-table
      :data="logs"
      v-loading="loading"
      stripe
      style="width: 100%"
    >
      <el-table-column prop="timestamp" label="时间" width="180">
        <template #default="{ row }">
          {{ formatTime(row.timestamp) }}
        </template>
      </el-table-column>
      <el-table-column prop="traceId" label="Trace ID" width="280">
        <template #default="{ row }">
          <el-text type="primary" class="trace-id" @click="handleViewDetail(row.traceId)">
            {{ row.traceId }}
          </el-text>
        </template>
      </el-table-column>
      <el-table-column prop="userId" label="用户" width="120">
        <template #default="{ row }">
          <div class="user-cell">
            <el-icon><User /></el-icon>
            <span>{{ row.userId || '-' }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="agentName" label="Agent" width="100">
        <template #default="{ row }">
          <el-tag size="small" effect="plain">{{ row.agentName || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="eventType" label="事件类型" width="140">
        <template #default="{ row }">
          <el-tag size="small">{{ row.eventType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusColors[row.status]" size="small">
            <el-icon class="status-icon"><component :is="statusIcons[row.status]" /></el-icon>
            {{ row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="clientIp" label="IP" width="130" />
      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ row }">
          <el-button
            :icon="View"
            circle
            size="small"
            @click="handleViewDetail(row.traceId)"
          />
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>

    <!-- 详情弹窗 -->
    <el-dialog
      v-model="detailVisible"
      title="执行详情"
      width="70%"
      destroy-on-close
    >
      <div v-loading="detailLoading">
        <template v-if="detailData">
          <!-- 基本信息 -->
          <el-descriptions :column="4" border class="detail-info">
            <el-descriptions-item label="Trace ID">{{ detailData.traceId }}</el-descriptions-item>
            <el-descriptions-item label="用户">{{ detailData.userId }}</el-descriptions-item>
            <el-descriptions-item label="Agent">{{ detailData.agentName }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="statusColors[detailData.status]">
                {{ detailData.status }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="开始时间">{{ formatTime(detailData.startTime) }}</el-descriptions-item>
            <el-descriptions-item label="结束时间">{{ formatTime(detailData.endTime) }}</el-descriptions-item>
            <el-descriptions-item label="持续时间">{{ formatDuration(detailData.durationMs) }}</el-descriptions-item>
            <el-descriptions-item label="步骤数">{{ detailData.totalSteps }}</el-descriptions-item>
          </el-descriptions>

          <!-- 执行步骤 -->
          <h4 class="steps-title">执行步骤</h4>
          <el-timeline>
            <el-timeline-item
              v-for="(step, index) in detailData.steps"
              :key="index"
              :timestamp="formatTime(step.timestamp)"
              placement="top"
            >
              <el-card>
                <template #header>
                  <div class="step-header">
                    <el-tag size="small">{{ step.eventType }}</el-tag>
                    <span v-if="step.agentName" class="step-agent">{{ step.agentName }}</span>
                  </div>
                </template>
                <div v-if="step.details && Object.keys(step.details).length > 0" class="step-details">
                  <pre>{{ JSON.stringify(step.details, null, 2) }}</pre>
                </div>
                <div v-else class="step-empty">无详细信息</div>
              </el-card>
            </el-timeline-item>
          </el-timeline>
        </template>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.audit-view {
  padding: 20px;
}

.stats-cards {
  display: flex;
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  flex: 1;
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}

.stat-icon.total {
  background: #e6f4ff;
  color: #1677ff;
}

.stat-icon.success {
  background: #f6ffed;
  color: #52c41a;
}

.stat-icon.error {
  background: #fff2f0;
  color: #ff4d4f;
}

.stat-icon.rate {
  background: #f5f5f5;
}

.stat-icon.tool {
  background: #fff7e6;
  color: #fa8c16;
}

.stat-content {
  flex: 1;
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
  color: #1f2937;
}

.stat-label {
  font-size: 12px;
  color: #6b7280;
  margin-top: 4px;
}

.filter-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.trace-id {
  cursor: pointer;
  font-family: monospace;
}

.trace-id:hover {
  text-decoration: underline;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 4px;
}

.status-icon {
  margin-right: 2px;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.detail-info {
  margin-bottom: 20px;
}

.steps-title {
  margin: 16px 0;
  font-size: 14px;
  color: #374151;
}

.step-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.step-agent {
  font-size: 12px;
  color: #6b7280;
}

.step-details {
  max-height: 200px;
  overflow: auto;
}

.step-details pre {
  margin: 0;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}

.step-empty {
  color: #9ca3af;
  font-size: 12px;
}
</style>
