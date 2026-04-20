<template>
  <div class="evaluation-view">
    <!-- 评测统计 -->
    <el-row :gutter="20" class="mb-4">
      <el-col :span="8">
        <el-card shadow="hover">
          <el-statistic title="已评测 Trace" :value="evaluationCount" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <el-statistic title="平均得分" :value="avgScore * 100" suffix="%" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <el-statistic title="通过率" :value="passRate * 100" suffix="%" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 评测操作 -->
    <el-card class="mb-4">
      <template #header>
        <span>执行评测</span>
      </template>
      <el-form :inline="true">
        <el-form-item label="Trace IDs">
          <el-input
            v-model="traceIdInput"
            type="textarea"
            :rows="3"
            placeholder="输入 Trace ID，每行一个"
            style="width: 400px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="runEvaluation" :loading="evaluating">
            执行评测
          </el-button>
          <el-button @click="generateReport" :disabled="results.length === 0">
            生成报告
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 评测器列表 -->
    <el-card class="mb-4">
      <template #header>
        <span>可用评测器</span>
      </template>
      <el-table :data="evaluators" stripe size="small">
        <el-table-column prop="name" label="名称" width="200" />
        <el-table-column prop="description" label="描述" />
      </el-table>
    </el-card>

    <!-- 评测结果 -->
    <el-card v-if="results.length > 0">
      <template #header>
        <div class="flex justify-between items-center">
          <span>评测结果</span>
          <el-tag>共 {{ results.length }} 条</el-tag>
        </div>
      </template>
      <el-table :data="results" stripe>
        <el-table-column prop="traceId" label="Trace ID" width="180">
          <template #default="{ row }">
            {{ row.traceId?.slice(0, 8) }}...
          </template>
        </el-table-column>
        <el-table-column prop="overallScore" label="综合得分" width="150">
          <template #default="{ row }">
            <el-progress
              :percentage="row.overallScore * 100"
              :color="getScoreColor(row.overallScore)"
              :format="() => (row.overallScore * 100).toFixed(1) + '%'"
            />
          </template>
        </el-table-column>
        <el-table-column label="通过" width="80">
          <template #default="{ row }">
            <el-tag :type="row.overallScore >= 0.7 ? 'success' : 'danger'" size="small">
              {{ row.overallScore >= 0.7 ? '通过' : '未通过' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="任务完成率" width="120">
          <template #default="{ row }">
            {{ (row.metrics?.taskCompletionRate * 100 || 0).toFixed(1) }}%
          </template>
        </el-table-column>
        <el-table-column label="工具成功率" width="120">
          <template #default="{ row }">
            {{ (row.metrics?.toolSuccessRate * 100 || 0).toFixed(1) }}%
          </template>
        </el-table-column>
        <el-table-column prop="recommendation" label="建议" show-overflow-tooltip />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDetail(row)">
              详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 详情对话框 -->
    <el-dialog
      v-model="detailVisible"
      title="评测详情"
      width="70%"
      destroy-on-close
    >
      <template v-if="selectedResult">
        <el-descriptions :column="2" border class="mb-4">
          <el-descriptions-item label="Trace ID">{{ selectedResult.traceId }}</el-descriptions-item>
          <el-descriptions-item label="综合得分">
            <el-progress
              :percentage="selectedResult.overallScore * 100"
              :color="getScoreColor(selectedResult.overallScore)"
            />
          </el-descriptions-item>
        </el-descriptions>

        <h4 class="mb-2">各评测器结果</h4>
        <el-collapse>
          <el-collapse-item
            v-for="(result, index) in selectedResult.evaluatorResults"
            :key="index"
            :name="index"
          >
            <template #title>
              <span class="flex items-center gap-2">
                <el-tag :type="result.passed ? 'success' : 'danger'" size="small">
                  {{ result.passed ? '通过' : '未通过' }}
                </el-tag>
                <span>{{ result.evaluatorId }}</span>
                <span class="text-gray-500">{{ (result.score * 100).toFixed(1) }}%</span>
              </span>
            </template>
            <div class="p-2">
              <div v-if="result.issues.length > 0" class="mb-2">
                <strong>问题:</strong>
                <ul class="list-disc pl-5">
                  <li v-for="issue in result.issues" :key="issue">{{ issue }}</li>
                </ul>
              </div>
              <div>
                <strong>建议:</strong> {{ result.recommendation }}
              </div>
            </div>
          </el-collapse-item>
        </el-collapse>
      </template>
    </el-dialog>

    <!-- 报告对话框 -->
    <el-dialog
      v-model="reportVisible"
      title="评测报告"
      width="80%"
      destroy-on-close
    >
      <template v-if="report">
        <el-descriptions :column="3" border class="mb-4">
          <el-descriptions-item label="报告 ID">{{ report.reportId }}</el-descriptions-item>
          <el-descriptions-item label="生成时间">{{ formatTime(report.generatedAt) }}</el-descriptions-item>
          <el-descriptions-item label="评测数量">{{ report.totalTraces }}</el-descriptions-item>
          <el-descriptions-item label="平均得分">{{ (report.avgOverallScore * 100).toFixed(1) }}%</el-descriptions-item>
          <el-descriptions-item label="通过率">{{ (report.passRate * 100).toFixed(1) }}%</el-descriptions-item>
        </el-descriptions>

        <el-divider />

        <h4 class="mb-2">详细结果</h4>
        <el-table :data="report.results" stripe max-height="400">
          <el-table-column prop="traceId" label="Trace ID" width="180">
            <template #default="{ row }">
              {{ row.traceId?.slice(0, 8) }}...
            </template>
          </el-table-column>
          <el-table-column prop="overallScore" label="得分" width="100">
            <template #default="{ row }">
              {{ (row.overallScore * 100).toFixed(1) }}%
            </template>
          </el-table-column>
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.overallScore >= 0.7 ? 'success' : 'danger'" size="small">
                {{ row.overallScore >= 0.7 ? '通过' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="recommendation" label="建议" show-overflow-tooltip />
        </el-table>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { observabilityApi, type EvaluationResult, type EvaluationReport } from '@/api/observability'

const evaluators = ref<{ name: string; description: string }[]>([])
const results = ref<EvaluationResult[]>([])
const report = ref<EvaluationReport | null>(null)
const traceIdInput = ref('')
const evaluating = ref(false)
const detailVisible = ref(false)
const reportVisible = ref(false)
const selectedResult = ref<EvaluationResult | null>(null)

const evaluationCount = computed(() => results.value.length)

const avgScore = computed(() => {
  if (results.value.length === 0) return 0
  return results.value.reduce((sum, r) => sum + r.overallScore, 0) / results.value.length
})

const passRate = computed(() => {
  if (results.value.length === 0) return 0
  return results.value.filter(r => r.overallScore >= 0.7).length / results.value.length
})

const loadEvaluators = async () => {
  try {
    evaluators.value = await observabilityApi.getEvaluators()
  } catch (error) {
    console.error('加载评测器列表失败', error)
  }
}

const runEvaluation = async () => {
  const traceIds = traceIdInput.value
    .split('\n')
    .map(id => id.trim())
    .filter(id => id.length > 0)

  if (traceIds.length === 0) {
    ElMessage.warning('请输入至少一个 Trace ID')
    return
  }

  evaluating.value = true
  try {
    const evalResults = await observabilityApi.evaluateBatch(traceIds)
    results.value = evalResults
    ElMessage.success(`评测完成，共 ${evalResults.length} 条结果`)
  } catch (error) {
    ElMessage.error('评测失败')
  } finally {
    evaluating.value = false
  }
}

const generateReport = async () => {
  if (results.value.length === 0) {
    ElMessage.warning('请先执行评测')
    return
  }

  try {
    const traceIds = results.value.map(r => r.traceId)
    report.value = await observabilityApi.generateReport(traceIds)
    reportVisible.value = true
  } catch (error) {
    ElMessage.error('生成报告失败')
  }
}

const showDetail = (result: EvaluationResult) => {
  selectedResult.value = result
  detailVisible.value = true
}

const getScoreColor = (score: number) => {
  if (score >= 0.8) return '#67C23A'
  if (score >= 0.6) return '#E6A23C'
  return '#F56C6C'
}

const formatTime = (time: string) => {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}

onMounted(() => {
  loadEvaluators()
})
</script>

<style scoped>
.mb-2 {
  margin-bottom: 8px;
}

.mb-4 {
  margin-bottom: 16px;
}

.gap-2 {
  gap: 8px;
}
</style>
