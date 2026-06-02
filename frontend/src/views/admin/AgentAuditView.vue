<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Search,
  Refresh,
  Delete,
  View,
  Timer,
  User,
  Monitor,
  Check,
  Close,
  Warning,
  Loading,
} from '@element-plus/icons-vue'
import {
  queryAuditLogs,
  getAuditStats,
  cleanupAuditLogs,
  getExecutionDetail,
  type ExecutionHistoryVO,
  type ExecutionDetailVO,
  type AuditStats,
} from '@/api/agent'
import PathBreadcrumb from '@/components/PathBreadcrumb.vue'

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
  { label: '路由决策', value: 'ROUTING_DECISION' },
]

// Agent 名称选项
const agentNameOptions = [
  { label: '全部', value: '' },
  { label: 'RouterAgent', value: 'router' },
  { label: 'OpsAgent', value: 'ops' },
  { label: 'DataAgent', value: 'data' },
  { label: 'PromptAgent', value: 'prompt' },
  { label: 'TestAgent', value: 'test' },
  { label: 'ChatAgent', value: 'chat' },
]

// 状态颜色映射（添加默认值处理）
const getStatusColor = (status: string): string => {
  const colors: Record<string, string> = {
    RUNNING: 'warning',
    SUCCESS: 'success',
    FAILED: 'danger',
    CANCELLED: 'info',
  }
  return colors[status] || 'info'
}

