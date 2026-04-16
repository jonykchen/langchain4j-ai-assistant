/**
 * Pinia 状态管理 - 聊天数据存储
 *
 * 职责：
 * 1. 管理对话列表和消息
 * 2. 持久化到 localStorage
 * 3. 处理消息发送和流式响应
 *
 * Pinia vs Vuex：
 * - Pinia 是 Vue 3 官方推荐的状态管理库
 * - 更简洁的 API，无需 mutations
 * - 更好的 TypeScript 支持
 */

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Conversation, Message } from '@/types'
import { sendMessage, streamMessage } from '@/api/chat'

/** localStorage 存储键 */
const STORAGE_KEY = 'chat-conversations'

/**
 * 生成唯一 ID
 * 使用时间戳 + 随机数
 */
function generateId(): string {
  return Date.now().toString(36) + Math.random().toString(36).substr(2)
}

/**
 * 保存对话数据到 localStorage
 *
 * 注意：
 * - localStorage 只能存储字符串
 * - Date 对象会被转为字符串，读取时需转换回来
 */
function saveToStorage(conversations: Conversation[]) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(conversations))
}

/**
 * 从 localStorage 加载对话数据
 *
 * 关键：将日期字符串转换回 Date 对象
 * JSON.parse 不会自动还原 Date 类型
 */
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

/**
 * defineStore 定义 Store
 *
 * 参数：
 * - 'chat': Store ID，必须唯一
 * - setup 函数：返回 state、getters、actions
 *
 * 等价于 Options API 方式：
 * defineStore('chat', {
 *   state: () => ({ ... }),
 *   getters: { ... },
 *   actions: { ... }
 * })
 */
