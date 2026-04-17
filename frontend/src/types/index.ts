/**
 * TypeScript 类型定义
 *
 * 为什么使用 TypeScript？
 * 1. 类型安全：编译时发现错误
 * 2. IDE 支持：自动补全、类型提示
 * 3. 代码可读性：类型定义即文档
 * 4. 重构友好：修改类型自动提示所有使用处
 */

/**
 * 消息类型
 *
 * 表示一条聊天消息（用户或 AI）
 */
export interface Message {
  /** 消息唯一 ID，用于 Vue 的 v-for key 和消息定位 */
  id: string

  /**
   * 消息角色
   * - 'user': 用户发送的消息
   * - 'assistant': AI 助手的回复
   */
  role: 'user' | 'assistant'

  /** 消息文本内容（支持 Markdown） */
  content: string

  /** 消息发送时间 */
  timestamp: Date

  /**
   * 是否正在流式输出
   * - true: 消息还在生成中（显示加载动画）
   * - false/undefined: 消息已完成
   *
   * 可选字段：undefined 表示已完成
   */
  isStreaming?: boolean
}

/**
 * 对话类型
 *
 * 表示一个完整的对话会话，包含多条消息
 */
export interface Conversation {
  /** 对话唯一 ID */
  id: string

  /**
   * 对话标题
   * - 默认："新对话"
   * - 通常用第一条用户消息的前 30 字符作为标题
   */
  title: string

  /** 对话中的所有消息 */
  messages: Message[]

  /** 对话创建时间 */
  createdAt: Date

  /** 对话最后更新时间（用于排序） */
  updatedAt: Date
}

/**
 * 聊天请求类型
 *
 * 发送给后端 API 的请求体结构
 */
export interface ChatRequest {
  /** 用户输入的消息内容 */
  message: string
}

/**
 * 聊天响应类型
 *
 * 后端 API 返回的响应体结构（同步模式）
 */
export interface ChatResponse {
  /** AI 的完整回复内容 */
  reply: string
}

/**
 * 统一 API 响应格式
 *
 * 后端所有接口返回的统一结构
 */
export interface ApiResponse<T> {
  /** 状态码：200 成功，4xx 客户端错误，5xx 服务端错误 */
  code: number
  /** 响应消息 */
  message: string
  /** 响应数据 */
  data: T | null
}

/*
 * ==================== 扩展类型设计 ====================
 *
 * 1. 用户信息类型
 * export interface User {
 *   id: string
 *   name: string
 *   avatar?: string
 * }
 *
 * 2. 消息扩展（多模态）
 * export interface Message {
 *   id: string
 *   role: 'user' | 'assistant' | 'system'
 *   content: string | ImageContent | AudioContent
 *   attachments?: Attachment[]
 * }
 *
 * export interface ImageContent {
 *   type: 'image'
 *   url: string
 *   alt?: string
 * }
 *
 * 3. 消息状态
 * export interface Message {
 *   status: 'pending' | 'streaming' | 'completed' | 'error'
 *   error?: string
 * }
 *
 * 4. 点赞/点踩
 * export interface Message {
 *   feedback?: 'like' | 'dislike'
 *   feedbackReason?: string
 * }
 */