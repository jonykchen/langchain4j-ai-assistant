/**
 * Agent API 模块（生产级 SSE 客户端）
 *
 * <p>提供 Agent 执行相关的 API 调用，增强能力：
 * <ol>
 *   <li>自动重连（指数退避，最多 5 次）</li>
 *   <li>事件序号检测，去重 + 乱序处理</li>
 *   <li>心跳超时检测（35s 无事件触发重连）</li>
 *   <li>前端可靠性指标上报</li>
 * </ol>
 *
 * @author jonychen
 */

import type {
  AgentEvent,
  AgentMetadata,
  ExecuteRequest,
  ConfirmRequest
} from '@/types/agent'
import {
  recordReconnect,
  recordEventGap,
  startMetricsReporting
} from '@/utils/frontendReliability'
import http from '@/utils/http'
import { useAuthStore } from '@/stores/auth'

// 启动指标上报
startMetricsReporting()

const API_BASE = '/api/agent'

/** SSE 连接选项 */
export interface SSEOptions {
  /** 重连时的回调，参数为当前重连次数 */
  onReconnect?: (attempt: number) => void
  /** 连接成功时的回调 */
  onConnected?: () => void
}

/**
 * 执行 Agent（SSE 流式响应，带自动重连）
 *
 * <p>增强功能：
 * <ul>
 *   <li>连接断开后自动重连，指数退避（1s, 2s, 4s, 8s, 16s），最多 5 次</li>
 *   <li>心跳超时检测：35s 无事件触发重连</li>
 *   <li>事件序号去重：同一 sequenceNumber 的事件只处理一次</li>
 *   <li>认证错误直接抛出，不重连</li>
 * </ul>
 *
 * @param request 执行请求
 * @param options SSE 事件回调
 * @returns AsyncGenerator，每次 yield 一个 AgentEvent
 *
 * @example
 * for await (const event of executeAgent({ userInput: '检查模型健康状态' })) {
 *   console.log(event.eventType, event)
 * }
 */
