import { Page, Route } from '@playwright/test';

/**
 * 聊天 API Mock
 * 在 CI 环境或不需要真实 AI 调用时使用
 */
export class ChatMock {
  private page: Page;

  constructor(page: Page) {
    this.page = page;
  }

  /**
   * Mock 同步聊天 API
   */
  async mockChatResponse(reply: string, options?: { delay?: number; status?: number }) {
    await this.page.route('**/api/chat', async (route: Route) => {
      if (options?.delay) {
        await new Promise(resolve => setTimeout(resolve, options.delay));
      }
      await route.fulfill({
        status: options?.status || 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          message: 'success',
          data: { reply }
        })
      });
    });
  }

  /**
   * Mock 流式聊天 API
   */
  async mockStreamResponse(tokens: string[], options?: { delay?: number }) {
    await this.page.route('**/api/chat/stream', async (route: Route) => {
      let body = '';
      for (const token of tokens) {
        body += `event:token\ndata:${token}\n\n`;
        if (options?.delay) {
          await new Promise(resolve => setTimeout(resolve, options.delay));
        }
      }
      body += 'event:done\ndata:[DONE]\n\n';

      await route.fulfill({
        status: 200,
        contentType: 'text/event-stream;charset=UTF-8',
        body
      });
    });
  }

  /**
   * Mock 带思考过程的聊天响应
   */
  async mockChatWithThinking(thinking: string, reply: string) {
    await this.mockChatResponse(`<thinking>${thinking}</thinking>${reply}`);
  }

  /**
   * Mock 带代码块的聊天响应
   */
  async mockChatWithCode(language: string, code: string, explanation: string) {
    const reply = `${explanation}\n\n\`\`\`${language}\n${code}\n\`\`\``;
    await this.mockChatResponse(reply);
  }

  /**
   * Mock 聊天 API 错误
   */
  async mockChatError(statusCode: number, errorCode: number, message: string) {
    await this.page.route('**/api/chat', async (route: Route) => {
      await route.fulfill({
        status: statusCode,
        contentType: 'application/json',
        body: JSON.stringify({
          code: errorCode,
          message,
          data: null
        })
      });
    });
  }

  /**
   * Mock 限流错误
   */
  async mockRateLimitError() {
    await this.mockChatError(429, 42900, '请求过于频繁，请稍后再试');
  }

  /**
   * Mock AI 服务不可用错误
   */
  async mockServiceUnavailableError() {
    await this.mockChatError(503, 50206, '所有 AI 模型均不可用');
  }

  /**
   * Mock 网络超时
   */
  async mockNetworkTimeout() {
    await this.page.route('**/api/chat', async (route: Route) => {
      await route.abort('timedout');
    });
  }

  /**
   * 移除所有 Mock
   */
  async unrouteAll() {
    await this.page.unroute('**/api/chat');
    await this.page.unroute('**/api/chat/stream');
  }
}
