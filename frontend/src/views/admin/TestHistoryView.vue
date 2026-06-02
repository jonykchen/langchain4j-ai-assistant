<template>
  <div class="admin-page">
    <div class="page-header">
      <div class="header-actions">
        <el-button type="primary" :icon="Download" @click="showExportDialog = true">导出</el-button>
        <el-button type="warning" :icon="Delete" @click="showCleanupDialog = true">清理</el-button>
      </div>
    </div>

    <el-card class="card-section">
      <template #header>
        <span>筛选条件</span>
      </template>

      <!-- 过滤条件 -->
      <el-form :inline="true" class="filter-form">
        <el-form-item label="测试类型">
          <el-select v-model="filters.type" placeholder="全部类型" clearable style="width: 140px">
            <el-option label="E2E 测试" value="E2E" />
            <el-option label="性能测试" value="PERFORMANCE" />
            <el-option label="AI 模型测试" value="AI_MODEL" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 140px">
            <el-option label="运行中" value="RUNNING" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="失败" value="FAILED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 360px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadHistory">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 对比操作 -->
      <div v-if="selectedJobs.length === 2" class="compare-bar">
        <span>已选择 2 条记录进行对比</span>
        <el-button type="success" size="small" @click="compareJobs">开始对比</el-button>
        <el-button size="small" @click="selectedJobs = []">取消选择</el-button>
      </div>

      <!-- 历史列表 -->
      <el-table
        :data="jobs"
        v-loading="loading"
        @selection-change="handleSelectionChange"
        :row-class-name="getRowClassName"
      >
        <el-table-column type="selection" width="50" :selectable="isSelectable" />
        <el-table-column prop="id" label="任务ID" width="200" show-overflow-tooltip />
        <el-table-column prop="testType" label="类型" width="120">
          <template #default="{ row }">
            <el-tag :type="getTypeTagType(row.testType)" size="small">
              {{ getTypeLabel(row.testType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.status)" size="small">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="startTime" label="开始时间" width="180">
          <template #default="{ row }">
            {{ formatTime(row.startTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="endTime" label="结束时间" width="180">
          <template #default="{ row }">
            {{ row.endTime ? formatTime(row.endTime) : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="progress" label="进度" width="100">
          <template #default="{ row }">
            <el-progress
              v-if="row.status === 'RUNNING'"
              :percentage="row.progress"
              :stroke-width="8"
              style="width: 80px"
            />
            <span v-else>{{ row.progress }}%</span>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="消息" min-width="200" show-overflow-tooltip />
        <el-table-column prop="triggeredBy" label="触发者" width="100" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="viewJobResults(row.id)">
              查看结果
            </el-button>
            <el-button type="danger" link size="small" @click="deleteJob(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="filters.page"
          v-model:page-size="filters.size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadHistory"
          @current-change="loadHistory"
        />
      </div>
    </el-card>

    <!-- 导出对话框 -->
    <TestExportDialog v-model:visible="showExportDialog" @export="handleExport" />

    <!-- 清理对话框 -->
    <el-dialog v-model="showCleanupDialog" title="清理历史记录" width="400px">
      <el-form>
        <el-form-item label="保留天数">
          <el-input-number v-model="cleanupDays" :min="1" :max="365" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCleanupDialog = false">取消</el-button>
        <el-button type="danger" @click="handleCleanup">确认清理</el-button>
      </template>
    </el-dialog>

    <!-- 对比结果对话框 -->
    <el-dialog v-model="showCompareDialog" title="测试结果对比" width="80%">
      <div v-if="compareResult">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="任务 1">{{ compareResult.job1.jobId }}</el-descriptions-item>
          <el-descriptions-item label="任务 2">{{ compareResult.job2.jobId }}</el-descriptions-item>
        </el-descriptions>

        <div class="compare-summary">
          <el-statistic title="总测试数" :value="compareResult.summary.totalTests" />
          <el-statistic title="改善" :value="compareResult.summary.improved" />
          <el-statistic title="退化" :value="compareResult.summary.degraded" />
          <el-statistic title="不变" :value="compareResult.summary.unchanged" />
        </div>

        <el-table :data="compareResult.diffs" style="margin-top: 16px">
          <el-table-column prop="testName" label="测试名称" width="200" />
          <el-table-column prop="field" label="对比字段" width="150" />
          <el-table-column label="值 1" width="150">
            <template #default="{ row }">
              {{ row.value1 }}
            </template>
          </el-table-column>
          <el-table-column label="值 2" width="150">
            <template #default="{ row }">
              {{ row.value2 }}
            </template>
          </el-table-column>
          <el-table-column label="方向" width="100">
            <template #default="{ row }">
              <el-tag
                v-if="row.changed"
                :type="
                  row.changeDirection === 'improved'
                    ? 'success'
                    : row.changeDirection === 'degraded'
                      ? 'danger'
                      : 'info'
                "
                size="small"
              >
                {{
                  row.changeDirection === 'improved'
                    ? '改善'
                    : row.changeDirection === 'degraded'
                      ? '退化'
                      : '不变'
                }}
              </el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>

    <!-- 结果详情对话框 -->
    <el-dialog v-model="showResultsDialog" title="测试结果详情" width="70%">
      <div v-if="jobResults" v-loading="resultsLoading">
        <el-tabs v-model="resultsTab">
          <el-tab-pane label="E2E 测试" name="e2e">
            <el-table :data="jobResults.e2e" empty-text="无 E2E 测试结果">
              <el-table-column prop="testName" label="测试名称" />
              <el-table-column prop="status" label="状态" width="80">
                <template #default="{ row }">
                  <el-tag :type="row.status === 'passed' ? 'success' : 'danger'" size="small">
                    {{ row.status }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="durationMs" label="耗时(ms)" width="100" />
              <el-table-column prop="assertionsPassed" label="通过断言" width="90" />
              <el-table-column prop="assertionsFailed" label="失败断言" width="90" />
              <el-table-column prop="errorMessage" label="错误信息" show-overflow-tooltip />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="性能测试" name="performance">
            <el-table :data="jobResults.performance" empty-text="无性能测试结果">
              <el-table-column prop="simulation" label="场景" />
              <el-table-column prop="requests" label="请求数" width="80" />
              <el-table-column prop="successRate" label="成功率" width="80">
                <template #default="{ row }">{{ row.successRate }}%</template>
              </el-table-column>
              <el-table-column prop="avgResponseTime" label="平均(ms)" width="90" />
              <el-table-column prop="p95ResponseTime" label="P95(ms)" width="90" />
              <el-table-column prop="p99ResponseTime" label="P99(ms)" width="90" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="AI 模型测试" name="aiModel">
            <el-table :data="jobResults.aiModel" empty-text="无 AI 模型测试结果">
              <el-table-column prop="testName" label="测试名称" />
              <el-table-column prop="category" label="分类" width="120">
                <template #default="{ row }">
                  <el-tag size="small">{{ row.category }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="score" label="得分" width="80">
                <template #default="{ row }">{{ (row.score * 100).toFixed(1) }}%</template>
              </el-table-column>
              <el-table-column prop="passed" label="通过" width="60">
                <template #default="{ row }">
                  <el-tag :type="row.passed ? 'success' : 'danger'" size="small">
                    {{ row.passed ? '是' : '否' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="responseTime" label="耗时(ms)" width="100" />
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Delete } from '@element-plus/icons-vue'
import {
  testApi,
  type TestJobHistory,
  type TestResultHistoryQuery,
  type TestComparisonResult,
  type E2ETestResultDetail,
  type PerformanceTestResultDetail,
  type AIModelTestResultDetail,
} from '@/api/admin'
import TestExportDialog from '@/components/TestExportDialog.vue'

interface JobResults {
  e2e: E2ETestResultDetail[]
  performance: PerformanceTestResultDetail[]
  aiModel: AIModelTestResultDetail[]
}

const loading = ref(false)
const jobs = ref<TestJobHistory[]>([])
const total = ref(0)
const dateRange = ref<string[]>([])

const filters = reactive<TestResultHistoryQuery>({
  type: undefined,
  status: undefined,
  page: 1,
  size: 20,
})

// 对比
const selectedJobs = ref<TestJobHistory[]>([])
const showCompareDialog = ref(false)
const compareResult = ref<TestComparisonResult | null>(null)

// 导出
const showExportDialog = ref(false)

// 清理
const showCleanupDialog = ref(false)
const cleanupDays = ref(90)

// 结果详情
const showResultsDialog = ref(false)
const resultsLoading = ref(false)
const jobResults = ref<JobResults | null>(null)
const resultsTab = ref('e2e')

onMounted(() => {
  loadHistory()
})

async function loadHistory() {
  loading.value = true
  try {
    const params: Record<string, unknown> = {
      page: (filters.page || 1) - 1,
      size: filters.size || 20,
    }
    if (filters.type) params.type = filters.type
    if (filters.status) params.status = filters.status
    if (dateRange.value?.length === 2) {
      params.from = dateRange.value[0]
      params.to = dateRange.value[1]
    }
    const res = await testApi.getTestJobHistory(params)
    jobs.value = res.data || []
    total.value = res.total || 0
  } catch (e) {
    const error = e instanceof Error ? e.message : '未知错误'
    ElMessage.error('加载历史记录失败: ' + error)
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.type = undefined
  filters.status = undefined
  dateRange.value = []
  filters.page = 1
  loadHistory()
}

function handleSelectionChange(rows: TestJobHistory[]) {
  // 只允许选择同类型的两条
  if (rows.length <= 2) {
    selectedJobs.value = rows
  }
}

function isSelectable(row: TestJobHistory) {
  if (selectedJobs.value.length < 2) return true
  if (selectedJobs.value.some(j => j.id === row.id)) return true
  // 只允许选择同类型
  return selectedJobs.value[0].testType === row.testType
}

async function compareJobs() {
  if (selectedJobs.value.length !== 2) {
    ElMessage.warning('请选择 2 条同类型的记录')
    return
  }
  try {
    compareResult.value = await testApi.compareTestResults(
      selectedJobs.value[0].id,
      selectedJobs.value[1].id
    )
    showCompareDialog.value = true
  } catch (e) {
    const error = e instanceof Error ? e.message : '未知错误'
    ElMessage.error('对比失败: ' + error)
  }
}

async function viewJobResults(jobId: string) {
  showResultsDialog.value = true
  resultsLoading.value = true
  try {
    jobResults.value = await testApi.getTestJobResults(jobId)
  } catch (e) {
    const error = e instanceof Error ? e.message : '未知错误'
    ElMessage.error('加载结果失败: ' + error)
  } finally {
    resultsLoading.value = false
  }
}

async function deleteJob(jobId: string) {
  try {
    await ElMessageBox.confirm('确认删除该测试记录？删除后不可恢复。', '确认删除', {
      type: 'warning',
    })
    await testApi.cancelJob(jobId)
    ElMessage.success('删除成功')
    loadHistory()
  } catch {
    // 用户取消
  }
}

async function handleCleanup() {
  try {
    const res = await testApi.cleanupTestJobs(cleanupDays.value)
    ElMessage.success(`清理完成，删除了 ${res.deleted} 条记录`)
    showCleanupDialog.value = false
    loadHistory()
  } catch (e) {
    const error = e instanceof Error ? e.message : '未知错误'
    ElMessage.error('清理失败: ' + error)
  }
}

async function handleExport(params: { type: string; format: string; from?: string; to?: string }) {
  try {
    const blob = await testApi.exportTestResults(params)
    const url = window.URL.createObjectURL(new Blob([blob as BlobPart]))
    const link = document.createElement('a')
    link.href = url
    const ext = params.format === 'excel' ? 'xlsx' : params.format
    link.download = `test-results-${params.type.toLowerCase()}.${ext}`
    link.click()
    window.URL.revokeObjectURL(url)
    showExportDialog.value = false
    ElMessage.success('导出成功')
  } catch (e) {
    const error = e instanceof Error ? e.message : '未知错误'
    ElMessage.error('导出失败: ' + error)
  }
}

// 辅助方法
function getTypeLabel(type: string) {
  const map: Record<string, string> = { E2E: 'E2E', PERFORMANCE: '性能', AI_MODEL: 'AI模型' }
  return map[type] || type
}

function getTypeTagType(type: string) {
  const map: Record<string, string> = { E2E: '', PERFORMANCE: 'warning', AI_MODEL: 'success' }
  return map[type] || ''
}

function getStatusLabel(status: string) {
  const map: Record<string, string> = {
    RUNNING: '运行中',
    COMPLETED: '已完成',
    FAILED: '失败',
    CANCELLED: '已取消',
    PENDING: '等待',
  }
  return map[status] || status
}

function getStatusTagType(status: string) {
  const map: Record<string, string> = {
    RUNNING: 'warning',
    COMPLETED: 'success',
    FAILED: 'danger',
    CANCELLED: 'info',
    PENDING: 'info',
  }
  return map[status] || ''
}

function formatTime(time: string) {
  if (!time) return '-'
  try {
    return new Date(time).toLocaleString('zh-CN')
  } catch {
    return time
  }
}

function getRowClassName({ row }: { row: TestJobHistory }) {
  if (row.status === 'FAILED') return 'row-failed'
  return ''
}
</script>

<style scoped>
.test-history {
  padding: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.filter-form {
  margin-bottom: 16px;
}

.compare-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 16px;
  background: var(--color-primary-light);
  border-radius: var(--radius-sm);
  margin-bottom: 16px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.compare-summary {
  display: flex;
  gap: 32px;
  margin-top: 16px;
  padding: 16px;
  background: var(--bg-secondary);
  border-radius: var(--radius-sm);
}
</style>
