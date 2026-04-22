<template>
  <div class="planning-view p-6">
    <!-- 统计卡片 -->
    <el-row :gutter="20" class="mb-6">
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="总执行次数" :value="executionCount" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="成功" :value="successCount">
            <template #suffix>
              <span class="text-green-500 text-sm">({{ successRate }}%)</span>
            </template>
          </el-statistic>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="失败" :value="failureCount">
            <template #suffix>
              <span class="text-red-500 text-sm">({{ failureRate }}%)</span>
            </template>
          </el-statistic>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="平均耗时" :value="avgExecutionTime" suffix="ms" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 主内容区 -->
    <el-row :gutter="20">
      <!-- 左侧：执行面板 -->
      <el-col :span="14">
        <el-card>
          <template #header>
            <div class="flex justify-between items-center">
              <span>任务执行</span>
              <el-radio-group v-model="strategy" size="small">
                <el-radio-button value="auto">自动选择</el-radio-button>
                <el-radio-button value="react">ReAct</el-radio-button>
                <el-radio-button value="plan-execute">Plan-Execute</el-radio-button>
                <el-radio-button value="predefined">预定义任务</el-radio-button>
              </el-radio-group>
            </div>
          </template>

          <!-- 输入区域 -->
          <el-form label-width="80px">
            <el-form-item :label="inputLabel">
              <el-input
                v-model="formData.goal"
                type="textarea"
                :rows="3"
                :placeholder="inputPlaceholder"
              />
            </el-form-item>

            <el-form-item label="Session ID">
              <el-input
                v-model="formData.sessionId"
                placeholder="可选，留空自动生成"
              />
            </el-form-item>
          </el-form>

          <!-- 步骤构建器（仅预定义模式显示） -->
          <div v-if="strategy === 'predefined'" class="step-builder-wrapper mb-4">
            <el-divider content-position="left">任务步骤</el-divider>
            <StepBuilder v-model:steps="formData.steps" />
          </div>

          <!-- 操作按钮 -->
          <div class="action-buttons mt-4">
            <el-button type="primary" @click="execute" :loading="loading">
              <el-icon class="mr-1"><VideoPlay /></el-icon>
              执行任务
            </el-button>
            <el-button @click="reset">
              <el-icon class="mr-1"><RefreshRight /></el-icon>
              重置
            </el-button>
          </div>
        </el-card>

        <!-- 执行结果 -->
        <ExecutionResultPanel :result="currentResult" :loading="loading" />
      </el-col>

      <!-- 右侧：历史面板 -->
      <el-col :span="10">
        <ExecutionHistoryPanel
          :history="executionHistory"
          v-model:selected-id="selectedHistoryId"
          @select="loadFromHistory"
          @clear="clearHistory"
        />
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { VideoPlay, RefreshRight } from '@element-plus/icons-vue'
import {
  planningApi,
  type TaskResultInfo,
  type StepDef,
  type ExecutionHistoryItem,
  type Strategy
} from '@/api/planning'
import ExecutionResultPanel from './components/ExecutionResultPanel.vue'
import ExecutionHistoryPanel from './components/ExecutionHistoryPanel.vue'
import StepBuilder from './components/StepBuilder.vue'

// 策略选择
const strategy = ref<Strategy>('auto')

// 表单数据
const formData = ref({
  goal: '',
  sessionId: '',
  steps: [] as StepDef[]
})

// 执行状态
const loading = ref(false)
const currentResult = ref<TaskResultInfo | null>(null)
const executionHistory = ref<ExecutionHistoryItem[]>([])
const selectedHistoryId = ref('')

// 计算属性：统计信息
const executionCount = computed(() => executionHistory.value.length)
const successCount = computed(() => executionHistory.value.filter(h => h.result.success).length)
const failureCount = computed(() => executionCount.value - successCount.value)
const successRate = computed(() =>
  executionCount.value > 0 ? Math.round((successCount.value / executionCount.value) * 100) : 0
)
const failureRate = computed(() =>
  executionCount.value > 0 ? Math.round((failureCount.value / executionCount.value) * 100) : 0
)
const avgExecutionTime = computed(() => {
  if (executionHistory.value.length === 0) return 0
  return Math.round(
    executionHistory.value.reduce((sum, h) => sum + h.result.executionTimeMs, 0) / executionHistory.value.length
  )
})

// 计算属性：输入标签和占位符
const inputLabel = computed(() => {
  return strategy.value === 'react' ? '问题' : '目标'
})

const inputPlaceholder = computed(() => {
  const placeholders: Record<Strategy, string> = {
    auto: '请输入任务目标，系统将自动选择最优执行策略...',
    react: '请输入需要推理的问题，系统将通过思考-行动循环逐步解决...',
    'plan-execute': '请输入复杂的任务目标，系统将先规划再执行...',
    predefined: '请输入预定义任务的目标...'
  }
  return placeholders[strategy.value]
})

