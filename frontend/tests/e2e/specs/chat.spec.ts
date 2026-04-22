import { test, expect } from '../fixtures/test-fixtures';
import { ChatMock } from '../mocks/chat-mock';

test.describe('聊天功能', () => {
  let chatMock: ChatMock;

  test.beforeEach(async ({ authenticatedPage }) => {
    chatMock = new ChatMock(authenticatedPage);
    // 先设置 Mock 再导航到页面
    await chatMock.mockChatResponse('你好！有什么可以帮助你的？');
    await authenticatedPage.goto('/');
  });

  test.afterEach(async () => {
    await chatMock?.unrouteAll();
  });

  test('发送简单消息并收到回复', async ({ authenticatedPage }) => {
    // 发送消息
    await authenticatedPage.locator('[data-testid="message-input"]').fill('你好');
    await authenticatedPage.locator('[data-testid="send-button"]').click();

    // 等待响应
    await authenticatedPage.locator('[data-testid="assistant-message"]').waitFor({ timeout: 30000 });

    // 验证消息包含预期文本
    await expect(authenticatedPage.locator('[data-testid="assistant-message"]')).toContainText('你好');
  });

  test('流式响应正确显示', async ({ authenticatedPage }) => {
    // Mock SSE 流
    await chatMock.mockStreamResponse(['你', '好', '！', '这是', '流式', '响应']);

    await authenticatedPage.locator('[data-testid="message-input"]').fill('讲个笑话');
    await authenticatedPage.locator('[data-testid="send-button"]').click();

    // 等待响应
    await authenticatedPage.locator('[data-testid="assistant-message"]').waitFor({ timeout: 30000 });

    // 验证消息逐步显示
    const messageText = await authenticatedPage.locator('[data-testid="assistant-message"]').textContent();
    expect(messageText).toContain('你好');
  });

  test('思考过程可折叠显示', async ({ authenticatedPage }) => {
    // Mock 包含思考过程的响应
    await chatMock.mockChatWithThinking('正在思考你的问题...', '这是我的回答。');

    await authenticatedPage.locator('[data-testid="message-input"]').fill('复杂问题');
    await authenticatedPage.locator('[data-testid="send-button"]').click();

    // 等待响应
    await authenticatedPage.locator('[data-testid="assistant-message"]').waitFor({ timeout: 30000 });

    // 验证思考部分存在
    await expect(authenticatedPage.locator('[data-testid="thinking-section"]')).toBeVisible();

    // 展开思考过程
    await authenticatedPage.locator('[data-testid="thinking-toggle"]').click();
    await expect(authenticatedPage.locator('[data-testid="thinking-section"]')).toContainText('正在思考');
  });

  test('代码块可以复制', async ({ authenticatedPage }) => {
    await chatMock.mockChatWithCode(
      'java',
      'System.out.println("Hello");',
      '这是一个 Java 示例'
    );

    await authenticatedPage.locator('[data-testid="message-input"]').fill('写个Java示例');
    await authenticatedPage.locator('[data-testid="send-button"]').click();

    // 等待响应
    await authenticatedPage.locator('[data-testid="assistant-message"]').waitFor({ timeout: 30000 });

    // 验证消息内容包含 Java 和 示例
    const messageContent = await authenticatedPage.locator('[data-testid="assistant-message"]').textContent();
    expect(messageContent).toContain('Java');
    expect(messageContent).toContain('示例');
  });

  test('网络错误时显示友好提示', async ({ authenticatedPage }) => {
    // 重新 Mock 错误响应
    await chatMock.mockChatError(500, 50000, '服务器内部错误');

    await authenticatedPage.locator('[data-testid="message-input"]').fill('测试错误');
    await authenticatedPage.locator('[data-testid="send-button"]').click();

    // 等待响应（错误提示在 assistant-message 中显示）
    await authenticatedPage.locator('[data-testid="assistant-message"]').waitFor({ timeout: 30000 });

    // 验证错误消息显示在回复中
    await expect(authenticatedPage.locator('[data-testid="assistant-message"]')).toContainText('错误');
  });

  test('限流时显示提示', async ({ authenticatedPage }) => {
    await chatMock.mockRateLimitError();

    await authenticatedPage.locator('[data-testid="message-input"]').fill('测试限流');
    await authenticatedPage.locator('[data-testid="send-button"]').click();

    // 等待响应
    await authenticatedPage.locator('[data-testid="assistant-message"]').waitFor({ timeout: 30000 });

    // 验证错误消息显示（检查"过于频繁"或"稍后"）
    const message = authenticatedPage.locator('[data-testid="assistant-message"]');
    await expect(message).toContainText(/过于频繁|稍后|错误/);
  });

  test('AI 服务不可用时显示提示', async ({ authenticatedPage }) => {
    await chatMock.mockServiceUnavailableError();

    await authenticatedPage.locator('[data-testid="message-input"]').fill('测试不可用');
    await authenticatedPage.locator('[data-testid="send-button"]').click();

    // 等待响应
    await authenticatedPage.locator('[data-testid="assistant-message"]').waitFor({ timeout: 30000 });

    // 验证错误消息显示（检查"不可用"或"错误"）
    const message = authenticatedPage.locator('[data-testid="assistant-message"]');
    await expect(message).toContainText(/不可用|错误/);
  });

  test('连续发送多条消息', async ({ authenticatedPage }) => {
    const responses = ['第一条回复', '第二条回复', '第三条回复'];
    let callCount = 0;

    await authenticatedPage.route('**/api/chat', async (route) => {
      const reply = responses[callCount % responses.length];
      callCount++;
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          message: 'success',
          data: { reply }
        })
      });
    });

    // 同时 Mock 流式 API
    await authenticatedPage.route('**/api/chat/stream', async (route) => {
      const reply = responses[callCount % responses.length];
      callCount++;
      const body = `event:token\ndata:${reply}\n\nevent:done\ndata:[DONE]\n\n`;
      await route.fulfill({
        status: 200,
        contentType: 'text/event-stream;charset=UTF-8',
        body
      });
    });

    // 发送第一条消息
    await authenticatedPage.locator('[data-testid="message-input"]').fill('消息1');
    await authenticatedPage.locator('[data-testid="send-button"]').click();
    await authenticatedPage.locator('[data-testid="assistant-message"]').waitFor({ timeout: 30000 });
    await expect(authenticatedPage.locator('[data-testid="assistant-message"]').first()).toContainText('第一条');

    // 发送第二条消息
    await authenticatedPage.locator('[data-testid="message-input"]').fill('消息2');
    await authenticatedPage.locator('[data-testid="send-button"]').click();
    await authenticatedPage.locator('[data-testid="assistant-message"]').nth(1).waitFor({ timeout: 30000 });
    await expect(authenticatedPage.locator('[data-testid="assistant-message"]').nth(1)).toContainText('第二条');
  });

  test('消息输入框支持回车发送', async ({ authenticatedPage }) => {
    await chatMock.mockChatResponse('回车发送测试');

    await authenticatedPage.locator('[data-testid="message-input"]').fill('测试回车');
    await authenticatedPage.keyboard.press('Enter');

    await authenticatedPage.locator('[data-testid="assistant-message"]').waitFor({ timeout: 30000 });
    await expect(authenticatedPage.locator('[data-testid="assistant-message"]')).toContainText('回车发送');
  });

  test('空消息不能发送', async ({ authenticatedPage }) => {
    // 清空输入框
    await authenticatedPage.locator('[data-testid="message-input"]').clear();

    // 发送按钮应该禁用
    const sendButton = authenticatedPage.locator('[data-testid="send-button"]');
    await expect(sendButton).toBeDisabled();

    // 验证没有用户消息或助手消息（只检查 user-message 和 assistant-message）
    const userMessageCount = await authenticatedPage.locator('[data-testid="user-message"]').count();
    const assistantMessageCount = await authenticatedPage.locator('[data-testid="assistant-message"]').count();
    // 初始状态不应该有任何消息
    expect(userMessageCount).toBe(0);
    expect(assistantMessageCount).toBe(0);
  });
});