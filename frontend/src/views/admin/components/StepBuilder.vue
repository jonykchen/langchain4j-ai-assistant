<template>
  <div class="step-builder">
    <div v-for="(step, index) in steps" :key="index" class="step-item mb-4">
      <el-card shadow="never">
        <template #header>
          <div class="flex justify-between items-center">
            <span class="step-header">
              <el-tag type="info" size="small" effect="plain">步骤 {{ index + 1 }}</el-tag>
            </span>
            <el-button
              type="danger"
              size="small"
              :icon="Delete"
              circle
              @click="removeStep(index)"
            />
          </div>
        </template>

        <el-form label-width="80px" size="small">
          <el-form-item label="描述" required>
            <el-input v-model="step.description" placeholder="步骤描述" />
          </el-form-item>

          <el-form-item label="类型">
            <el-radio-group v-model="stepModes[index]" @change="onModeChange(index)">
              <el-radio value="action">执行动作</el-radio>
              <el-radio value="tool">调用工具</el-radio>
            </el-radio-group>
          </el-form-item>

          <template v-if="stepModes[index] === 'action'">
            <el-form-item label="动作" required>
              <el-input v-model="step.action" placeholder="具体执行动作，如：分析数据并生成报告" />
            </el-form-item>
          </template>

          <template v-else>
            <el-form-item label="工具名称" required>
              <el-input
                v-model="step.tool"
                placeholder="工具名称，如：search, calculator, weather"
              />
            </el-form-item>
            <el-form-item label="参数">
              <el-input
                type="textarea"
                :rows="3"
                :model-value="paramsToJson(step.params)"
                @update:model-value="updateParams(step, $event)"
                placeholder='JSON 格式参数，如: {"query": "hello"}'
                class="params-input"
              />
            </el-form-item>
          </template>
        </el-form>
      </el-card>
    </div>

    <el-button type="primary" @click="addStep" class="w-full" plain>
      <el-icon class="mr-1"><Plus /></el-icon>
      添加步骤
    </el-button>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { Delete, Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { StepDef } from '@/api/planning'

const props = defineProps<{
  steps: StepDef[]
}>()

const emit = defineEmits<{
  'update:steps': [steps: StepDef[]]
}>()

const steps = ref<StepDef[]>([...props.steps])
const stepModes = ref<('action' | 'tool')[]>(props.steps.map(s => (s.tool ? 'tool' : 'action')))

const addStep = () => {
  steps.value.push({
    description: '',
    action: '',
    tool: '',
    params: {},
  })
  stepModes.value.push('action')
  emit('update:steps', steps.value)
}

const removeStep = (index: number) => {
  if (steps.value.length === 1) {
    ElMessage.warning('至少保留一个步骤')
    return
  }
  steps.value.splice(index, 1)
  stepModes.value.splice(index, 1)
  emit('update:steps', steps.value)
}

const onModeChange = (index: number) => {
  const mode = stepModes.value[index]
  const step = steps.value[index]
  if (mode === 'action') {
    step.tool = ''
    step.params = {}
  } else {
    step.action = ''
  }
  emit('update:steps', steps.value)
}

const paramsToJson = (params?: Record<string, unknown>) => {
  if (!params || Object.keys(params).length === 0) return ''
  return JSON.stringify(params, null, 2)
}

const updateParams = (step: StepDef, jsonStr: string) => {
  try {
    step.params = jsonStr ? JSON.parse(jsonStr) : {}
  } catch {
    // Invalid JSON, keep previous value
  }
  emit('update:steps', steps.value)
}

watch(
  steps,
  newSteps => {
    emit('update:steps', newSteps)
  },
  { deep: true }
)

// Initialize
watch(
  () => props.steps,
  newSteps => {
    steps.value = [...newSteps]
    stepModes.value = newSteps.map(s => (s.tool ? 'tool' : 'action'))
  },
  { immediate: true }
)
</script>

<style scoped>
.step-builder {
  width: 100%;
}

.step-item {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.step-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mb-4 {
  margin-bottom: 16px;
}

.w-full {
  width: 100%;
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

.params-input :deep(textarea) {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
}
</style>