// 执行任务
const execute = async () => {
  if (!validateForm()) return

  loading.value = true
  try {
    const sessionId = formData.value.sessionId || generateSessionId()
    let result: TaskResultInfo

    switch (strategy.value) {
      case 'auto':
        result = await planningApi.execute({
          goal: formData.value.goal,
          sessionId
        })
        break
      case 'react':
        result = await planningApi.executeReAct({
          question: formData.value.goal,
          sessionId
        })
        break
      case 'plan-execute':
        result = await planningApi.executePlanExecute({
          goal: formData.value.goal,
          sessionId
        })
        break
      case 'predefined':
        result = await planningApi.executePredefinedTask({
          goal: formData.value.goal,
          sessionId,
          steps: formData.value.steps
        })
        break
      default:
        throw new Error('未知的执行策略')
    }

    currentResult.value = result
    addToHistory(result)
    ElMessage.success('执行完成')
  } catch (error: any) {
    ElMessage.error('执行失败: ' + (error.message || '未知错误'))
    console.error('Planning execution failed:', error)
  } finally {
    loading.value = false
  }
}

// 表单验证
const validateForm = () => {
  if (!formData.value.goal.trim()) {
    ElMessage.warning(strategy.value === 'react' ? '请输入问题' : '请输入目标')
    return false
  }
  if (strategy.value === 'predefined' && formData.value.steps.length === 0) {
    ElMessage.warning('请添加至少一个步骤')
    return false
  }
  if (strategy.value === 'predefined') {
    // 验证步骤
    for (let i = 0; i < formData.value.steps.length; i++) {
      const step = formData.value.steps[i]
      if (!step.description) {
        ElMessage.warning(`步骤 ${i + 1} 缺少描述`)
        return false
      }
      if (step.tool && !step.action && !step.tool) {
        ElMessage.warning(`步骤 ${i + 1} 缺少动作或工具`)
        return false
      }
    }
  }
  return true
}

// 生成会话 ID
const generateSessionId = () => {
  return `session-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`
}

// 添加到历史记录
const addToHistory = (result: TaskResultInfo) => {
  const item: ExecutionHistoryItem = {
    id: result.taskId,
    timestamp: new Date().toISOString(),
    strategy: strategy.value,
    goal: formData.value.goal,
    result
  }

  executionHistory.value.unshift(item)

  // 保留最近 20 条
  if (executionHistory.value.length > 20) {
    executionHistory.value = executionHistory.value.slice(0, 20)
  }

  saveHistoryToStorage()
}

// 从历史加载
const loadFromHistory = (item: ExecutionHistoryItem) => {
  strategy.value = item.strategy
  formData.value.goal = item.goal
  currentResult.value = item.result
  selectedHistoryId.value = item.id
}

// 清空历史
const clearHistory = () => {
  executionHistory.value = []
  selectedHistoryId.value = ''
  localStorage.removeItem('planning_history')
}

// 重置表单
const reset = () => {
  formData.value = {
    goal: '',
    sessionId: '',
    steps: []
  }
  currentResult.value = null
  selectedHistoryId.value = ''
}

// 保存历史到 localStorage
const saveHistoryToStorage = () => {
  try {
    localStorage.setItem('planning_history', JSON.stringify(executionHistory.value))
  } catch (e) {
    console.warn('Failed to save planning history:', e)
  }
}

// 从 localStorage 加载历史
const loadHistoryFromStorage = () => {
  const stored = localStorage.getItem('planning_history')
  if (stored) {
    try {
      const parsed = JSON.parse(stored) as ExecutionHistoryItem[]
      // 验证数据格式
      if (Array.isArray(parsed)) {
        executionHistory.value = parsed.filter(item =>
          item.id && item.timestamp && item.strategy && item.result
        )
      }
    } catch (e) {
      console.warn('Failed to parse planning history:', e)
    }
  }
}

// 监听策略变化，清空步骤（非预定义模式）
watch(strategy, (newStrategy) => {
  if (newStrategy !== 'predefined') {
    formData.value.steps = []
  }
})

// 初始化
onMounted(() => {
  loadHistoryFromStorage()
})
</script>

<style scoped>
.planning-view {
  min-height: 100%;
  background: #f5f7fa;
}

.mb-4 {
  margin-bottom: 16px;
}

.mb-6 {
  margin-bottom: 24px;
}

.mt-4 {
  margin-top: 16px;
}

.mr-1 {
  margin-right: 4px;
}

.flex {
  display: flex;
}

.justify-between {
  justify-content: space-between;
}

.items-center {
  align-items: center;
}

.text-green-500 {
  color: #67C23A;
}

.text-red-500 {
  color: #F56C6C;
}

.text-sm {
  font-size: 12px;
}

.step-builder-wrapper {
  margin-top: 16px;
}

.action-buttons {
  display: flex;
  gap: 8px;
}
</style>
