<script setup lang="ts">
import { ref } from 'vue'
import { ElButton, ElInput } from 'element-plus'

const props = defineProps<{
  disabled?: boolean
}>()

const emit = defineEmits<{
  send: [message: string]
}>()

const inputText = ref('')
const isComposing = ref(false)

function handleSend() {
  const text = inputText.value.trim()
  if (text) {
    emit('send', text)
    inputText.value = ''
  }
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey && !isComposing.value) {
    e.preventDefault()
    handleSend()
  }
}

function handleCompositionStart() {
  isComposing.value = true
}

function handleCompositionEnd() {
  isComposing.value = false
}
</script>

<template>
  <div class="chat-input-container">
    <div class="chat-input-wrapper">
      <ElInput
        v-model="inputText"
        type="textarea"
        :rows="1"
        :autosize="{ minRows: 1, maxRows: 4 }"
        placeholder="输入消息，按 Enter 发送..."
        :disabled="disabled"
        resize="none"
        @keydown="handleKeydown"
        @compositionstart="handleCompositionStart"
        @compositionend="handleCompositionEnd"
      />
      <ElButton
        type="primary"
        :disabled="!inputText.trim() || disabled"
        @click="handleSend"
      >
        发送
      </ElButton>
    </div>
  </div>
</template>

<style scoped>
.chat-input-container {
  padding: 16px 20px;
  background: #fff;
  border-top: 1px solid #e5e7eb;
}

.chat-input-wrapper {
  display: flex;
  gap: 12px;
  max-width: 800px;
  margin: 0 auto;
}

.chat-input-wrapper :deep(.el-textarea__inner) {
  font-size: 15px;
  padding: 10px 14px;
}
</style>
