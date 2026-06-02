<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  VideoPlay,
  TrendCharts,
  MagicStick,
  Finished,
  CircleCheck,
  CircleClose,
  Loading,
  Timer,
} from '@element-plus/icons-vue'
import { testApi, type TestStatsSummary, type TestJobStatus } from '@/api/admin'
import E2ETestView from './E2ETestView.vue'
import PerformanceTestView from './PerformanceTestView.vue'
import AIModelTestView from './AIModelTestView.vue'

const activeTab = ref('ai')
const stats = ref<TestStatsSummary>({
  totalTests: 0,
  passed: 0,
  failed: 0,
  running: 0,
  passRate: 0,
  avgResponseTime: 0,
})

const e2eLoading = ref(false)
const perfLoading = ref(false)
const aiLoading = ref(false)
const allLoading = ref(false)
const statsLoading = ref(false)
const loadError = ref<string | null>(null)

const currentJobId = ref<string | null>(null)
const jobStatus = ref<TestJobStatus | null>(null)

// 轮询清理相关
const pollingTimeouts = new Set<ReturnType<typeof setTimeout>>()
const MAX_POLLING_ATTEMPTS = 150 // 最大轮询次数（5分钟）
let pollingAttempts = 0

// 统计卡片数据
const statsCards = computed(() => [
  {
    label: '通过测试',
    value: stats.value.passed,
    icon: CircleCheck,
    color: 'text-green-500',
    bgColor: 'bg-green-50',
  },
  {
    label: '失败测试',
    value: stats.value.failed,
    icon: CircleClose,
    color: 'text-red-500',
    bgColor: 'bg-red-50',
  },
  {
    label: '运行中',
    value: stats.value.running,
    icon: Loading,
    color: 'text-blue-500',
    bgColor: 'bg-blue-50',
  },
  {
    label: '平均耗时',
    value: `${stats.value.avgResponseTime}ms`,
    icon: Timer,
    color: 'text-yellow-500',
    bgColor: 'bg-yellow-50',
  },
])

// 加载统计数据
const loadStats = async () => {
  statsLoading.value = true
  loadError.value = null
  try {
    stats.value = await testApi.getStatsSummary()
  } catch (error) {
    const errorMsg = error instanceof Error ? error.message : '加载失败，请检查网络连接或登录状态'
    loadError.value = errorMsg
    ElMessage.warning('加载统计数据失败，可能需要重新登录')
  } finally {
    statsLoading.value = false
  }
}

// 运行 E2E 测试
const runE2ETests = async () => {
  e2eLoading.value = true
  try {
    const result = await testApi.runE2ETests()
    currentJobId.value = result.jobId
    ElMessage.success('E2E 测试已启动')
    pollJobStatus(result.jobId)
  } catch (error) {
    const errorMsg = error instanceof Error ? error.message : '请检查登录状态'
    ElMessage.error('启动 E2E 测试失败: ' + errorMsg)
  } finally {
    e2eLoading.value = false
  }
}

// 运行性能测试
const runPerformanceTest = async () => {
  perfLoading.value = true
  try {
    const result = await testApi.runPerformanceTest('ChatSimulation')
    currentJobId.value = result.jobId
    ElMessage.success('性能测试已启动')
    pollJobStatus(result.jobId)
  } catch (error) {
    const errorMsg = error instanceof Error ? error.message : '请检查登录状态'
    ElMessage.error('启动性能测试失败: ' + errorMsg)
  } finally {
    perfLoading.value = false
  }
}

// 运行 AI 模型测试
const runAIModelTests = async () => {
  aiLoading.value = true
  try {
    const result = await testApi.runAIModelTests()
    currentJobId.value = result.jobId
    ElMessage.success('AI 模型测试已启动')
    pollJobStatus(result.jobId)
  } catch (error) {
    const errorMsg = error instanceof Error ? error.message : '请检查登录状态'
    ElMessage.error('启动 AI 模型测试失败: ' + errorMsg)
  } finally {
    aiLoading.value = false
  }
}

// 运行全部测试
const runAllTests = async () => {
  allLoading.value = true
  try {
    await Promise.all([
      testApi.runE2ETests(),
      testApi.runPerformanceTest('ChatSimulation'),
      testApi.runAIModelTests(),
    ])
    ElMessage.success('全部测试已启动')
  } catch (error) {
    const errorMsg = error instanceof Error ? error.message : '请检查登录状态'
    ElMessage.error('启动测试失败: ' + errorMsg)
  } finally {
    allLoading.value = false
  }
}

