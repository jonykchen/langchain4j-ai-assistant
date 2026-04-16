<script setup lang="ts">
import Sidebar from '@/components/Sidebar.vue'
import MessageList from '@/components/MessageList.vue'
import ChatInput from '@/components/ChatInput.vue'
import { useChatStore } from '@/stores/chat'
import { storeToRefs } from 'pinia'

const chatStore = useChatStore()
const { conversations, currentConversation, currentConversationId, currentMessages, isLoading } = storeToRefs(chatStore)

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
</script>

<template>
  <div class="chat-container">
    <Sidebar
      :conversations="conversations"
      :current-id="currentConversationId"
      @new-chat="handleNewChat"
      @select="handleSelect"
      @delete="handleDelete"
    />
    <main class="chat-main">
      <div class="chat-header">
        <h1 class="chat-title">{{ currentConversation?.title || 'AI Chat' }}</h1>
      </div>
      <MessageList
        v-if="currentConversation"
        :messages="currentMessages"
      />
      <ChatInput
        @send="handleSend"
      />
    </main>
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
  padding: 16px 20px;
  border-bottom: 1px solid #e5e7eb;
  background: #fff;
}

.chat-title {
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
  max-width: 800px;
  margin: 0 auto;
}
</style>
