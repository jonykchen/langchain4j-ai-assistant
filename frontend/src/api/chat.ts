/**
 * API 模块 - 与后端通信
 *
 * 提供两种对话模式：
 * 1. sendMessage: 同步模式，等待完整响应
 * 2. streamMessage: 流式模式，逐字返回（推荐）
 */

import type { ApiResponse, ChatRequest, ChatResponse } from '@/types'

/** API 基础路径，由 Vite 代理转发到后端 */
const API_BASE = '/api/chat'

/**
 * 同步发送消息
 *
 * @param message 用户消息
 * @returns AI 完整回复
 *
 * 使用场景：
 * - 需要完整响应后再处理
 * - 简单的问答场景
 *
 * 缺点：
 * - 等待时间长，用户体验差
 * - 长回复可能导致超时
 */
export async function sendMessage(message: string): Promise<string> {
  // 从 localStorage 获取 Token
  const token = localStorage.getItem('access_token')

  // fetch API 发送 POST 请求
  const response = await fetch(API_BASE, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    // 将消息封装为请求体
    body: JSON.stringify({ message } as ChatRequest),
  })

  // 检查响应状态
  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}))
    throw new Error(errorData.message || `HTTP error! status: ${response.status}`)
  }

  // 解析 JSON 响应
  const result: ApiResponse<ChatResponse> = await response.json()

  // 检查业务状态码
  if (result.code !== 200) {
    throw new Error(result.message || '请求失败')
  }

  return result.data?.reply || ''
}

/**
 * 流式发送消息（推荐）
 *
 * @param message 用户消息
 * @returns AsyncGenerator，每次 yield 一个 token
 *
 * 什么是 AsyncGenerator？
 * - 异步生成器函数，用 async function* 定义
 * - yield 返回值后，函数暂停，下次调用继续执行
 * - 适合处理流式数据（如 SSE）
 *
 * 使用方式：
 * for await (const token of streamMessage("你好")) {
 *   console.log(token) // 逐字打印
 * }
 *
 * SSE (Server-Sent Events) 协议说明：
 * - 服务器向客户端单向推送事件
 * - 基于 HTTP 长连接
 * - 响应 Content-Type: text/event-stream
 *
 * SSE 事件格式：
 * event: token        ← 事件类型
 * data: 你            ← 事件数据
 *                      ← 空行表示事件结束
 * event: done
 * data: [DONE]
 */
export async function* streamMessage(message: string): AsyncGenerator<string> {
  // 从 localStorage 获取 Token
  const token = localStorage.getItem('access_token')

  // 发送 POST 请求到流式接口
  const response = await fetch(`${API_BASE}/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({ message } as ChatRequest),
  })

  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`)
  }

  // 获取 ReadableStream 的 reader
  // ReadableStream 是 Fetch API 的流式响应接口
  const reader = response.body?.getReader()
  if (!reader) {
    throw new Error('No response body')
  }

  // TextDecoder 用于解码 Uint8Array 到字符串
  const decoder = new TextDecoder()

  // 缓冲区：存储未完整解析的内容
  // SSE 事件可能跨多个 chunk，需要缓冲拼接
  let buffer = ''

  try {
    /*
     * SSE 解析逻辑：
     *
     * 每个事件格式：
     * event:token\n
     * data:你好\n
     * \n (空行结束事件)
     *
     * 多行 data 情况：
     * data:第一行\n
     * data:第二行\n
     * \n
     * → 合并为 "第一行\n第二行"
     */
    let currentDataParts: string[] = []

    // 循环读取流
    while (true) {
      // reader.read() 返回 Promise
      // done: true 表示流结束
      // value: Uint8Array，本次读取的字节
      const { done, value } = await reader.read()
      if (done) break

      // 解码字节到字符串
      // { stream: true } 表示还有后续数据，保留未完整解码的字符
      buffer += decoder.decode(value, { stream: true })

      // 按换行符分割成行
      // 注意：split 后最后一行可能不完整，需要放回缓冲区
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      // 逐行处理
      for (const line of lines) {
        if (line.startsWith('event:')) {
          // event 行：事件类型（忽略，不需要处理）
          continue
        } else if (line.startsWith('data:')) {
          // data 行：事件数据
          // SSE 规范：data: 后可能有空格，需要去掉
          const dataContent = line.slice(5)
          currentDataParts.push(dataContent.startsWith(' ') ? dataContent.slice(1) : dataContent)
        } else if (line === '') {
          // 空行：事件结束，处理收集的数据
          if (currentDataParts.length > 0) {
            // 多行 data 用 \n 连接（SSE 规范）
            const fullData = currentDataParts.join('\n')
            // 跳过结束标记 [DONE]
            if (fullData !== '[DONE]') {
              // yield 返回 token，暂停函数
              // 外部调用 next() 后继续执行
              yield fullData
            }
          }
          // 重置，准备下一个事件
          currentDataParts = []
        }
        // 其他行（如 :comment 注释行）忽略
      }
    }

    // 处理缓冲区剩余内容（流结束时）
    if (currentDataParts.length > 0) {
      const fullData = currentDataParts.join('\n')
      if (fullData !== '[DONE]') {
        yield fullData
      }
    }
  } finally {
    // 释放 reader 锁，允许其他 reader 使用
    // 必须调用，否则流会被锁定
    reader.releaseLock()
  }
}

/*
 * ==================== 扩展知识点 ====================
 *
 * 1. AbortController（取消请求）
 *    const controller = new AbortController()
 *    fetch(url, { signal: controller.signal })
 *    controller.abort() // 取消请求
 *
 * 2. 重试机制
 *    async function fetchWithRetry(url, retries = 3) {
 *      for (let i = 0; i < retries; i++) {
 *        try { return await fetch(url) }
 *        catch (e) { if (i === retries - 1) throw e }
 *      }
 *    }
 *
 * 3. WebSocket（双向通信）
 *    const ws = new WebSocket('ws://localhost:8082/chat')
 *    ws.onmessage = (e) => console.log(e.data)
 *    ws.send('你好')
 *
 * 4. EventSource API（SSE 专用，但只支持 GET）
 *    const source = new EventSource('/api/chat/stream?msg=你好')
 *    source.addEventListener('token', (e) => console.log(e.data))
 */
