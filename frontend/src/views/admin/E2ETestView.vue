<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, VideoPlay } from '@element-plus/icons-vue'
import { testApi, type TestResultSummary, type TestStatsSummary } from '@/api/admin'

const emit = defineEmits<{
  'stats-update': [stats: Partial<TestStatsSummary>]
}>()

const loading = ref(false)
const results = ref<TestResultSummary[]>([])
const reportUrl = ref<string | null>(null)

const formatDuration = (ms: number | undefined | null) => {
  if (ms === undefined || ms === null) return '-'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(2)}s`
}

const getStatusType = (status: string) => {
  const types: Record<string, '' | 'success' | 'warning' | 'info' | 'danger'> = {
    'passed': 'success',
    'failed': 'danger',
    'running': 'warning',
    'pending': 'info'
  }
  return types[status] || ''
}

const runTests = async () => {
  loading.value = true
  try {
    await testApi.runE2ETests()
    ElMessage.success('E2E 测试已启动，请稍后刷新查看结果')
  } catch (error) {
    ElMessage.error('启动 E2E 测试失败')
  } finally {
    loading.value = false
  }
}

const loadResults = async () => {
  loading.value = true
  try {
    results.value = await testApi.getE2EStatus()
    emit('stats-update', calculateStats())
  } catch (error) {
    console.error('加载 E2E 测试结果失败', error)
    ElMessage.error('加载 E2E 测试结果失败')
  } finally {
    loading.value = false
  }
}

const loadReport = async () => {
  try {
    const url = await testApi.getE2EReport()
    // 只在报告路径有效时设置
    if (url && url !== '') {
      reportUrl.value = url
    }
  } catch (error) {
    // 报告不可用，不显示按钮
    reportUrl.value = null
  }
}

const calculateStats = () => {
  const passed = results.value.filter(r => r.status === 'passed').length
  return {
    passed,
    failed: results.value.filter(r => r.status === 'failed').length,
    running: results.value.filter(r => r.status === 'running').length,
    totalTests: results.value.length,
    avgResponseTime: results.value.length > 0
      ? Math.round(results.value.reduce((sum, r) => sum + (r.duration ?? 0), 0) / results.value.length)
      : 0
  }
}

const openReport = () => {
  if (reportUrl.value) {
    window.open(reportUrl.value, '_blank')
  }
}

onMounted(() => {
  loadResults()
  loadReport()
})
</script>

<template>
  <div class="e2e-test">
    <!-- 操作栏 -->
    <div class="mb-4 flex justify-between items-center">
      <div class="text-gray-500">
        E2E 测试需要前端开发服务器运行
      </div>
      <div class="flex gap-2">
        <el-button v-if="reportUrl" @click="openReport">
          <el-icon><Document /></el-icon>
          查看报告
        </el-button>
        <el-button type="primary" @click="runTests" :loading="loading">
          <el-icon><VideoPlay /></el-icon>
          运行 E2E 测试
        </el-button>
      </div>
    </div>

    <!-- 测试结果列表 -->
    <el-table :data="results" stripe v-loading="loading">
      <el-table-column prop="testName" label="测试名称" width="300" />
      <el-table-column prop="status" label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)">
            {{ row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="duration" label="耗时" width="120">
        <template #default="{ row }">
          {{ formatDuration(row.duration) }}
        </template>
      </el-table-column>
      <el-table-column prop="assertions" label="断言">
        <template #default="{ row }">
          <div class="flex items-center gap-2" v-if="row.assertions">
            <el-tag type="success" size="small">
              {{ row.assertions.passed }} 通过
            </el-tag>
            <el-tag v-if="row.assertions.failed > 0" type="danger" size="small">
              {{ row.assertions.failed }} 失败
            </el-tag>
          </div>
          <span v-else class="text-gray-400">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="error" label="错误信息">
        <template #default="{ row }">
          <span v-if="row.error" class="text-red-500">{{ row.error }}</span>
          <span v-else class="text-gray-400">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="timestamp" label="时间" width="180">
        <template #default="{ row }">
          {{ new Date(row.timestamp).toLocaleString() }}
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="results.length === 0 && !loading" description="暂无 E2E 测试结果">
      <el-button type="primary" @click="runTests">运行测试</el-button>
    </el-empty>
  </div>
</template>