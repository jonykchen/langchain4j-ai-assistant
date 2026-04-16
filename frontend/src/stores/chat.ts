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
    return JSON.parse(data)
  }
  return []
}

export const useChatStore = defineStore('chat', () => {
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
    saveToStorage(conversations.value)
    return id
  }

  function deleteConversation(id: string) {
    const index = conversations.value.findIndex(c => c.id === id)
    if (index !== -1) {
      conversations.value.splice(index, 1)
      if (currentConversationId.value === id) {
        currentConversationId.value = conversations.value[0]?.id || null
      }
      saveToStorage(conversations.value)
    }
  }

  function selectConversation(id: string) {
    currentConversationId.value = id
  }

  async function sendUserMessage(content: string, useStream: boolean = true) {
    if (!currentConversation.value) {
      createConversation()
    }

    const conversation = currentConversation.value!
    const userMessage: Message = {
      id: generateId(),
      role: 'user',
      content,
      timestamp: new Date(),
    }

    conversation.messages.push(userMessage)

    // Update title from first message
    if (conversation.messages.length === 1) {
      conversation.title = content.slice(0, 30) + (content.length > 30 ? '...' : '')
    }

    const assistantMessage: Message = {
      id: generateId(),
      role: 'assistant',
      content: '',
      timestamp: new Date(),
      isStreaming: true,
    }
    conversation.messages.push(assistantMessage)
    conversation.updatedAt = new Date()
    saveToStorage(conversations.value)

    isLoading.value = true

    try {
      if (useStream) {
        for await (const token of streamMessage(content)) {
          assistantMessage.content += token
          saveToStorage(conversations.value)
        }
      } else {
        assistantMessage.content = await sendMessage(content)
      }
    } catch (error) {
      assistantMessage.content = '抱歉，发生了错误。请稍后重试。'
      console.error('Chat error:', error)
    } finally {
      assistantMessage.isStreaming = false
      isLoading.value = false
      conversation.updatedAt = new Date()
      saveToStorage(conversations.value)
    }
  }

  // Initialize: create conversation if none exists
  if (conversations.value.length === 0) {
    createConversation()
  } else if (!currentConversationId.value) {
    currentConversationId.value = conversations.value[0].id
  }

  return {
    conversations: sortedConversations,
    currentConversation,
    currentConversationId,
    isLoading,
    createConversation,
    deleteConversation,
    selectConversation,
    sendUserMessage,
  }
})
