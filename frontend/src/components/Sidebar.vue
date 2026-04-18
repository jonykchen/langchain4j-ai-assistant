<script setup lang="ts">
import { ref, computed, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton, ElDialog, ElInput, ElMessageBox } from 'element-plus'
import { Plus, Delete, SwitchButton, Setting, User, DataLine } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
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

const router = useRouter()
const authStore = useAuthStore()
const user = computed(() => authStore.user)
const isAdmin = computed(() => authStore.isAdmin)

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

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await authStore.logout()
    router.push('/login')
  } catch {
    // 用户取消
  }
}
</script>

<template>
  <div class="sidebar-wrapper">
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
          <button class="rename-btn" title="重命名" @click.stop="startEdit(conv)">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/><path d="m15 5 4 4"/></svg>
          </button>
          <ElButton
            text
            size="small"
            :icon="Delete"
            class="delete-btn"
            @click.stop="$emit('delete', conv.id)"
          />
        </div>
      </div>

      <!-- 底部用户区域 -->
      <div class="sidebar-footer">
        <div v-if="isAdmin" class="admin-entry" @click="router.push('/admin')">
          <el-icon><Setting /></el-icon>
          <span>后台管理</span>
        </div>
        <div class="user-entry" @click="handleLogout">
          <el-avatar :size="28" :src="user?.avatar" class="user-avatar">
            {{ user?.username?.charAt(0).toUpperCase() }}
          </el-avatar>
          <span class="user-name">{{ user?.nickname || user?.username }}</span>
          <el-icon class="logout-icon"><SwitchButton /></el-icon>
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

/* 底部用户区域 */
.sidebar-footer {
  border-top: 1px solid #e5e7eb;
  padding: 8px;
  flex-shrink: 0;
}

.admin-entry {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  color: #409eff;
  transition: background 0.2s;
}

.admin-entry:hover {
  background: #ecf5ff;
}

.user-entry {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
}

.user-entry:hover {
  background: #e5e7eb;
}

.user-avatar {
  background: #409eff;
  color: white;
  font-size: 12px;
  flex-shrink: 0;
}

.user-name {
  flex: 1;
  font-size: 14px;
  color: #374151;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.logout-icon {
  color: #909399;
  font-size: 16px;
  flex-shrink: 0;
}

.user-entry:hover .logout-icon {
  color: #f56c6c;
}
</style>
