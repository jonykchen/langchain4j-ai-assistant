/**
 * Agent API 模块
 *
 * 提供 Agent 执行相关的 API 调用：
 * - executeAgent: SSE 流式执行
 * - confirmOperation: 确认敏感操作
 * - cancelExecution: 取消执行
 * - listAgents: 获取可用 Agent 列表
 */

import type {
  AgentEvent,
  AgentMetadata,
  ExecuteRequest,
  ConfirmRequest
} from '@/types/agent'

const API_BASE = '/api/agent'

/** 从 localStorage 获取认证头 */
function getAuthHeaders(): Record<string, string> {
  const token = localStorage.getItem('access_token')
  return token ? { 'Authorization': `Bearer ${token}` } : {}
}

/**
 * 执行 Agent（SSE 流式响应）
 *
 * @param request 执行请求
 * @returns AsyncGenerator，每次 yield 一个 AgentEvent
 *
 * 使用方式：
 * for await (const event of executeAgent({ userInput: '检查模型健康状态' })) {
 *   console.log(event.eventType, event)
 * }
 */
export async function* executeAgent(request: ExecuteRequest): AsyncGenerator<AgentEvent> {
  const response = await fetch(`${API_BASE}/execute`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...getAuthHeaders()
    },
    body: JSON.stringify(request)
  })

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}))
    throw new Error(errorData.message || `HTTP error! status: ${response.status}`)
  }

  const reader = response.body?.getReader()
  if (!reader) {
    throw new Error('No response body')
  }

  const decoder = new TextDecoder()
  let buffer = ''

  try {
    let currentEvent: string | null = null
    let currentData: string = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })

      // 按换行分割
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line.startsWith('event:')) {
          // 事件类型行
          currentEvent = line.slice(6).trim()
        } else if (line.startsWith('data:')) {
          // 数据行
          const dataContent = line.slice(5)
          currentData += (currentData ? '\n' : '') + (dataContent.startsWith(' ') ? dataContent.slice(1) : dataContent)
        } else if (line === '' && currentEvent && currentData) {
          // 空行表示事件结束
          try {
            const parsed = JSON.parse(currentData) as AgentEvent
            yield parsed
          } catch (e) {
            console.warn('[AgentAPI] JSON 解析失败:', currentData, e)
          }
          // 重置
          currentEvent = null
          currentData = ''
        }
      }
    }

    // 处理剩余数据
    if (currentEvent && currentData) {
      try {
        const parsed = JSON.parse(currentData) as AgentEvent
        yield parsed
      } catch (e) {
        console.warn('[AgentAPI] JSON 解析失败:', currentData, e)
      }
    }
  } finally {
    reader.releaseLock()
  }
}

/**
 * 确认敏感操作
 */
export async function confirmOperation(request: ConfirmRequest): Promise<{ success: boolean; message: string }> {
  const response = await fetch(`${API_BASE}/confirm`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...getAuthHeaders()
    },
    body: JSON.stringify(request)
  })

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}))
    throw new Error(errorData.message || `HTTP error! status: ${response.status}`)
  }

  return response.json()
}

/**
 * 取消执行
 */
export async function cancelExecution(traceId: string): Promise<{ success: boolean; message: string }> {
  const response = await fetch(`${API_BASE}/cancel/${traceId}`, {
    method: 'POST',
    headers: {
      ...getAuthHeaders()
    }
  })

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}))
    throw new Error(errorData.message || `HTTP error! status: ${response.status}`)
  }

  return response.json()
}

/**
 * 获取可用 Agent 列表
 */
export async function listAgents(): Promise<AgentMetadata[]> {
  const response = await fetch(API_BASE + '/list', {
    headers: {
      ...getAuthHeaders()
    }
  })

  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`)
  }

  return response.json()
}
