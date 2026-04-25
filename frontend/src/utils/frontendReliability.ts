/**
 * 前端可靠性监控工具
 *
 * <p>提供 SSE 连接可靠性监控，包括：
 * <ul>
 *   <li>重连次数统计</li>
 *   <li>事件序号间隙检测</li>
 *   <li>状态恢复失败记录</li>
 *   <li>自动上报到后端 Prometheus 指标</li>
 * </ul>
 *
 * @author jonychen
 */

// 指标上报 API
const METRICS_API = '/api/agent/metrics/frontend'

/** 指标类型 */
export type FrontendMetricType = 'reconnect' | 'event_gap' | 'state_restore_failure'

/** 指标上报请求 */
interface MetricReport {
  type: FrontendMetricType
  count: number
  traceId?: string
  sessionId?: string
  details?: Record<string, any>
}

/** 本地指标累积器（用于批量上报） */
const metricsBuffer: MetricReport[] = []
let flushTimer: ReturnType<typeof setInterval> | null = null

/**
 * 记录 SSE 重连
 *
 * @param attempt 重连次数
 * @param traceId 当前执行追踪 ID
 */
export function recordReconnect(attempt: number, traceId?: string): void {
  metricsBuffer.push({
    type: 'reconnect',
    count: 1,
    traceId,
    details: { attempt }
  })
  console.warn(`[FrontendReliability] SSE reconnect attempt ${attempt}, traceId=${traceId}`)
}

/**
 * 记录事件序号间隙
 *
 * @param expected 期望的序号
 * @param actual 实际收到的序号
 * @param traceId 当前执行追踪 ID
 */
export function recordEventGap(expected: number, actual: number, traceId?: string): void {
  metricsBuffer.push({
    type: 'event_gap',
    count: actual - expected,
    traceId,
    details: { expected, actual, gap: actual - expected }
  })
  console.warn(`[FrontendReliability] Event gap: expected ${expected}, got ${actual}`)
}

/**
 * 记录状态恢复失败
 *
 * @param sessionId 会话 ID
 * @param reason 失败原因
 */
export function recordStateRestoreFailure(sessionId: string, reason: string): void {
  metricsBuffer.push({
    type: 'state_restore_failure',
    count: 1,
    sessionId,
    details: { reason }
  })
  console.error(`[FrontendReliability] State restore failed: sessionId=${sessionId}, reason=${reason}`)
}

/**
 * 获取认证头
 */
function getAuthHeaders(): Record<string, string> {
  const token = localStorage.getItem('access_token')
  return token ? { 'Authorization': `Bearer ${token}` } : {}
}

/**
 * 执行指标上报
 *
 * <p>将累积的指标批量上报到后端，后端将其转换为 Prometheus 指标
 */
async function flushMetrics(): Promise<void> {
  if (metricsBuffer.length === 0) return

  // 复制缓冲区并清空
  const metrics = [...metricsBuffer]
  metricsBuffer.length = 0

  try {
    const response = await fetch(METRICS_API, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...getAuthHeaders()
      },
      body: JSON.stringify({ metrics })
    })

    if (!response.ok) {
      // 上报失败，将指标重新加入缓冲区（最多保留 100 条）
      if (metricsBuffer.length < 100) {
        metricsBuffer.push(...metrics)
      }
      console.warn('[FrontendReliability] Metrics flush failed:', response.status)
    }
  } catch (e) {
    console.error('[FrontendReliability] Metrics flush error:', e)
    // 上报失败，将指标重新加入缓冲区
    if (metricsBuffer.length < 100) {
      metricsBuffer.push(...metrics)
    }
  }
}

/**
 * 启动定期上报
 *
 * <p>每 30 秒批量上报一次指标，避免频繁请求
 */
export function startMetricsReporting(): void {
  if (flushTimer) return
  flushTimer = setInterval(flushMetrics, 30000)
  console.info('[FrontendReliability] Metrics reporting started')
}

/**
 * 停止定期上报
 *
 * <p>停止前执行最后一次上报
 */
export async function stopMetricsReporting(): Promise<void> {
  if (!flushTimer) return
  clearInterval(flushTimer)
  flushTimer = null
  await flushMetrics()
  console.info('[FrontendReliability] Metrics reporting stopped')
}

/**
 * 获取当前缓冲区状态（用于调试）
 */
export function getMetricsBufferStatus(): { size: number; types: Record<string, number> } {
  const types: Record<string, number> = {}
  for (const m of metricsBuffer) {
    types[m.type] = (types[m.type] || 0) + m.count
  }
  return { size: metricsBuffer.length, types }
}

/**
 * 手动上报（用于测试或特殊场景）
 */
export async function manualFlush(): Promise<void> {
  await flushMetrics()
}

// 页面可见性变化时暂停/恢复上报
document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'hidden') {
    // 页面隐藏时执行一次上报
    flushMetrics().catch(() => {})
  }
})

// 页面关闭前执行最后一次上报
window.addEventListener('beforeunload', () => {
  // 使用 sendBeacon 确保上报成功
  if (metricsBuffer.length > 0) {
    const blob = new Blob([JSON.stringify({ metrics: metricsBuffer })], {
      type: 'application/json'
    })
    navigator.sendBeacon(METRICS_API, blob)
    metricsBuffer.length = 0
  }
})