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

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() || ''

    for (const line of lines) {
      if (line.startsWith('event:')) {
        continue
      }
      if (line.startsWith('data:')) {
        const data = line.slice(5).trim()
        if (data && data !== '[DONE]') {
          yield data
        }
      }
    }
  }
}
