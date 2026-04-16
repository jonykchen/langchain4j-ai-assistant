<script setup lang="ts">
import { ElButton, ElIcon } from 'element-plus'
import { Plus, Delete } from '@element-plus/icons-vue'
import type { Conversation } from '@/types'

defineEmits<{
  newChat: []
  select: [id: string]
  delete: [id: string]
}>()

defineProps<{
  conversations: Conversation[]
  currentId: string | null
}>()
</script>

<template>
  <aside class="sidebar">
    <div class="sidebar-header">
      <h2 class="sidebar-title">AI Chat</h2>
      <ElButton type="primary" :icon="Plus" @click="$emit('newChat')">
        新对话
      </ElButton>
    </div>
    <div class="conversation-list">
      <div
        v-for="conv in conversations"
        :key="conv.id"
        class="conversation-item"
        :class="{ active: conv.id === currentId }"
        @click="$emit('select', conv.id)"
      >
        <span class="conversation-title">{{ conv.title }}</span>
        <ElButton
          text
          size="small"
          :icon="Delete"
          class="delete-btn"
          @click.stop="$emit('delete', conv.id)"
        />
      </div>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  width: 260px;
  background: #f9fafb;
  border-right: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid #e5e7eb;
}

.sidebar-title {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 12px;
  color: #1f2937;
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.conversation-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  margin-bottom: 4px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
}

.conversation-item:hover {
  background: #e5e7eb;
}

.conversation-item.active {
  background: #dbeafe;
}

.conversation-title {
  font-size: 14px;
  color: #374151;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.delete-btn {
  opacity: 0;
  transition: opacity 0.2s;
}

.conversation-item:hover .delete-btn {
  opacity: 1;
}
</style>
