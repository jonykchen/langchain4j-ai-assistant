import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Conversation, Message } from '@/types'
import { sendMessage, streamMessage } from '@/api/chat'

const STORAGE_KEY = 'chat-conversations'

function generateId(): string {
  return Date.now().toString(36) + Math.random().toString(36).substr(2)
}

function saveToStorage(conversations: Conversation[]) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(conversations))
}

function loadFromStorage(): Conversation[] {
  const data = localStorage.getItem(STORAGE_KEY)
  if (data) {
    const parsed = JSON.parse(data)
    // 转换日期字符串为 Date 对象
    return parsed.map((conv: Conversation) => ({
      ...conv,
      createdAt: new Date(conv.createdAt),
      updatedAt: new Date(conv.updatedAt),
      messages: conv.messages.map((msg: Message) => ({
        ...msg,
        timestamp: new Date(msg.timestamp)
      }))
    }))
  }
  return []
}

export const useChatStore = defineStore('chat', () => {
  // 当前对话的消息列表（单独追踪，用于实时更新）
  const currentMessages = ref<Message[]>([])
  const conversations = ref<Conversation[]>(loadFromStorage())
  const currentConversationId = ref<string | null>(null)
  const isLoading = ref(false)

  const currentConversation = computed(() => {
    return conversations.value.find(c => c.id === currentConversationId.value)
  })

  const sortedConversations = computed(() => {
    return [...conversations.value].sort((a, b) =>
      b.updatedAt.getTime() - a.updatedAt.getTime()
    )
  })

  // 同步当前消息列表
  function syncCurrentMessages() {
    if (currentConversation.value) {
      currentMessages.value = currentConversation.value.messages
    } else {
      currentMessages.value = []
    }
  }

  function createConversation(): string {
    const id = generateId()
    const conversation: Conversation = {
      id,
      title: '新对话',
      messages: [],
      createdAt: new Date(),
      updatedAt: new Date(),
    }
    conversations.value.push(conversation)
    currentConversationId.value = id
    currentMessages.value = conversation.messages
    saveToStorage(conversations.value)
    return id
  }

  function deleteConversation(id: string) {
    const index = conversations.value.findIndex(c => c.id === id)
    if (index !== -1) {
      conversations.value.splice(index, 1)
      if (currentConversationId.value === id) {
        currentConversationId.value = conversations.value[0]?.id || null
        syncCurrentMessages()
      }
      saveToStorage(conversations.value)
    }
  }

  function selectConversation(id: string) {
    currentConversationId.value = id
    syncCurrentMessages()
  }

  function renameConversation(id: string, title: string) {
    const convIndex = conversations.value.findIndex(c => c.id === id)
    if (convIndex !== -1) {
      conversations.value[convIndex].title = title
      conversations.value[convIndex].updatedAt = new Date()
      saveToStorage(conversations.value)
    }
  }

  async function sendUserMessage(content: string, useStream: boolean = true) {
    // 确保有当前对话
    if (!currentConversationId.value) {
      createConversation()
    }

    const conversation = currentConversation.value!
    const convIndex = conversations.value.findIndex(c => c.id === conversation.id)

    // 添加用户消息
    const userMessage: Message = {
      id: generateId(),
      role: 'user',
      content,
      timestamp: new Date(),
    }
    conversation.messages.push(userMessage)
    currentMessages.value = [...conversation.messages]

    // 更新标题
    if (conversation.messages.length === 1) {
      conversation.title = content.slice(0, 30) + (content.length > 30 ? '...' : '')
    }

    // 添加助手消息占位
    const assistantMessageId = generateId()
    const assistantMessage: Message = {
      id: assistantMessageId,
      role: 'assistant',
      content: '',
      timestamp: new Date(),
      isStreaming: true,
    }
    conversation.messages.push(assistantMessage)
    conversation.updatedAt = new Date()
    currentMessages.value = [...conversation.messages]

    saveToStorage(conversations.value)
    isLoading.value = true

    // 获取助手消息的索引
    const msgIndex = conversation.messages.findIndex(m => m.id === assistantMessageId)

    try {
      if (useStream) {
        let fullContent = ''
        for await (const token of streamMessage(content)) {
          fullContent += token
          // 更新消息内容
          conversation.messages[msgIndex].content = fullContent
          // 触发响应式更新
          currentMessages.value = [...conversation.messages]
        }
      } else {
        conversation.messages[msgIndex].content = await sendMessage(content)
        currentMessages.value = [...conversation.messages]
      }
    } catch (error) {
      conversation.messages[msgIndex].content = '抱歉，发生了错误。请稍后重试。'
      currentMessages.value = [...conversation.messages]
      console.error('Chat error:', error)
    } finally {
      conversation.messages[msgIndex].isStreaming = false
      isLoading.value = false
      conversation.updatedAt = new Date()
      currentMessages.value = [...conversation.messages]
      conversations.value[convIndex] = { ...conversation }
      saveToStorage(conversations.value)
    }
  }

  // 初始化
  if (conversations.value.length === 0) {
    createConversation()
  } else if (!currentConversationId.value) {
    currentConversationId.value = conversations.value[0].id
    syncCurrentMessages()
  } else {
    syncCurrentMessages()
  }

  return {
    conversations: sortedConversations,
    currentConversation,
    currentConversationId,
    currentMessages,
    isLoading,
    createConversation,
    deleteConversation,
    selectConversation,
    renameConversation,
    sendUserMessage,
  }
})
