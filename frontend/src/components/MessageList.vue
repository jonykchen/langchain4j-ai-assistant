<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import MessageItem from './MessageItem.vue'
import type { Message } from '@/types'

const props = defineProps<{
  messages: Message[]
}>()

const emit = defineEmits<{
  (e: 'regenerate'): void
}>()

const listRef = ref<HTMLElement | null>(null)

watch(
  () => props.messages.length,
  () => {
    nextTick(() => {
      if (listRef.value) {
        listRef.value.scrollTop = listRef.value.scrollHeight
      }
    })
  }
)
</script>

<template>
  <div ref="listRef" class="message-list" data-testid="message-list">
    <TransitionGroup name="message">
      <MessageItem
        v-for="message in messages"
        :key="message.id"
        :message="message"
        :data-testid="message.role === 'user' ? 'user-message' : 'assistant-message'"
        @regenerate="emit('regenerate')"
      />
    </TransitionGroup>
    <div v-if="messages.length === 0" class="empty-state">
      <el-icon class="empty-icon" :size="48" color="var(--text-tertiary)"><ChatDotRound /></el-icon>
      <div class="empty-text">开始新对话</div>
    </div>
  </div>
</template>

<style scoped>
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-xl);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--text-tertiary);
}

.empty-icon {
  margin-bottom: var(--space-md);
}

.empty-text {
  font-size: 16px;
}
</style>
