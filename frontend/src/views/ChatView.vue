<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Menu, Loading, Cpu, Setting } from '@element-plus/icons-vue'
import Sidebar from '@/components/Sidebar.vue'
import MessageList from '@/components/MessageList.vue'
import ChatInput from '@/components/ChatInput.vue'
import UserInfo from '@/components/UserInfo.vue'
import { useChatStore } from '@/stores/chat'
import { useAuthStore } from '@/stores/auth'
import { storeToRefs } from 'pinia'
import { computed } from 'vue'
import { useRouter } from 'vue-router'

const chatStore = useChatStore()
const authStore = useAuthStore()
const router = useRouter()

const { conversations, currentConversation, currentConversationId, currentMessages, isLoading } = storeToRefs(chatStore)

const isAdmin = computed(() => authStore.isAdmin)

// 移动端侧边栏可见性
const sidebarVisible = ref(true)

function handleNewChat() {
  chatStore.createConversation()
}

function handleSelect(id: string) {
  chatStore.selectConversation(id)
  if (window.innerWidth < 768) {
    sidebarVisible.value = false
  }
}

function handleDelete(id: string) {
  chatStore.deleteConversation(id)
}

function handleSend(message: string) {
  chatStore.sendUserMessage(message)
}

function handleRegenerate() {
  const messages = currentMessages.value
  if (messages.length < 2) return
  const lastUserMsg = [...messages].reverse().find(m => m.role === 'user')
  if (lastUserMsg?.content) {
    chatStore.sendUserMessage(lastUserMsg.content)
  }
}

function handleRename(id: string, title: string) {
  chatStore.renameConversation(id, title)
}

onMounted(() => {
  if (window.innerWidth < 768) {
    sidebarVisible.value = false
  }
})
</script>

<template>
  <div class="chat-container">
    <!-- 移动端侧边栏遮罩 -->
    <transition name="fade">
      <div
        v-if="sidebarVisible"
        class="sidebar-backdrop"
        @click="sidebarVisible = false"
      />
    </transition>

    <Sidebar
      :conversations="conversations"
      :current-id="currentConversationId"
      :class="['chat-sidebar', { 'sidebar-visible': sidebarVisible }]"
      @new-chat="handleNewChat"
      @select="handleSelect"
      @delete="handleDelete"
      @rename="handleRename"
    />

    <div class="chat-main">
      <!-- 顶部导航栏 -->
      <header class="chat-header">
        <div class="header-left">
          <el-button
            text
            class="menu-btn"
            @click="sidebarVisible = !sidebarVisible"
          >
            <el-icon><Menu /></el-icon>
          </el-button>
          <div class="header-title-wrapper">
            <h1 class="chat-title">{{ currentConversation?.title || 'AI 助手' }}</h1>
            <span v-if="isLoading" class="typing-indicator">
              <el-icon class="is-loading"><Loading /></el-icon>
              正在思考...
            </span>
          </div>
        </div>
        <div class="header-actions">
          <el-button
            text
            @click="router.push('/agent')"
            class="nav-btn"
          >
            <el-icon><Cpu /></el-icon>
            <span class="hide-on-mobile">Agent 执行</span>
          </el-button>
          <el-button
            v-if="isAdmin"
            text
            @click="router.push('/admin')"
            class="nav-btn"
          >
            <el-icon><Setting /></el-icon>
            <span class="hide-on-mobile">管理后台</span>
          </el-button>
          <el-divider direction="vertical" />
          <UserInfo />
        </div>
      </header>

      <!-- 消息区域 -->
      <MessageList
        v-if="currentConversation"
        :messages="currentMessages"
        @regenerate="handleRegenerate"
      />

      <!-- 输入区域 -->
      <ChatInput
        :disabled="isLoading"
        @send="handleSend"
      />
    </div>
  </div>
</template>

<style scoped>
.chat-container {
  display: flex;
  height: 100vh;
  background: var(--bg-secondary);
  overflow: hidden;
}

.chat-sidebar {
  flex-shrink: 0;
  transition: transform var(--duration-normal) var(--ease-out);
}

.sidebar-backdrop {
  display: none;
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

.chat-header {
  height: var(--header-height);
  padding: 0 var(--space-2xl);
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-primary);
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
  box-shadow: var(--shadow-sm);
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  min-width: 0;
}

.menu-btn {
  display: none;
  color: var(--text-secondary);
  border-radius: var(--radius-lg);
  transition: all var(--duration-fast) var(--ease-out);
}

.menu-btn:hover {
  background: var(--bg-hover);
  color: var(--color-primary);
}

.header-title-wrapper {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.chat-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 400px;
}

.typing-indicator {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--color-primary);
  background: var(--color-primary-light);
  padding: 4px 12px;
  border-radius: var(--radius-full);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.nav-btn {
  color: var(--text-secondary);
  border-radius: var(--radius-lg);
  transition: all var(--duration-fast) var(--ease-out);
}

.nav-btn:hover {
  color: var(--color-primary);
  background: var(--color-primary-light);
}

/* ===== 动画 ===== */
.fade-enter-active,
.fade-leave-active {
  transition: opacity var(--duration-normal) var(--ease-out);
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* ===== 移动端适配 ===== */
@media (max-width: 768px) {
  .chat-sidebar {
    position: fixed;
    left: 0;
    top: 0;
    bottom: 0;
    z-index: var(--z-drawer);
    transform: translateX(-100%);
    box-shadow: var(--shadow-lg);
  }

  .chat-sidebar.sidebar-visible {
    transform: translateX(0);
  }

  .sidebar-backdrop {
    display: block;
    position: fixed;
    inset: 0;
    background: var(--bg-overlay);
    z-index: var(--z-modal-backdrop);
  }

  .menu-btn {
    display: inline-flex;
  }

  .hide-on-mobile {
    display: none;
  }

  .chat-title {
    max-width: 200px;
  }

  .chat-header {
    padding: 0 var(--space-lg);
  }
}
</style>