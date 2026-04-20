import { test, expect } from '../fixtures/test-fixtures';
import { ChatMock } from '../mocks/chat-mock';

test.describe('流式响应测试', () => {
  let chatMock: ChatMock;

  test.beforeEach(async ({ chatPage }) => {
    chatMock = new ChatMock(chatPage.page);
    await chatPage.goto();
  });

  test.afterEach(async () => {
    await chatMock?.unrouteAll();
  });

  test('SSE 流式响应逐字显示', async ({ chatPage }) => {
    // Mock SSE 流 - 逐字输出
    await chatMock.mockStreamResponse(
      ['你', '好', '！', '我', '是', 'AI', '助', '手'],
      { delay: 50 }
    );

    await chatPage.sendMessage('你好');
    await chatPage.waitForResponse();

    // 验证最终消息完整
    await chatPage.expectMessageContains('你好');
    await chatPage.expectMessageContains('AI');
  });

  test('流式响应包含代码块', async ({ chatPage }) => {
    // Mock 包含代码的流式响应
    await chatPage.page.route('**/api/chat/stream', async (route) => {
      const body = [
        'event:token\ndata:以下是\n\n',
        'event:token\ndata:Java\n\n',
        'event:token\ndata:代码\n\n',
        'event:token\ndata:```java\n\n',
        'event:token\ndata:public class Hello {\n\n',
        'event:token\ndata:}\n\n',
        'event:token\ndata:```\n\n',
        'event:done\ndata:[DONE]\n\n'
      ].join('');

      await route.fulfill({
        status: 200,
        contentType: 'text/event-stream;charset=UTF-8',
        body
      });
    });

    await chatPage.sendMessage('写代码');
    await chatPage.waitForResponse();

    // 验证代码块渲染
    await expect(chatPage.codeBlock).toBeVisible();
  });

  test('流式响应中途断开', async ({ chatPage }) => {
    // 模拟流式响应中断
    await chatPage.page.route('**/api/chat/stream', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'text/event-stream;charset=UTF-8',
        body: 'event:token\ndata:部分响应\n\n'  // 缺少 done 事件
      });
    });

    await chatPage.sendMessage('测试中断');

    // 验证部分消息已显示
    await chatPage.waitForResponse();
    await chatPage.expectMessageContains('部分响应');
  });

  test('流式响应包含思考过程', async ({ chatPage }) => {
    await chatPage.page.route('**/api/chat/stream', async (route) => {
      const body = [
        'event:token\ndata:<thinking>\n\n',
        'event:token\ndata:正在分析\n\n',
        'event:token\ndata:</thinking>\n\n',
        'event:token\ndata:这是回答\n\n',
        'event:done\ndata:[DONE]\n\n'
      ].join('');

      await route.fulfill({
        status: 200,
        contentType: 'text/event-stream;charset=UTF-8',
        body
      });
    });

    await chatPage.sendMessage('复杂问题');
    await chatPage.waitForResponse();

    // 验证思考过程可折叠
    await expect(chatPage.thinkingSection).toBeVisible();
  });

  test('流式响应错误事件', async ({ chatPage }) => {
    await chatPage.page.route('**/api/chat/stream', async (route) => {
      const body = 'event:error\ndata:AI 服务暂时不可用\n\n';
      await route.fulfill({
        status: 200,
        contentType: 'text/event-stream;charset=UTF-8',
        body
      });
    });

    await chatPage.sendMessage('测试错误');
    await chatPage.expectErrorMessage('不可用');
  });

  test('多个流式请求顺序处理', async ({ chatPage }) => {
    let requestCount = 0;

    await chatPage.page.route('**/api/chat/stream', async (route) => {
      requestCount++;
      const body = `event:token\ndata:回复${requestCount}\n\nevent:done\ndata:[DONE]\n\n`;
      await route.fulfill({
        status: 200,
        contentType: 'text/event-stream;charset=UTF-8',
        body
      });
    });

    // 连续发送两条消息
    await chatPage.sendMessage('问题1');
    await chatPage.waitForResponse();

    await chatPage.sendMessage('问题2');
    await chatPage.waitForResponse();

    // 验证消息数量增加
    const count = await chatPage.getMessageCount();
    expect(count).toBeGreaterThanOrEqual(4); // 2 user + 2 assistant
  });
});
