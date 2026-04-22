<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { ElButton, ElDialog, ElInput } from 'element-plus'
import { Plus, Delete } from '@element-plus/icons-vue'
import type { Conversation } from '@/types'

const emit = defineEmits<{
  newChat: []
  select: [id: string]
  delete: [id: string]
  rename: [id: string, title: string]
}>()

defineProps<{
  conversations: Conversation[]
  currentId: string | null
}>()

// 重命名弹窗
const renameVisible = ref(false)
const renameId = ref<string | null>(null)
const renameTitle = ref('')
const renameInputRef = ref<InstanceType<typeof ElInput>>()

function startEdit(conv: Conversation) {
  renameId.value = conv.id
  renameTitle.value = conv.title
  renameVisible.value = true
  nextTick(() => {
    renameInputRef.value?.focus()
    renameInputRef.value?.select()
  })
}

function confirmRename() {
  if (renameId.value && renameTitle.value.trim()) {
    emit('rename', renameId.value, renameTitle.value.trim())
  }
  closeRename()
}

function closeRename() {
  renameVisible.value = false
  renameId.value = null
  renameTitle.value = ''
}
</script>

<template>
  <div class="sidebar-wrapper">
    <aside class="sidebar" data-testid="sidebar">
      <div class="sidebar-header">
        <h2 class="sidebar-title">AI Chat</h2>
        <ElButton type="primary" :icon="Plus" data-testid="new-conversation" @click="$emit('newChat')">
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
          <button class="rename-btn" title="重命名" @click.stop="startEdit(conv)">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/><path d="m15 5 4 4"/></svg>
          </button>
          <ElButton
            text
            size="small"
            :icon="Delete"
            class="delete-btn"
            :aria-label="'删除对话: ' + conv.title"
            title="删除对话"
            @click.stop="$emit('delete', conv.id)"
          />
        </div>
      </div>
    </aside>

    <!-- 重命名弹窗 -->
    <Teleport to="body">
      <ElDialog
        v-model="renameVisible"
        title="重命名对话"
        width="400px"
        :align-center="true"
        :close-on-click-modal="true"
        destroy-on-close
      >
        <ElInput
          ref="renameInputRef"
          v-model="renameTitle"
          placeholder="请输入对话名称"
          maxlength="50"
          show-word-limit
          clearable
          @keyup.enter="confirmRename"
        />
        <template #footer>
          <span class="dialog-footer">
            <ElButton @click="closeRename">取消</ElButton>
            <ElButton type="primary" :disabled="!renameTitle.trim()" @click="confirmRename">确定</ElButton>
          </span>
        </template>
      </ElDialog>
    </Teleport>
  </div>
</template>

<style scoped>
.sidebar-wrapper {
  display: flex;
  flex-direction: column;
  height: 100%;
}

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

.rename-btn {
  display: none;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: #909399;
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.2s;
}

.conversation-item:hover .rename-btn,
.conversation-item.active .rename-btn {
  display: inline-flex;
}

.rename-btn:hover {
  background: rgba(0, 0, 0, 0.06);
  color: #333;
}

.delete-btn {
  opacity: 0;
  transition: opacity 0.2s;
}

.conversation-item:hover .delete-btn {
  opacity: 1;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
