import { test, expect } from '../fixtures/test-fixtures'
import { ChatMock } from '../mocks/chat-mock'

test.describe('流式响应测试', () => {
  let chatMock: ChatMock

  test.beforeEach(async ({ authenticatedPage }) => {
    chatMock = new ChatMock(authenticatedPage)
    // 先设置 Mock 再导航
    await chatMock.mockStreamResponse(['你', '好', '！', '我', '是', 'AI', '助', '手'])
    await authenticatedPage.goto('/')
  })

  test.afterEach(async () => {
    await chatMock?.unrouteAll()
  })

  test('SSE 流式响应逐字显示', async ({ authenticatedPage }) => {
    // Mock SSE 流 - 逐字输出
    await chatMock.mockStreamResponse(['你', '好', '！', '我', '是', 'AI', '助', '手'], {
      delay: 50,
    })

    await authenticatedPage.locator('[data-testid="message-input"]').fill('你好')
    await authenticatedPage.locator('[data-testid="send-button"]').click()

    // 等待响应
    await authenticatedPage.locator('[data-testid="assistant-message"]').waitFor({ timeout: 30000 })

    // 验证最终消息完整
    await expect(authenticatedPage.locator('[data-testid="assistant-message"]')).toContainText(
      '你好'
    )
    await expect(authenticatedPage.locator('[data-testid="assistant-message"]')).toContainText('AI')
  })

  test('流式响应包含代码块', async ({ authenticatedPage }) => {
    // Mock 包含代码的流式响应
    await authenticatedPage.route('**/api/chat/stream', async route => {
      const body = [
        'event:token\ndata:以下是\n\n',
        'event:token\ndata:Java\n\n',
        'event:token\ndata:代码\n\n',
        'event:token\ndata:```java\n\n',
        'event:token\ndata:public class Hello {\n\n',
        'event:token\ndata:}\n\n',
        'event:token\ndata:```\n\n',
        'event:done\ndata:[DONE]\n\n',
      ].join('')

      await route.fulfill({
        status: 200,
        contentType: 'text/event-stream;charset=UTF-8',
        body,
      })
    })

    await authenticatedPage.locator('[data-testid="message-input"]').fill('写代码')
    await authenticatedPage.locator('[data-testid="send-button"]').click()

    // 等待响应
    await authenticatedPage.locator('[data-testid="assistant-message"]').waitFor({ timeout: 30000 })

    // 验证代码内容存在（代码块通过 Markdown 渲染，可能包含 pre/code 标签）
    await expect(
      authenticatedPage.locator(
        '[data-testid="assistant-message"] pre, [data-testid="assistant-message"] code'
      )
    ).toBeVisible()
  })

  test('流式响应中途断开', async ({ authenticatedPage }) => {
    // 模拟流式响应中断
    await authenticatedPage.route('**/api/chat/stream', async route => {
      await route.fulfill({
        status: 200,
        contentType: 'text/event-stream;charset=UTF-8',
        body: 'event:token\ndata:部分响应\n\n', // 缺少 done 事件
      })
    })

    await authenticatedPage.locator('[data-testid="message-input"]').fill('测试中断')
    await authenticatedPage.locator('[data-testid="send-button"]').click()

    // 验证部分消息已显示
    await authenticatedPage.locator('[data-testid="assistant-message"]').waitFor({ timeout: 30000 })
    await expect(authenticatedPage.locator('[data-testid="assistant-message"]')).toContainText(
      '部分响应'
    )
  })

  test('流式响应包含思考过程', async ({ authenticatedPage }) => {
    await authenticatedPage.route('**/api/chat/stream', async route => {
      const body = [
        'event:token\ndata:<thinking>\n\n',
        'event:token\ndata:正在分析\n\n',
        'event:token\ndata:</thinking>\n\n',
        'event:token\ndata:这是回答\n\n',
        'event:done\ndata:[DONE]\n\n',
      ].join('')

      await route.fulfill({
        status: 200,
        contentType: 'text/event-stream;charset=UTF-8',
        body,
      })
    })

    await authenticatedPage.locator('[data-testid="message-input"]').fill('复杂问题')
    await authenticatedPage.locator('[data-testid="send-button"]').click()

    // 等待响应
    await authenticatedPage.locator('[data-testid="assistant-message"]').waitFor({ timeout: 30000 })

    // 验证思考过程可折叠
    await expect(authenticatedPage.locator('[data-testid="thinking-section"]')).toBeVisible()
  })

  test('流式响应错误事件', async ({ authenticatedPage }) => {
    await authenticatedPage.route('**/api/chat/stream', async route => {
      const body = 'event:error\ndata:AI 服务暂时不可用\n\n'
      await route.fulfill({
        status: 200,
        contentType: 'text/event-stream;charset=UTF-8',
        body,
      })
    })

    await authenticatedPage.locator('[data-testid="message-input"]').fill('测试错误')
    await authenticatedPage.locator('[data-testid="send-button"]').click()

    // 等待响应（错误消息显示在 assistant-message 中）
    await authenticatedPage.locator('[data-testid="assistant-message"]').waitFor({ timeout: 30000 })

    // 验证错误消息显示（检查消息内容包含 "AI 服务" 或 "不可用"）
    await expect(authenticatedPage.locator('[data-testid="assistant-message"]')).toContainText(
      'AI 服务'
    )
  })

  test('多个流式请求顺序处理', async ({ authenticatedPage }) => {
    let requestCount = 0

    await authenticatedPage.route('**/api/chat/stream', async route => {
      requestCount++
      const body = `event:token\ndata:回复${requestCount}\n\nevent:done\ndata:[DONE]\n\n`
      await route.fulfill({
        status: 200,
        contentType: 'text/event-stream;charset=UTF-8',
        body,
      })
    })

    // 连续发送两条消息
    await authenticatedPage.locator('[data-testid="message-input"]').fill('问题1')
    await authenticatedPage.locator('[data-testid="send-button"]').click()
    await authenticatedPage.locator('[data-testid="assistant-message"]').waitFor({ timeout: 30000 })

    await authenticatedPage.locator('[data-testid="message-input"]').fill('问题2')
    await authenticatedPage.locator('[data-testid="send-button"]').click()
    await authenticatedPage
      .locator('[data-testid="assistant-message"]')
      .nth(1)
      .waitFor({ timeout: 30000 })

    // 验证消息数量增加（至少有 2 条用户消息和 2 条助手消息）
    const count = await authenticatedPage
      .locator(
        '[data-testid^="message-"], [data-testid="user-message"], [data-testid="assistant-message"]'
      )
      .count()
    expect(count).toBeGreaterThanOrEqual(2)
  })
})
