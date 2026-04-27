<template>
  <el-card v-if="result || loading" shadow="hover" class="execution-result-panel">
    <template #header>
      <div class="flex justify-between items-center">
        <span>执行结果</span>
        <el-tag v-if="result" :type="result.success ? 'success' : 'danger'">
          {{ result.success ? '成功' : '失败' }}
        </el-tag>
      </div>
    </template>

    <div v-loading="loading" class="result-content">
      <template v-if="result">
        <!-- Summary statistics -->
        <el-descriptions :column="4" border class="mb-4">
          <el-descriptions-item label="任务ID">
            <el-tooltip :content="result.taskId" placement="top">
              <span class="truncate">{{ result.taskId.slice(0, 8) }}...</span>
            </el-tooltip>
          </el-descriptions-item>
          <el-descriptions-item label="执行状态">
            <el-tag :type="result.success ? 'success' : 'danger'" size="small">
              {{ result.success ? '成功' : '失败' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="执行时长">{{ result.executionTimeMs }}ms</el-descriptions-item>
          <el-descriptions-item label="迭代次数">{{ result.iterations }}</el-descriptions-item>
        </el-descriptions>

        <!-- Output section -->
        <div v-if="result.output" class="mb-4">
          <div class="flex justify-between items-center mb-2">
            <strong>输出结果</strong>
            <el-button size="small" @click="copyOutput">复制</el-button>
          </div>
          <el-input
            type="textarea"
            :rows="4"
            :model-value="result.output"
            readonly
            class="output-textarea"
          />
        </div>

        <!-- Error section -->
        <el-alert v-if="result.error" type="error" :closable="false" class="mb-4">
          <template #title>错误信息</template>
          {{ result.error }}
        </el-alert>

        <!-- Step results timeline -->
        <div v-if="result.stepResults && result.stepResults.length > 0">
          <h4 class="mb-2">执行步骤</h4>
          <el-timeline>
            <el-timeline-item
              v-for="(step, index) in result.stepResults"
              :key="index"
              :type="step.success ? 'success' : 'danger'"
              :color="step.success ? '#67C23A' : '#F56C6C'"
              placement="top"
            >
              <template #dot>
                <el-icon :size="16">
                  <component :is="step.success ? 'CircleCheck' : 'CircleClose'" />
                </el-icon>
              </template>
              <el-card shadow="never" class="step-card">
                <template #header>
                  <div class="flex justify-between items-center">
                    <span>步骤 {{ index + 1 }}</span>
                    <el-tag :type="step.success ? 'success' : 'danger'" size="small">
                      {{ step.success ? '成功' : '失败' }}
                    </el-tag>
                  </div>
                </template>
                <el-collapse v-if="step.output || step.observation || step.error">
                  <el-collapse-item title="详情">
                    <div v-if="step.output" class="step-detail">
                      <strong>输出：</strong>
                      <pre class="step-output">{{ step.output }}</pre>
                    </div>
                    <div v-if="step.observation" class="step-detail">
                      <strong>观察：</strong>
                      <pre class="step-observation">{{ step.observation }}</pre>
                    </div>
                    <div v-if="step.error" class="step-detail">
                      <strong>错误：</strong>
                      <el-text type="danger">{{ step.error }}</el-text>
                    </div>
                  </el-collapse-item>
                </el-collapse>
              </el-card>
            </el-timeline-item>
          </el-timeline>
        </div>

        <!-- No step results -->
        <el-empty v-else-if="!result.output && !result.error" description="无执行结果" />
      </template>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
// @ts-expect-error Vue template uses these icons as dynamic components
import { CircleCheck, CircleClose } from '@element-plus/icons-vue'
import type { TaskResultInfo } from '@/api/planning'

const props = defineProps<{
  result: TaskResultInfo | null
  loading?: boolean
}>()

const copyOutput = async () => {
  if (props.result?.output) {
    try {
      await navigator.clipboard.writeText(props.result.output)
      ElMessage.success('已复制到剪贴板')
    } catch {
      ElMessage.error('复制失败')
    }
  }
}
</script>

<style scoped>
.execution-result-panel {
  margin-top: 16px;
}

.mb-2 {
  margin-bottom: 8px;
}

.mb-4 {
  margin-bottom: 16px;
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

.truncate {
  display: inline-block;
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.output-textarea :deep(textarea) {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  line-height: 1.5;
}

.step-card {
  margin-bottom: 8px;
}

.step-detail {
  margin-bottom: 8px;
}

.step-detail:last-child {
  margin-bottom: 0;
}

.step-output,
.step-observation {
  white-space: pre-wrap;
  word-break: break-word;
  margin: 4px 0 0 0;
  font-size: 13px;
  background: var(--bg-secondary);
  padding: 8px;
  border-radius: var(--radius-sm);
  max-height: 150px;
  overflow: auto;
  font-family: 'Consolas', 'Monaco', monospace;
}
</style>
