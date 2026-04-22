<template>
  <el-dialog :model-value="visible" @update:model-value="$emit('update:visible', $event)" title="导出测试结果" width="480px">
    <el-form label-width="100px">
      <el-form-item label="测试类型">
        <el-select v-model="exportForm.type" style="width: 100%">
          <el-option label="全部" value="ALL" />
          <el-option label="E2E 测试" value="E2E" />
          <el-option label="性能测试" value="PERFORMANCE" />
          <el-option label="AI 模型测试" value="AI_MODEL" />
        </el-select>
      </el-form-item>
      <el-form-item label="导出格式">
        <el-radio-group v-model="exportForm.format">
          <el-radio-button value="json">JSON</el-radio-button>
          <el-radio-button value="csv">CSV</el-radio-button>
          <el-radio-button value="excel">Excel</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="时间范围">
        <el-date-picker
          v-model="dateRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DDTHH:mm:ss"
          style="width: 100%"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:visible', false)">取消</el-button>
      <el-button type="primary" @click="handleExport" :loading="exporting">导出</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'

defineProps<{ visible: boolean }>()
const emit = defineEmits<{
  'update:visible': [value: boolean]
  'export': [params: { type: string; format: string; from?: string; to?: string }]
}>()

const exporting = ref(false)
const dateRange = ref<string[]>([])

const exportForm = reactive({
  type: 'ALL',
  format: 'json'
})

async function handleExport() {
  exporting.value = true
  try {
    const params: any = {
      type: exportForm.type,
      format: exportForm.format
    }
    if (dateRange.value?.length === 2) {
      params.from = dateRange.value[0]
      params.to = dateRange.value[1]
    }
    emit('export', params)
  } finally {
    exporting.value = false
  }
}
</script>
