import type { ChatRequest, ChatResponse } from '@/types'

const API_BASE = '/api/chat'

export async function sendMessage(message: string): Promise<string> {
  const response = await fetch(API_BASE, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ message } as ChatRequest),
  })

  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`)
  }

  const data: ChatResponse = await response.json()
  return data.reply
}

export async function* streamMessage(message: string): AsyncGenerator<string> {
  const response = await fetch(`${API_BASE}/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ message } as ChatRequest),
  })

  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`)
  }

  const reader = response.body?.getReader()
  if (!reader) {
    throw new Error('No response body')
  }

  const decoder = new TextDecoder()
  let buffer = ''

  try {
    // SSE 规范：每个事件以空行(\n\n)分隔
    // 事件内可能有多行 data: 字段，需要合并
    // 示例：
    //   event:token\n  (event 行)
    //   data:你好\n    (data 行)
    //   \n             (空行，事件结束)
    let currentDataParts: string[] = []

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line.startsWith('event:')) {
          // 忽略 event 行，不做处理
          continue
        } else if (line.startsWith('data:')) {
          // data: 后面可能有一个空格，按 SSE 规范应去掉前导空格
          const dataContent = line.slice(5)
          currentDataParts.push(dataContent.startsWith(' ') ? dataContent.slice(1) : dataContent)
        } else if (line === '') {
          // 空行 = 事件结束，处理收集到的事件
          if (currentDataParts.length > 0) {
            // SSE 规范：多行 data 用 \n 连接
            const fullData = currentDataParts.join('\n')
            if (fullData !== '[DONE]') {
              yield fullData
            }
          }
          // 重置事件状态
          currentDataParts = []
        }
        // 其他行（如注释行以冒号开头）忽略
      }
    }

    // 处理缓冲区中剩余内容
    if (currentDataParts.length > 0) {
      const fullData = currentDataParts.join('\n')
      if (fullData !== '[DONE]') {
        yield fullData
      }
    }
  } finally {
    reader.releaseLock()
  }
}
