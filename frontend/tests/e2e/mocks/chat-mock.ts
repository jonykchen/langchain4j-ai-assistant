import { Page, Route } from '@playwright/test'

/**
 * 聊天 API Mock
 * 在 CI 环境或不需要真实 AI 调用时使用
 */
export class ChatMock {
  private page: Page

  constructor(page: Page) {
    this.page = page
  }

  /**
   * Mock 同步聊天 API
   * 同时拦截同步和流式 API，确保测试覆盖实际使用场景
   */
  async mockChatResponse(reply: string, options?: { delay?: number; status?: number }) {
    // 拦截同步 API
    await this.page.route('**/api/chat', async (route: Route) => {
      if (options?.delay) {
        await new Promise(resolve => setTimeout(resolve, options.delay))
      }
      await route.fulfill({
        status: options?.status || 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          message: 'success',
          data: { reply },
        }),
      })
    })

    // 同时拦截流式 API（实际应用使用流式响应）
    await this.page.route('**/api/chat/stream', async (route: Route) => {
      if (options?.delay) {
        await new Promise(resolve => setTimeout(resolve, options.delay))
      }
      // 将同步响应转换为 SSE 流式格式
      const body = `event:token\ndata:${reply}\n\nevent:done\ndata:[DONE]\n\n`
      await route.fulfill({
        status: options?.status || 200,
        contentType: 'text/event-stream;charset=UTF-8',
        body,
      })
    })
  }

  /**
   * Mock 流式聊天 API
   */
  async mockStreamResponse(tokens: string[], options?: { delay?: number }) {
    await this.page.route('**/api/chat/stream', async (route: Route) => {
      let body = ''
      for (const token of tokens) {
        body += `event:token\ndata:${token}\n\n`
        if (options?.delay) {
          await new Promise(resolve => setTimeout(resolve, options.delay))
        }
      }
      body += 'event:done\ndata:[DONE]\n\n'

      await route.fulfill({
        status: 200,
        contentType: 'text/event-stream;charset=UTF-8',
        body,
      })
    })
  }

  /**
   * Mock 带思考过程的聊天响应
   */
  async mockChatWithThinking(thinking: string, reply: string) {
    await this.mockChatResponse(`<thinking>${thinking}</thinking>${reply}`)
  }

  /**
   * Mock 带代码块的聊天响应
   */
  async mockChatWithCode(language: string, code: string, explanation: string) {
    const reply = `${explanation}\n\n\`\`\`${language}\n${code}\n\`\`\``
    await this.mockChatResponse(reply)
  }

  /**
   * Mock 聊天 API 错误
   * 同时拦截同步和流式 API
   */
  async mockChatError(statusCode: number, errorCode: number, message: string) {
    // 拦截同步 API
    await this.page.route('**/api/chat', async (route: Route) => {
      await route.fulfill({
        status: statusCode,
        contentType: 'application/json',
        body: JSON.stringify({
          code: errorCode,
          message,
          data: null,
        }),
      })
    })

    // 同时拦截流式 API
    await this.page.route('**/api/chat/stream', async (route: Route) => {
      // 流式 API 错误以 error 事件形式返回
      const body = `event:error\ndata:${message}\n\n`
      await route.fulfill({
        status: 200,
        contentType: 'text/event-stream;charset=UTF-8',
        body,
      })
    })
  }

  /**
   * Mock 限流错误
   */
  async mockRateLimitError() {
    await this.mockChatError(429, 42900, '请求过于频繁，请稍后再试')
  }

  /**
   * Mock AI 服务不可用错误
   */
  async mockServiceUnavailableError() {
    await this.mockChatError(503, 50206, '所有 AI 模型均不可用')
  }

  /**
   * Mock 网络超时
   * 同时拦截同步和流式 API
   */
  async mockNetworkTimeout() {
    await this.page.route('**/api/chat', async (route: Route) => {
      await route.abort('timedout')
    })
    await this.page.route('**/api/chat/stream', async (route: Route) => {
      await route.abort('timedout')
    })
  }

  /**
   * 移除所有 Mock
   */
  async unrouteAll() {
    await this.page.unroute('**/api/chat')
    await this.page.unroute('**/api/chat/stream')
  }
}
