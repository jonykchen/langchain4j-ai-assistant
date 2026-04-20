import { test, expect } from '../fixtures/test-fixtures';
import { ChatMock } from '../mocks/chat-mock';

test.describe('聊天功能', () => {
  let chatMock: ChatMock;

  test.beforeEach(async ({ chatPage }) => {
    chatMock = new ChatMock(chatPage.page);
    await chatPage.goto();
  });

  test.afterEach(async () => {
    await chatMock?.unrouteAll();
  });

  test('发送简单消息并收到回复', async ({ chatPage }) => {
    // Mock API 响应
    await chatMock.mockChatResponse('你好！有什么可以帮助你的？');

    await chatPage.sendMessage('你好');
    await chatPage.waitForResponse();
    await chatPage.expectMessageContains('你好');
  });

  test('流式响应正确显示', async ({ chatPage }) => {
    // Mock SSE 流
    await chatMock.mockStreamResponse(['你', '好', '！', '这是', '流式', '响应']);

    await chatPage.sendMessage('讲个笑话');
    await chatPage.waitForResponse();

    // 验证消息逐步显示
    const messageText = await chatPage.assistantMessage.textContent();
    expect(messageText).toContain('你好');
  });

  test('思考过程可折叠显示', async ({ chatPage }) => {
    // Mock 包含思考过程的响应
    await chatMock.mockChatWithThinking('正在思考你的问题...', '这是我的回答。');

    await chatPage.sendMessage('复杂问题');
    await chatPage.waitForResponse();

    // 验证思考部分存在
    await expect(chatPage.thinkingSection).toBeVisible();

    // 展开思考过程
    await chatPage.expandThinking();
    await expect(chatPage.thinkingSection).toContainText('正在思考');
  });

  test('代码块可以复制', async ({ chatPage, context }) => {
    // 授予剪贴板权限
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);

    await chatMock.mockChatWithCode(
      'java',
      'System.out.println("Hello");',
      '这是一个 Java 示例'
    );

    await chatPage.sendMessage('写个Java示例');
    await chatPage.waitForResponse();

    // 验证代码块存在
    await expect(chatPage.codeBlock).toBeVisible();

    // 复制代码
    await chatPage.copyCode();

    // 验证剪贴板内容
    const clipboardText = await chatPage.page.evaluate(() => navigator.clipboard.readText());
    expect(clipboardText).toContain('System.out.println');
  });

  test('网络错误时显示友好提示', async ({ chatPage }) => {
    await chatMock.mockChatError(500, 50000, '服务器内部错误');

    await chatPage.sendMessage('测试错误');

    // 验证错误提示显示
    await chatPage.expectErrorMessage('服务器内部错误');
  });

  test('限流时显示提示', async ({ chatPage }) => {
    await chatMock.mockRateLimitError();

    await chatPage.sendMessage('测试限流');

    // 验证限流提示
    await chatPage.expectErrorMessage('请求过于频繁');
  });

  test('AI 服务不可用时显示提示', async ({ chatPage }) => {
    await chatMock.mockServiceUnavailableError();

    await chatPage.sendMessage('测试不可用');

    // 验证服务不可用提示
    await chatPage.expectErrorMessage('不可用');
  });

  test('连续发送多条消息', async ({ chatPage }) => {
    const responses = ['第一条回复', '第二条回复', '第三条回复'];
    let callCount = 0;

    await chatPage.page.route('**/api/chat', async (route) => {
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

    // 发送第一条消息
    await chatPage.sendMessage('消息1');
    await chatPage.waitForResponse();
    await chatPage.expectMessageContains('第一条');

    // 发送第二条消息
    await chatPage.sendMessage('消息2');
    await chatPage.waitForResponse();
    await chatPage.expectMessageContains('第二条');
  });

  test('消息输入框支持回车发送', async ({ chatPage }) => {
    await chatMock.mockChatResponse('回车发送测试');

    await chatPage.messageInput.fill('测试回车');
    await chatPage.page.keyboard.press('Enter');

    await chatPage.waitForResponse();
    await chatPage.expectMessageContains('回车发送');
  });

  test('空消息不能发送', async ({ chatPage }) => {
    const initialCount = await chatPage.getMessageCount();

    // 尝试发送空消息
    await chatPage.sendButton.click();

    // 验证消息数量未增加
    const newCount = await chatPage.getMessageCount();
    expect(newCount).toBe(initialCount);
  });
});
