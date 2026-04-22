<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { testApi, type PerformanceResultSummary } from '@/api/admin'

const emit = defineEmits<{
  'stats-update': [stats: any]
}>()

const loading = ref(false)
const results = ref<PerformanceResultSummary[]>([])
const selectedSimulation = ref('ChatSimulation')
const availableSimulations = ref<string[]>([])

const formatTime = (ms: number) => {
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

const summaryStats = computed(() => {
  if (results.value.length === 0) return null
  const latest = results.value[results.value.length - 1]
  return {
    totalRequests: latest.requests,
    successRate: latest.successRate,
    avgResponseTime: latest.avgResponseTime,
    p95ResponseTime: latest.p95ResponseTime,
    p99ResponseTime: latest.p99ResponseTime,
    duration: latest.endTime - latest.startTime
  }
})

const runTest = async () => {
  loading.value = true
  try {
    await testApi.runPerformanceTest(selectedSimulation.value)
    await loadResults()
    ElMessage.success('性能测试已启动')
  } catch (error) {
    ElMessage.error('启动性能测试失败')
  } finally {
    loading.value = false
  }
}

const loadResults = async () => {
  loading.value = true
  try {
    results.value = await testApi.getPerformanceResults()
    emit('stats-update', calculateStats())
  } catch (error) {
    console.error('加载性能测试结果失败', error)
    ElMessage.error('加载性能测试结果失败')
  } finally {
    loading.value = false
  }
}

const loadSimulations = async () => {
  try {
    availableSimulations.value = await testApi.getAvailableSimulations()
  } catch (error) {
    console.error('加载模拟列表失败', error)
    ElMessage.warning('加载模拟场景列表失败')
  }
}

const calculateStats = () => {
  return {
    passed: results.value.filter(r => r.successRate >= 95).length,
    failed: results.value.filter(r => r.successRate < 95).length,
    running: 0,
    totalTests: results.value.length,
    avgResponseTime: results.value.length > 0
      ? Math.round(results.value.reduce((sum, r) => sum + r.avgResponseTime, 0) / results.value.length)
      : 0
  }
}

onMounted(() => {
  loadResults()
  loadSimulations()
})
</script>

<template>
  <div class="performance-test">
    <!-- 操作栏 -->
    <div class="mb-4 flex justify-between items-center">
      <el-select v-model="selectedSimulation" placeholder="选择模拟场景" style="width: 250px">
        <el-option
          v-for="sim in availableSimulations"
          :key="sim"
          :label="sim"
          :value="sim"
        />
      </el-select>
      <el-button type="success" @click="runTest" :loading="loading">
        <el-icon><TrendCharts /></el-icon>
        运行性能测试
      </el-button>
    </div>

    <!-- 概览统计 -->
    <el-row :gutter="20" class="mb-6" v-if="summaryStats">
      <el-col :span="4">
        <el-card shadow="never" class="stat-card-small">
          <div class="text-sm text-gray-500">总请求数</div>
          <div class="text-lg font-bold">{{ summaryStats.totalRequests.toLocaleString() }}</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never" class="stat-card-small">
          <div class="text-sm text-gray-500">成功率</div>
          <div class="text-lg font-bold" :class="summaryStats.successRate >= 95 ? 'text-green-600' : 'text-red-600'">
            {{ summaryStats.successRate.toFixed(1) }}%
          </div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never" class="stat-card-small">
          <div class="text-sm text-gray-500">平均耗时</div>
          <div class="text-lg font-bold">{{ formatTime(summaryStats.avgResponseTime) }}</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never" class="stat-card-small">
          <div class="text-sm text-gray-500">P95 耗时</div>
          <div class="text-lg font-bold">{{ formatTime(summaryStats.p95ResponseTime) }}</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never" class="stat-card-small">
          <div class="text-sm text-gray-500">P99 耗时</div>
          <div class="text-lg font-bold">{{ formatTime(summaryStats.p99ResponseTime) }}</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never" class="stat-card-small">
          <div class="text-sm text-gray-500">测试时长</div>
          <div class="text-lg font-bold">{{ formatTime(summaryStats.duration) }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 结果列表 -->
    <el-table :data="results" stripe v-loading="loading">
      <el-table-column prop="simulation" label="模拟场景" width="200" />
      <el-table-column prop="requests" label="请求数" width="120">
        <template #default="{ row }">
          {{ row.requests.toLocaleString() }}
        </template>
      </el-table-column>
      <el-table-column prop="successRate" label="成功率" width="120">
        <template #default="{ row }">
          <el-tag :type="row.successRate >= 95 ? 'success' : row.successRate >= 90 ? 'warning' : 'danger'">
            {{ row.successRate.toFixed(1) }}%
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="avgResponseTime" label="平均耗时" width="120">
        <template #default="{ row }">
          {{ formatTime(row.avgResponseTime) }}
        </template>
      </el-table-column>
      <el-table-column prop="maxResponseTime" label="最大耗时" width="120">
        <template #default="{ row }">
          {{ formatTime(row.maxResponseTime) }}
        </template>
      </el-table-column>
      <el-table-column prop="p95ResponseTime" label="P95" width="120">
        <template #default="{ row }">
          {{ formatTime(row.p95ResponseTime) }}
        </template>
      </el-table-column>
      <el-table-column prop="p99ResponseTime" label="P99" width="120">
        <template #default="{ row }">
          {{ formatTime(row.p99ResponseTime) }}
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="results.length === 0 && !loading" description="暂无性能测试结果" />
  </div>
</template>

<style scoped>
.stat-card-small {
  text-align: center;
}
</style>