// 轮询任务状态
const pollJobStatus = async (jobId: string) => {
  pollingAttempts = 0
  const poll = async () => {
    pollingAttempts++
    if (pollingAttempts > MAX_POLLING_ATTEMPTS) {
      ElMessage.warning('轮询超时，请手动刷新查看状态')
      return
    }
    try {
      const status = await testApi.getJobStatus(jobId)
      jobStatus.value = status

      if (status.status === 'running') {
        const timeout = setTimeout(poll, 2000)
        pollingTimeouts.add(timeout)
      } else {
        loadStats()
        if (status.status === 'completed') {
          ElMessage.success('测试完成')
        } else if (status.status === 'failed') {
          ElMessage.error(`测试失败: ${status.message}`)
        }
      }
    } catch (error) {
      console.error('获取任务状态失败', error)
    }
  }
  poll()
}

// 取消任务
const cancelJob = async () => {
  if (!currentJobId.value) return
  try {
    await testApi.cancelJob(currentJobId.value)
    ElMessage.success('任务已取消')
    jobStatus.value = null
    currentJobId.value = null
  } catch (error) {
    ElMessage.error('取消任务失败')
  }
}

// 更新统计
const updateStats = (newStats: Partial<TestStatsSummary>) => {
  stats.value = { ...stats.value, ...newStats }
}

// 刷新数据
const refreshData = async () => {
  await loadStats()
}

onMounted(() => {
  loadStats()
})

// 组件卸载时清理所有轮询定时器
onUnmounted(() => {
  pollingTimeouts.forEach(timeout => clearTimeout(timeout))
  pollingTimeouts.clear()
})
</script>

<template>
  <div class="admin-page">
    <!-- 统计卡片 -->
    <el-row :gutter="20" class="card-section" v-loading="statsLoading">
      <el-col :span="6" v-for="card in statsCards" :key="card.label">
        <el-card shadow="hover" :body-style="{ padding: '20px' }">
          <div class="stat-card">
            <div :class="['stat-icon', card.bgColor]">
              <el-icon :class="card.color" :size="32">
                <component :is="card.icon" />
              </el-icon>
            </div>
            <div class="stat-content">
              <div class="stat-value">{{ card.value }}</div>
              <div class="stat-label">{{ card.label }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 加载错误提示 -->
    <el-alert
      v-if="loadError"
      :title="loadError"
      type="warning"
      show-icon
      class="card-section"
      :closable="false"
    >
      <template #default>
        <div class="flex items-center gap-2">
          <span>可能是登录状态已过期，请尝试</span>
          <el-button type="primary" size="small" @click="refreshData">刷新</el-button>
        </div>
      </template>
    </el-alert>

    <!-- 任务状态 -->
    <el-card v-if="jobStatus" class="card-section">
      <template #header>
        <div class="flex justify-between items-center">
          <span>当前任务</span>
          <el-button type="danger" size="small" @click="cancelJob">取消</el-button>
        </div>
      </template>
      <div class="job-info">
        <el-descriptions :column="4">
          <el-descriptions-item label="任务ID">{{ jobStatus.jobId }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag
              :type="
                jobStatus.status === 'running'
                  ? 'warning'
                  : jobStatus.status === 'completed'
                    ? 'success'
                    : 'danger'
              "
            >
              {{ jobStatus.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="进度">{{ jobStatus.progress }}%</el-descriptions-item>
          <el-descriptions-item label="消息">{{ jobStatus.message }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-card>

    <!-- 快速操作 -->
    <el-card class="card-section">
      <template #header>快速测试</template>
      <el-row :gutter="20">
        <el-col :span="6">
          <el-button type="primary" @click="runE2ETests" :loading="e2eLoading">
            <el-icon><VideoPlay /></el-icon>
            运行 E2E 测试
          </el-button>
        </el-col>
        <el-col :span="6">
          <el-button type="success" @click="runPerformanceTest" :loading="perfLoading">
            <el-icon><TrendCharts /></el-icon>
            运行性能测试
          </el-button>
        </el-col>
        <el-col :span="6">
          <el-button type="warning" @click="runAIModelTests" :loading="aiLoading">
            <el-icon><MagicStick /></el-icon>
            运行 AI 模型测试
          </el-button>
        </el-col>
        <el-col :span="6">
          <el-button @click="runAllTests" :loading="allLoading">
            <el-icon><Finished /></el-icon>
            运行全部测试
          </el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- 测试结果标签页 -->
    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane label="AI 模型测试" name="ai">
        <AIModelTestView @stats-update="updateStats" />
      </el-tab-pane>
      <el-tab-pane label="性能测试" name="performance">
        <PerformanceTestView @stats-update="updateStats" />
      </el-tab-pane>
      <el-tab-pane label="E2E 测试" name="e2e">
        <E2ETestView @stats-update="updateStats" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.stat-card {
  display: flex;
  align-items: center;
  gap: var(--space-lg);
}

.stat-icon {
  padding: var(--space-md);
  border-radius: var(--radius-lg);
}

.stat-value {
  font-size: var(--font-size-3xl);
  font-weight: bold;
  color: var(--text-primary);
}

.stat-label {
  font-size: var(--font-size-base);
  color: var(--text-tertiary);
}

.job-info {
  padding: 8px 0;
}
</style>