export async function* executeAgent(
  request: ExecuteRequest,
  options: SSEOptions = {}
): AsyncGenerator<AgentEvent> {
  let lastSequenceNumber = 0
  let reconnectCount = 0
  const MAX_RECONNECT = 5
  const HEARTBEAT_TIMEOUT_MS = 35000 // 心跳间隔 30s，超时 35s

  let isDone = false
  const processedSequences = new Set<number>()

  /**
   * 建立 SSE 连接
   *
   * <p>认证/授权错误直接抛出，不触发重连。其他错误返回 null 触发重连逻辑。
   *
   * @returns ReadableStream reader 或 null（连接失败）
   */
  async function connect(): Promise<ReadableStreamDefaultReader<Uint8Array> | null> {
    try {
      // 每次连接前获取最新 token（可能已被其他请求刷新过）
      const currentToken = localStorage.getItem('access_token')
      const response = await fetch(`${API_BASE}/execute`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'text/event-stream',
          ...(currentToken ? { 'Authorization': `Bearer ${currentToken}` } : {}),
        },
        body: JSON.stringify(request)
      })

      // 认证错误：尝试刷新 Token 后重试一次
      if (response.status === 401) {
        console.warn('[AgentAPI] SSE 连接收到 401，尝试刷新 Token...')
        const authStore = useAuthStore()
        const refreshed = await authStore.refreshAccessToken()
        if (refreshed) {
          const newToken = localStorage.getItem('access_token')
          console.info('[AgentAPI] Token 刷新成功，重新建立 SSE 连接')
          const retryResponse = await fetch(`${API_BASE}/execute`, {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              'Accept': 'text/event-stream',
              ...(newToken ? { 'Authorization': `Bearer ${newToken}` } : {}),
            },
            body: JSON.stringify(request)
          })
          if (!retryResponse.ok) {
            const errorData = await retryResponse.json().catch(() => ({}))
            throw new Error(errorData.message || `HTTP error! status: ${retryResponse.status}`)
          }
          if (options.onConnected) options.onConnected()
          reconnectCount = 0
          return retryResponse.body?.getReader() || null
        }
        // 刷新失败，抛出认证错误（不触发重连）
        throw new Error(`Auth error: ${response.status}`)
      }

      // 授权错误直接抛出，不重连
      if (response.status === 403) {
        throw new Error(`Auth error: ${response.status}`)
      }

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}))
        throw new Error(errorData.message || `HTTP error! status: ${response.status}`)
      }

      // 连接成功，重置重连计数
      if (options.onConnected) options.onConnected()
      reconnectCount = 0

      return response.body?.getReader() || null
    } catch (e) {
      const errMsg = e instanceof Error ? e.message : String(e)
      // 认证错误直接抛出，不重连
      if (errMsg.startsWith('Auth error')) {
        throw e
      }
      // 超过最大重连次数，抛出错误
      if (reconnectCount >= MAX_RECONNECT) {
        throw e
      }
      // 其他错误返回 null，触发重连
      console.warn('[AgentAPI] 连接失败，准备重连:', errMsg)
      return null
    }
  }

  let reader: ReadableStreamDefaultReader<Uint8Array> | null = null
  let heartbeatTimer: number | null = null

  /**
   * 重置心跳计时器
   *
   * <p>每次收到数据后重置计时器。如果 35s 内没有收到任何事件，
   * 则认为连接已断开，取消当前 reader 触发重连。
   */
  function resetHeartbeat() {
    if (heartbeatTimer) window.clearTimeout(heartbeatTimer)
    heartbeatTimer = window.setTimeout(() => {
      console.warn('[AgentAPI] 心跳超时，触发重连')
      reader?.cancel().catch(() => {})
    }, HEARTBEAT_TIMEOUT_MS)
  }

  try {
    while (!isDone) {
      reader = await connect()

      if (!reader) {
        // 连接失败，等待指数退避后重连
        reconnectCount++
        if (options.onReconnect) options.onReconnect(reconnectCount)
        // 记录重连指标
        recordReconnect(reconnectCount, request.sessionId)
        const delay = Math.min(1000 * Math.pow(2, reconnectCount - 1), 30000)
        console.info(`[AgentAPI] 第 ${reconnectCount}/${MAX_RECONNECT} 次重连，${delay}ms 后重试`)
        await new Promise(r => setTimeout(r, delay))
        continue
      }

      resetHeartbeat()

      const decoder = new TextDecoder()
      let buffer = ''
      let currentEvent = ''
      let currentData = ''

      try {
        while (true) {
          const { done, value } = await reader.read()
          if (done) break

          // 收到数据，重置心跳
          resetHeartbeat()

          buffer += decoder.decode(value, { stream: true })

          // 按换行分割
          const lines = buffer.split('\n')
          buffer = lines.pop() || ''

          for (const line of lines) {
            if (line.startsWith('event:')) {
              currentEvent = line.slice(6).trim()
            } else if (line.startsWith('data:')) {
              const dataContent = line.slice(5)
              currentData += (currentData ? '\n' : '') + (dataContent.startsWith(' ') ? dataContent.slice(1) : dataContent)
            } else if (line === '' && currentEvent && currentData) {
              // 空行表示事件结束

              // 检查是否是 SSE 结束标记
              if (currentEvent === 'done' && currentData === '[DONE]') {
                isDone = true
                return
              }

              try {
                const parsed = JSON.parse(currentData)
                const event: AgentEvent = {
                  eventType: currentEvent as AgentEvent['eventType'],
                  ...parsed,
                }

                // 事件序号检测 + 去重
                if (event.sequenceNumber !== undefined && event.sequenceNumber !== null) {
                  if (processedSequences.has(event.sequenceNumber)) {
                    // 重复事件，跳过
                    console.debug(`[AgentAPI] 跳过重复事件: seq=${event.sequenceNumber}, type=${event.eventType}`)
                    currentEvent = ''
                    currentData = ''
                    continue
                  }

                  // 检测事件间隔（乱序或丢包）
                  if (event.sequenceNumber > lastSequenceNumber + 1) {
                    console.warn(
                      `[AgentAPI] SSE event gap: expected ${lastSequenceNumber + 1}, got ${event.sequenceNumber}`
                    )
                    // 记录事件间隙指标
                    recordEventGap(lastSequenceNumber + 1, event.sequenceNumber, event.traceId)
                  }

                  processedSequences.add(event.sequenceNumber)
                  lastSequenceNumber = event.sequenceNumber
                }

                yield event
              } catch (e) {
                console.warn('[AgentAPI] JSON 解析失败:', currentData, e)
                // 解析错误不中断，继续消费后续事件
              }

              currentEvent = ''
              currentData = ''
            }
          }
        }
      } catch (e) {
        // 读取异常，尝试重连
        if (!isDone && reconnectCount < MAX_RECONNECT) {
          reconnectCount++
          if (options.onReconnect) options.onReconnect(reconnectCount)
          const delay = Math.min(1000 * Math.pow(2, reconnectCount - 1), 30000)
          console.warn(`[AgentAPI] 读取异常，${delay}ms 后重连:`, e)
          await new Promise(r => setTimeout(r, delay))
          continue
        }
        throw e
      } finally {
        reader?.releaseLock()
      }
    }
  } finally {
    // 清理心跳计时器
    if (heartbeatTimer) window.clearTimeout(heartbeatTimer)
  }
}