export const useChatStore = defineStore('chat', () => {
  // ==================== State ====================

  /**
   * 当前对话的消息列表
   *
   * 为什么需要单独的 ref？
   * - computed 对嵌套属性变化不敏感
   * - 直接修改数组元素不会触发响应式更新
   * - 使用独立 ref + 创建新数组可以强制触发更新
   *
   * currentMessages.value = [...conversation.messages]
   */
  const currentMessages = ref<Message[]>([])

  /** 所有对话列表 */
  const conversations = ref<Conversation[]>(loadFromStorage())

  /** 当前对话 ID */
  const currentConversationId = ref<string | null>(null)

  /** 是否正在加载（发送消息中） */
  const isLoading = ref(false)

  // ==================== Getters ====================

  /** 当前对话对象 */
  const currentConversation = computed(() => {
    return conversations.value.find(c => c.id === currentConversationId.value)
  })

  /** 按更新时间排序的对话列表（最新的在前） */
  const sortedConversations = computed(() => {
    return [...conversations.value].sort((a, b) =>
      b.updatedAt.getTime() - a.updatedAt.getTime()
    )
  })

  // ==================== Actions ====================

  /**
   * 同步当前消息列表
   * 用于切换对话或初始化时
   */
  function syncCurrentMessages() {
    if (currentConversation.value) {
      currentMessages.value = currentConversation.value.messages
    } else {
      currentMessages.value = []
    }
  }

  /**
   * 创建新对话
   * @returns 新对话的 ID
   */
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

  /**
   * 删除对话
   */
  function deleteConversation(id: string) {
    const index = conversations.value.findIndex(c => c.id === id)
    if (index !== -1) {
      conversations.value.splice(index, 1)
      // 如果删除的是当前对话，切换到第一个
      if (currentConversationId.value === id) {
        currentConversationId.value = conversations.value[0]?.id || null
        syncCurrentMessages()
      }
      saveToStorage(conversations.value)
    }
  }

  /**
   * 切换对话
   */
  function selectConversation(id: string) {
    currentConversationId.value = id
    syncCurrentMessages()
  }

  /**
   * 重命名对话
   */
  function renameConversation(id: string, title: string) {
    const convIndex = conversations.value.findIndex(c => c.id === id)
    if (convIndex !== -1) {
      conversations.value[convIndex].title = title
      conversations.value[convIndex].updatedAt = new Date()
      saveToStorage(conversations.value)
    }
  }

  /**
   * 发送用户消息
   *
   * @param content 用户输入的消息内容
   * @param useStream 是否使用流式响应（默认 true）
   *
   * 流程：
   * 1. 添加用户消息到列表
   * 2. 添加助手消息占位（空内容 + isStreaming）
   * 3. 调用 API 获取响应
   * 4. 更新助手消息内容
   * 5. 保存到 localStorage
   */
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
    // 触发响应式更新：创建新数组
    currentMessages.value = [...conversation.messages]

    // 首条消息时，用消息内容作为对话标题
    if (conversation.messages.length === 1) {
      conversation.title = content.slice(0, 30) + (content.length > 30 ? '...' : '')
    }

    // 添加助手消息占位
    // 先显示空消息，流式更新内容
    const assistantMessageId = generateId()
    const assistantMessage: Message = {
      id: assistantMessageId,
      role: 'assistant',
      content: '',
      timestamp: new Date(),
      isStreaming: true, // 流式标记，用于显示加载动画
    }
    conversation.messages.push(assistantMessage)
    conversation.updatedAt = new Date()
    currentMessages.value = [...conversation.messages]

    saveToStorage(conversations.value)
    isLoading.value = true

    // 获取助手消息的索引（用于后续更新）
    const msgIndex = conversation.messages.findIndex(m => m.id === assistantMessageId)

    try {
      if (useStream) {
        // 流式响应：逐字更新
        let fullContent = ''
        // for await 遍历 AsyncGenerator
        for await (const token of streamMessage(content)) {
          fullContent += token
          // 更新消息内容
          conversation.messages[msgIndex].content = fullContent
          // 触发响应式更新
          currentMessages.value = [...conversation.messages]
        }
      } else {
        // 同步响应：等待完整回复
        conversation.messages[msgIndex].content = await sendMessage(content)
        currentMessages.value = [...conversation.messages]
      }
    } catch (error) {
      // 错误处理
      conversation.messages[msgIndex].content = '抱歉，发生了错误。请稍后重试。'
      currentMessages.value = [...conversation.messages]
      console.error('Chat error:', error)
    } finally {
      // 完成后更新状态
      conversation.messages[msgIndex].isStreaming = false
      isLoading.value = false
      conversation.updatedAt = new Date()
      currentMessages.value = [...conversation.messages]
      // 更新 conversations 数组中的引用
      conversations.value[convIndex] = { ...conversation }
      saveToStorage(conversations.value)
    }
  }

  // ==================== 初始化 ====================

  // 首次加载时的初始化逻辑
  if (conversations.value.length === 0) {
    // 没有对话，创建一个新对话
    createConversation()
  } else if (!currentConversationId.value) {
    // 有对话但没有选中，选中第一个
    currentConversationId.value = conversations.value[0].id
    syncCurrentMessages()
  } else {
    // 恢复上次选中的对话
    syncCurrentMessages()
  }

  // ==================== 导出 ====================

  return {
    // State
    conversations: sortedConversations,
    currentConversation,
    currentConversationId,
    currentMessages,
    isLoading,
    // Actions
    createConversation,
    deleteConversation,
    selectConversation,
    renameConversation,
    sendUserMessage,
  }
})

/*
 * ==================== 扩展知识点 ====================
 *
 * 1. 响应式陷阱
 *    问题：直接修改数组元素不触发更新
 *    解决：
 *    - 创建新数组：arr = [...arr]
 *    - 使用 splice：arr.splice(index, 1, newItem)
 *
 * 2. 数据持久化方案
 *    - localStorage: 简单，但有 5MB 限制
 *    - IndexedDB: 容量大，支持复杂查询
 *    - Pinia 插件：pinia-plugin-persistedstate
 *
 * 3. 会话管理
 *    - 按用户隔离：userId 作为 key
 *    - 服务端存储：对话历史保存到数据库
 *
 * 4. 性能优化
 *    - 虚拟滚动：消息过多时只渲染可见区域
 *    - 分页加载：历史消息懒加载
 */