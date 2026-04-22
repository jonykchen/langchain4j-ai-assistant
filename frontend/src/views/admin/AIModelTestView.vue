<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { testApi, type AIModelTestSummary } from '@/api/admin'

const emit = defineEmits<{
  'stats-update': [stats: any]
}>()

const loading = ref(false)
const results = ref<AIModelTestSummary[]>([])
const selectedCategory = ref('')

const categories = [
  { label: '全部', value: '' },
  { label: '基础问答', value: 'basic' },
  { label: '代码生成', value: 'code-generation' },
  { label: '知识问答', value: 'knowledge' },
  { label: '多轮对话', value: 'conversation' }
]

const filteredResults = computed(() => {
  if (!selectedCategory.value) return results.value
  return results.value.filter(r => r.category === selectedCategory.value)
})

const categoryStats = computed(() => {
  const stats: Record<string, { total: number; passed: number }> = {}
  for (const result of results.value) {
    if (!stats[result.category]) {
      stats[result.category] = { total: 0, passed: 0 }
    }
    stats[result.category].total++
    if (result.passed) stats[result.category].passed++
  }
  return stats
})

const runTests = async () => {
  loading.value = true
  try {
    await testApi.runAIModelTests(selectedCategory.value || undefined)
    await loadResults()
    ElMessage.success('AI 模型测试完成')
  } catch (error) {
    ElMessage.error('运行测试失败')
  } finally {
    loading.value = false
  }
}

const loadResults = async () => {
  loading.value = true
  try {
    results.value = await testApi.getAIModelResults()
    emit('stats-update', calculateStats())
  } catch (error) {
    console.error('加载测试结果失败', error)
    ElMessage.error('加载测试结果失败')
  } finally {
    loading.value = false
  }
}

const calculateStats = () => {
  const passed = results.value.filter(r => r.passed).length
  const count = results.value.length
  return {
    passed,
    failed: count - passed,
    running: 0,
    totalTests: count,
    avgResponseTime: count > 0
      ? Math.round(results.value.reduce((sum, r) => sum + r.responseTime, 0) / count)
      : 0
  }
}

const getCategoryType = (category: string) => {
  const types: Record<string, '' | 'success' | 'warning' | 'info' | 'danger'> = {
    'basic': '',
    'code-generation': 'warning',
    'knowledge': 'success',
    'conversation': 'info'
  }
  return types[category] || ''
}

const getCategoryLabel = (category: string) => {
  const labels: Record<string, string> = {
    'basic': '基础问答',
    'code-generation': '代码生成',
    'knowledge': '知识问答',
    'conversation': '多轮对话'
  }
  return labels[category] || category
}

onMounted(() => {
  loadResults()
})
</script>

<template>
  <div class="ai-model-test">
    <!-- 筛选和操作 -->
    <div class="mb-4 flex justify-between items-center">
      <el-select v-model="selectedCategory" placeholder="选择分类" clearable style="width: 200px">
        <el-option
          v-for="cat in categories"
          :key="cat.value"
          :label="cat.label"
          :value="cat.value"
        />
      </el-select>
      <el-button type="primary" @click="runTests" :loading="loading">
        运行测试
      </el-button>
    </div>

    <!-- 分类统计 -->
    <el-row :gutter="16" class="mb-4">
      <el-col :span="6" v-for="(stat, category) in categoryStats" :key="category">
        <el-card shadow="never" class="category-card">
          <div class="text-sm text-gray-500">{{ getCategoryLabel(category) }}</div>
          <div class="text-lg font-bold">
            {{ stat.passed }}/{{ stat.total }}
            <span class="text-sm font-normal text-gray-400">
              ({{ stat.total > 0 ? Math.round(stat.passed / stat.total * 100) : 0 }}%)
            </span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 测试结果列表 -->
    <el-table :data="filteredResults" stripe v-loading="loading">
      <el-table-column prop="testName" label="测试名称" width="200" />
      <el-table-column prop="category" label="分类" width="120">
        <template #default="{ row }">
          <el-tag :type="getCategoryType(row.category)">
            {{ getCategoryLabel(row.category) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="score" label="得分" width="180">
        <template #default="{ row }">
          <el-progress
            :percentage="Math.round(row.score * 100)"
            :status="row.score >= 0.8 ? 'success' : row.score >= 0.5 ? 'warning' : 'exception'"
            :format="(val: number) => val + '%'"
          />
        </template>
      </el-table-column>
      <el-table-column prop="responseTime" label="耗时" width="100">
        <template #default="{ row }">
          {{ row.responseTime }}ms
        </template>
      </el-table-column>
      <el-table-column prop="passed" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.passed ? 'success' : 'danger'">
            {{ row.passed ? '通过' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="断言详情">
        <template #default="{ row }">
          <div class="assertion-list">
            <div
              v-for="(detail, index) in row.details"
              :key="index"
              :class="['assertion-item', detail.passed ? 'passed' : 'failed']"
            >
              <el-icon v-if="detail.passed"><CircleCheck /></el-icon>
              <el-icon v-else><CircleClose /></el-icon>
              <span>{{ detail.assertion }}</span>
              <el-tooltip v-if="!detail.passed" :content="`期望: ${detail.expected}, 实际: ${detail.actual}`">
                <el-icon><QuestionFilled /></el-icon>
              </el-tooltip>
            </div>
          </div>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.category-card {
  text-align: center;
}

.assertion-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.assertion-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.assertion-item.passed {
  color: var(--el-color-success);
}

.assertion-item.failed {
  color: var(--el-color-danger);
}
</style>