/**
 * 确认敏感操作
 */
export async function confirmOperation(request: ConfirmRequest): Promise<{ success: boolean; message: string }> {
  return http.post(API_BASE + '/confirm', request)
}

/**
 * 取消执行
 */
export async function cancelExecution(traceId: string): Promise<{ success: boolean; message: string }> {
  return http.post(`${API_BASE}/cancel/${traceId}`)
}

/**
 * 获取可用 Agent 列表
 */
export async function listAgents(): Promise<AgentMetadata[]> {
  return http.get<AgentMetadata[]>(API_BASE + '/list')
}

// ==================== 执行历史 API ====================

export interface ExecutionHistoryVO {
  traceId: string
  userId: string
  agentName: string
  eventType: string
  status: string
  timestamp: string
  clientIp: string
  details: Record<string, any>
}

export interface ExecutionDetailVO {
  traceId: string
  userId: string
  agentName: string
  status: string
  startTime: string
  endTime: string
  durationMs: number
  totalSteps: number
  steps: StepDetail[]
  summary: Record<string, any>
}

export interface StepDetail {
  eventType: string
  timestamp: string
  agentName: string
  details: Record<string, any>
}

export interface AuditStats {
  totalExecutions: number
  successCount: number
  errorCount: number
  cancelCount: number
  toolCalls: number
  confirmations: number
  hoursRange: number
}

export interface CleanupResult {
  deletedCount: number
  daysBefore: number
}

export interface PageResponse<T> {
  data: T[]
  total: number
  page: number
  size: number
}

/**
 * 分页查询执行历史
 */
export async function getHistory(params: {
  page?: number
  size?: number
  userId?: string
  agentName?: string
  eventType?: string
}): Promise<PageResponse<ExecutionHistoryVO>> {
  const query = new URLSearchParams()
  if (params.page !== undefined) query.set('page', String(params.page))
  if (params.size !== undefined) query.set('size', String(params.size))
  if (params.userId) query.set('userId', params.userId)
  if (params.agentName) query.set('agentName', params.agentName)
  if (params.eventType) query.set('eventType', params.eventType)

  return http.get<PageResponse<ExecutionHistoryVO>>(`${API_BASE}/history?${query}`)
}

/**
 * 获取执行详情
 */
export async function getExecutionDetail(traceId: string): Promise<ExecutionDetailVO> {
  return http.get<ExecutionDetailVO>(`${API_BASE}/history/${traceId}`)
}

// ==================== 管理员审计 API ====================

const ADMIN_API_BASE = '/api/admin/agent/audit'

/**
 * 分页查询审计日志（管理员）
 */
export async function queryAuditLogs(params: {
  page?: number
  size?: number
  userId?: string
  agentName?: string
  eventType?: string
}): Promise<PageResponse<ExecutionHistoryVO>> {
  const query = new URLSearchParams()
  if (params.page !== undefined) query.set('page', String(params.page))
  if (params.size !== undefined) query.set('size', String(params.size))
  if (params.userId) query.set('userId', params.userId)
  if (params.agentName) query.set('agentName', params.agentName)
  if (params.eventType) query.set('eventType', params.eventType)

  return http.get<PageResponse<ExecutionHistoryVO>>(`${ADMIN_API_BASE}/logs?${query}`)
}

/**
 * 获取审计统计（管理员）
 */
export async function getAuditStats(hours: number = 24): Promise<AuditStats> {
  return http.get<AuditStats>(`${ADMIN_API_BASE}/stats?hours=${hours}`)
}

/**
 * 清理过期日志（管理员）
 */
export async function cleanupAuditLogs(daysBefore: number = 30): Promise<CleanupResult> {
  return http.delete<CleanupResult>(`${ADMIN_API_BASE}/cleanup?daysBefore=${daysBefore}`)
}
