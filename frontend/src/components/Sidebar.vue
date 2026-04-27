<script setup lang="ts">
import { ref, computed, nextTick } from 'vue'
import { ElButton, ElDialog, ElInput } from 'element-plus'
import { Plus, Delete, Search, Sunny, Moon } from '@element-plus/icons-vue'
import { useThemeStore } from '@/stores/theme'
import type { Conversation } from '@/types'

const themeStore = useThemeStore()

const emit = defineEmits<{
  newChat: []
  select: [id: string]
  delete: [id: string]
  rename: [id: string, title: string]
}>()

const props = defineProps<{
  conversations: Conversation[]
  currentId: string | null
}>()

// 搜索过滤
const searchQuery = ref('')

const filteredConversations = computed(() => {
  if (!searchQuery.value.trim()) return props.conversations
  const q = searchQuery.value.trim().toLowerCase()
  return props.conversations.filter(c => c.title.toLowerCase().includes(q))
})

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

      <!-- 搜索过滤 -->
      <div class="sidebar-search">
        <ElInput
          v-model="searchQuery"
          placeholder="搜索对话..."
          :prefix-icon="Search"
          clearable
          size="small"
        />
      </div>

      <div class="conversation-list">
        <div
          v-for="conv in filteredConversations"
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

        <!-- 空状态 -->
        <div v-if="filteredConversations.length === 0" class="list-empty">
          <p v-if="searchQuery">未找到匹配对话</p>
          <p v-else>暂无对话，点击上方按钮开始</p>
        </div>
      </div>

      <!-- 主题切换 -->
      <div class="sidebar-footer">
        <el-button text size="small" class="theme-toggle" @click="themeStore.toggle()">
          <el-icon class="theme-icon">
            <component :is="themeStore.effectiveTheme === 'dark' ? Sunny : Moon" />
          </el-icon>
          <span>{{ themeStore.effectiveTheme === 'dark' ? '浅色模式' : '深色模式' }}</span>
        </el-button>
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
  width: var(--sidebar-width);
  background: var(--bg-primary);
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  height: 100%;
  padding: var(--space-lg);
  gap: var(--space-lg);
}

.sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-lg);
  flex-shrink: 0;
}

.sidebar-title {
  font-size: 20px;
  font-weight: 700;
  margin: 0;
  color: var(--text-primary);
  letter-spacing: -0.3px;
}

.sidebar-search {
  flex-shrink: 0;
}

.sidebar-search :deep(.el-input__wrapper) {
  background: var(--bg-secondary);
  box-shadow: none !important;
  border-radius: var(--radius-lg);
  padding: 4px 12px;
}

.sidebar-search :deep(.el-input__inner) {
  background: transparent;
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  margin: 0 calc(-1 * var(--space-lg));
  padding: 0 var(--space-lg);
}

.conversation-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px var(--space-md);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: all 0.2s ease;
  border: 1px solid transparent;
  gap: var(--space-sm);
}

.conversation-item:hover {
  background: var(--bg-secondary);
  border-color: var(--border-light);
}

.conversation-item.active {
  background: var(--color-primary-light);
  border-color: var(--color-primary);
}

.conversation-title {
  font-size: 14px;
  color: var(--text-primary);
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
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-tertiary);
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.2s;
}

.conversation-item:hover .rename-btn,
.conversation-item.active .rename-btn {
  display: inline-flex;
}

.rename-btn:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.delete-btn {
  opacity: 0;
  transition: opacity 0.2s;
  flex-shrink: 0;
}

.conversation-item:hover .delete-btn {
  opacity: 1;
}

.list-empty {
  padding: var(--space-2xl) var(--space-md);
  text-align: center;
  color: var(--text-tertiary);
  font-size: 13px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-sm);
}

.sidebar-footer {
  padding-top: var(--space-md);
  border-top: 1px solid var(--border-color);
  display: flex;
  justify-content: center;
  flex-shrink: 0;
}

.theme-toggle {
  color: var(--text-secondary);
  width: 100%;
  justify-content: flex-start;
  padding: 8px 12px;
  border-radius: var(--radius-lg);
}

.theme-toggle:hover {
  color: var(--text-primary);
  background: var(--bg-secondary);
}

.theme-icon {
  margin-right: 6px;
}

/* 移动端：侧边栏默认隐藏，通过外部 class 控制 */
@media (max-width: 768px) {
  .sidebar {
    width: var(--sidebar-width);
  }
}
</style>