// 状态图标映射（添加默认值处理）
const getStatusIcon = (status: string): any => {
  const icons: Record<string, any> = {
    RUNNING: Loading,
    SUCCESS: Check,
    FAILED: Close,
    CANCELLED: Warning,
  }
  return icons[status] || Warning // 默认使用 Warning 图标
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
      eventType: eventTypeFilter.value || undefined,
    })
    logs.value = result.data
    total.value = result.total
  } catch (e) {
    const errorMsg = e instanceof Error ? e.message : '未知错误'
    ElMessage.error('加载审计日志失败: ' + errorMsg)
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
  } catch (e) {
    const errorMsg = e instanceof Error ? e.message : '未知错误'
    ElMessage.error('加载详情失败: ' + errorMsg)
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
        inputValue: '30',
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
    <!-- 顶部：统计 + 筛选 -->
    <div class="top-bar">
      <!-- 统计卡片 -->
      <div class="stats-row" v-if="stats">
        <div class="stat-item">
          <el-icon class="stat-icon total"><Timer /></el-icon>
          <span class="stat-value">{{ stats.totalExecutions }}</span>
          <span class="stat-label">总执行</span>
        </div>
        <div class="stat-divider"></div>
        <div class="stat-item">
          <el-icon class="stat-icon success"><Check /></el-icon>
          <span class="stat-value">{{ stats.successCount }}</span>
          <span class="stat-label">成功</span>
        </div>
        <div class="stat-divider"></div>
        <div class="stat-item">
          <el-icon class="stat-icon error"><Close /></el-icon>
          <span class="stat-value">{{ stats.errorCount }}</span>
          <span class="stat-label">失败</span>
        </div>
        <div class="stat-divider"></div>
        <div class="stat-item">
          <el-progress
            type="circle"
            :percentage="successRate"
            :width="32"
            :stroke-width="3"
            :color="successRate >= 90 ? '#67c23a' : successRate >= 70 ? '#e6a23c' : '#f56c6c'"
          />
          <span class="stat-value">{{ successRate }}%</span>
          <span class="stat-label">成功率</span>
        </div>
        <div class="stat-divider"></div>
        <div class="stat-item">
          <el-icon class="stat-icon tool"><Monitor /></el-icon>
          <span class="stat-value">{{ stats.toolCalls }}</span>
          <span class="stat-label">工具调用</span>
        </div>
      </div>

      <!-- 筛选和操作 -->
      <div class="filter-actions">
        <el-input
          v-model="searchQuery"
          placeholder="搜索用户 ID..."
          :prefix-icon="Search"
          clearable
          style="width: 160px"
          @input="handleSearchChange"
        />
        <el-select
          v-model="agentNameFilter"
          placeholder="Agent"
          clearable
          style="width: 100px"
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
          style="width: 120px"
          @change="handleFilterChange"
        >
          <el-option
            v-for="item in eventTypeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-button-group>
          <el-button :icon="Refresh" @click="handleRefresh">刷新</el-button>
          <el-button :icon="Delete" type="danger" @click="handleCleanup">清理</el-button>
        </el-button-group>
      </div>
    </div>

    <!-- 日志表格 -->
    <el-table :data="logs" v-loading="loading" stripe style="width: 100%">
      <el-table-column prop="timestamp" label="时间" width="180">
        <template #default="{ row }">
          {{ formatTime(row.timestamp) }}
        </template>
      </el-table-column>
      <el-table-column prop="traceId" label="Trace ID" width="280">
        <template #default="{ row }">
          <PathBreadcrumb
            :value="row.traceId"
            separator="uuid"
            :max-items="4"
            :expandable="false"
          />
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
          <el-tag :type="getStatusColor(row.status)" size="small">
            <el-icon class="status-icon"><component :is="getStatusIcon(row.status)" /></el-icon>
            {{ row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="clientIp" label="IP" width="130" />
      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ row }">
          <el-button :icon="View" circle size="small" @click="handleViewDetail(row.traceId)" />
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
    <el-dialog v-model="detailVisible" title="执行详情" width="70%" destroy-on-close>
      <div v-loading="detailLoading">
        <template v-if="detailData">
          <!-- 基本信息 -->
          <el-descriptions :column="4" border class="detail-info">
            <el-descriptions-item label="Trace ID">
              <PathBreadcrumb :value="detailData.traceId" separator="uuid" :max-items="8" />
            </el-descriptions-item>
            <el-descriptions-item label="用户">{{ detailData.userId }}</el-descriptions-item>
            <el-descriptions-item label="Agent">{{ detailData.agentName }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="getStatusColor(detailData.status)">
                {{ detailData.status }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="开始时间">
              {{ formatTime(detailData.startTime) }}
            </el-descriptions-item>
            <el-descriptions-item label="结束时间">
              {{ formatTime(detailData.endTime) }}
            </el-descriptions-item>
            <el-descriptions-item label="持续时间">
              {{ formatDuration(detailData.durationMs) }}
            </el-descriptions-item>
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
                <div
                  v-if="step.details && Object.keys(step.details).length > 0"
                  class="step-details"
                >
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
.top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: var(--bg-primary);
  border-radius: var(--radius-md);
  padding: 12px 16px;
  margin-bottom: 16px;
  box-shadow: var(--shadow-sm);
  flex-wrap: wrap;
  gap: 12px;
}

.stats-row {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.stat-icon {
  font-size: 18px;
}

.stat-icon.total {
  color: var(--color-primary);
}

.stat-icon.success {
  color: var(--color-success);
}

.stat-icon.error {
  color: var(--color-danger);
}

.stat-icon.tool {
  color: var(--color-warning);
}

.stat-divider {
  width: 1px;
  height: 24px;
  background: var(--border-color);
}

.stat-value {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.stat-label {
  font-size: 12px;
  color: var(--text-tertiary);
}

.filter-actions {
  display: flex;
  gap: 8px;
  align-items: center;
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
  color: var(--text-primary);
}

.step-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.step-agent {
  font-size: 12px;
  color: var(--text-tertiary);
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
  color: var(--text-tertiary);
  font-size: 12px;
}

@media (max-width: 900px) {
  .top-bar {
    flex-direction: column;
    align-items: stretch;
  }
  .stats-row {
    justify-content: center;
  }
  .filter-actions {
    justify-content: flex-end;
  }
}
</style>
