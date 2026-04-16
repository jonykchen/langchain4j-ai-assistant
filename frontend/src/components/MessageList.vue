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
  <div ref="listRef" class="message-list">
    <TransitionGroup name="message">
      <MessageItem
        v-for="(message, index) in messages"
        :key="message.id"
        :message="message"
        @regenerate="emit('regenerate')"
      />
    </TransitionGroup>
    <div v-if="messages.length === 0" class="empty-state">
      <div class="empty-icon">💬</div>
      <div class="empty-text">开始新对话</div>
    </div>
  </div>
</template>

<style scoped>
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #909399;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.empty-text {
  font-size: 16px;
}
</style>
