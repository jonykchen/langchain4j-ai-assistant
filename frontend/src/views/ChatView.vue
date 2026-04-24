<script setup lang="ts">
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

function handleNewChat() {
  chatStore.createConversation()
}

function handleSelect(id: string) {
  chatStore.selectConversation(id)
}

function handleDelete(id: string) {
  chatStore.deleteConversation(id)
}

function handleSend(message: string) {
  chatStore.sendUserMessage(message)
}

function handleRegenerate() {
  // 获取最后一条用户消息，重新发送
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
</script>

<template>
  <div class="chat-container">
    <Sidebar
      :conversations="conversations"
      :current-id="currentConversationId"
      @new-chat="handleNewChat"
      @select="handleSelect"
      @delete="handleDelete"
      @rename="handleRename"
    />

    <div class="chat-main">
      <!-- 顶部导航栏 -->
      <header class="chat-header">
        <h1 class="chat-title">{{ currentConversation?.title || 'AI 助手' }}</h1>
        <div class="header-actions">
          <el-button
            text
            @click="router.push('/agent')"
            class="agent-btn"
          >
            <el-icon><Cpu /></el-icon>
            Agent 执行
          </el-button>
          <el-button
            v-if="isAdmin"
            text
            @click="router.push('/admin')"
            class="admin-btn"
          >
            <el-icon><Setting /></el-icon>
            管理后台
          </el-button>
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
  background: #fff;
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.chat-header {
  height: 56px;
  padding: 0 20px;
  border-bottom: 1px solid #e5e7eb;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}

.chat-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 400px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.admin-btn {
  color: #606266;
}

.admin-btn:hover {
  color: #409eff;
}
</style>
