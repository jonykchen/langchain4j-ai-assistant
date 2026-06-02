<script setup lang="ts">
/**
 * Agent 输入组件
 *
 * 提供用户输入文本框和执行按钮，支持 Ctrl+Enter 快捷键。
 *
 * @author jonychen
 */
import { ref, computed } from 'vue'

const props = withDefaults(
  defineProps<{
    placeholder?: string
    disabled?: boolean
    loading?: boolean
  }>(),
  {
    placeholder: '输入您的指令，例如：查询上周 token 消耗',
    disabled: false,
    loading: false,
  }
)

const emit = defineEmits<{
  (e: 'submit', input: string): void
}>()

const inputValue = ref('')

const canSubmit = computed(() => {
  return inputValue.value.trim().length > 0 && !props.disabled && !props.loading
})

function handleSubmit() {
  if (canSubmit.value) {
    emit('submit', inputValue.value.trim())
    inputValue.value = ''
  }
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && event.ctrlKey) {
    event.preventDefault()
    handleSubmit()
  }
}
</script>

<template>
  <div class="agent-input">
    <el-input
      v-model="inputValue"
      type="textarea"
      :rows="3"
      :placeholder="placeholder"
      :disabled="disabled"
      @keydown="handleKeydown"
    />
    <el-button type="primary" :disabled="!canSubmit" :loading="loading" @click="handleSubmit">
      <el-icon v-if="!loading"><VideoPlay /></el-icon>
      <span>{{ loading ? '执行中...' : '开始执行' }}</span>
    </el-button>
  </div>
</template>

<style scoped>
.agent-input {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.agent-input :deep(.el-textarea__inner) {
  font-size: 14px;
  line-height: 1.6;
}

.el-button {
  align-self: flex-end;
  min-width: 120px;
}
</style>
