import { Page, Locator, expect } from '@playwright/test'

/**
 * 聊天页面对象模型
 * 封装聊天页面的交互逻辑
 */
export class ChatPage {
  readonly page: Page
  readonly messageInput: Locator
  readonly sendButton: Locator
  readonly messageList: Locator
  readonly assistantMessage: Locator
  readonly userMessage: Locator
  readonly thinkingSection: Locator
  readonly codeBlock: Locator
  readonly loadingIndicator: Locator
  readonly sidebar: Locator
  readonly newConversationButton: Locator

  constructor(page: Page) {
    this.page = page
    // 消息输入区域
    this.messageInput = page.locator('[data-testid="message-input"]')
    this.sendButton = page.locator('[data-testid="send-button"]')

    // 消息列表
    this.messageList = page.locator('[data-testid="message-list"]')
    this.userMessage = page.locator('[data-testid="user-message"]')
    this.assistantMessage = page.locator('[data-testid="assistant-message"]')

    // 思考过程和代码块
    this.thinkingSection = page.locator('[data-testid="thinking-section"]')
    this.codeBlock = page.locator('[data-testid="code-block"]')

    // 加载状态
    this.loadingIndicator = page.locator('.loading-indicator, [data-testid="loading"]')

    // 侧边栏
    this.sidebar = page.locator('[data-testid="sidebar"]')
    this.newConversationButton = page.locator('[data-testid="new-conversation"]')
  }

  /**
   * 导航到聊天页面
   */
  async goto() {
    await this.page.goto('/')
    await this.page.waitForLoadState('networkidle')
  }

  /**
   * 发送消息
   */
  async sendMessage(message: string) {
    await this.messageInput.fill(message)
    await this.sendButton.click()
  }

  /**
   * 发送消息并等待响应
   */
  async sendMessageAndWait(message: string, timeout = 30000) {
    await this.sendMessage(message)
    await this.waitForResponse(timeout)
  }

  /**
   * 等待响应完成
   */
  async waitForResponse(timeout = 30000) {
    // 等待助手消息出现
    await this.assistantMessage.waitFor({ state: 'visible', timeout })

    // 等待加载指示器消失
    try {
      await this.loadingIndicator.waitFor({ state: 'hidden', timeout })
    } catch {
      // 加载指示器可能已经消失
    }
  }

  /**
   * 验证消息包含指定文本
   */
  async expectMessageContains(text: string) {
    await expect(this.assistantMessage).toContainText(text)
  }

  /**
   * 验证消息不包含指定文本
   */
  async expectMessageNotContains(text: string) {
    await expect(this.assistantMessage).not.toContainText(text)
  }

  /**
   * 展开思考过程
   */
  async expandThinking() {
    const thinkingToggle = this.thinkingSection.locator('[data-testid="thinking-toggle"]')
    await thinkingToggle.click()
  }

  /**
   * 复制代码
   */
  async copyCode() {
    const copyButton = this.codeBlock.locator('[data-testid="copy-button"]')
    await copyButton.click()
  }

  /**
   * 获取代码内容
   */
  async getCodeContent(): Promise<string> {
    const codeElement = this.codeBlock.locator('code')
    return (await codeElement.textContent()) || ''
  }

  /**
   * 创建新对话
   */
  async createNewConversation() {
    await this.newConversationButton.click()
  }

  /**
   * 获取消息数量
   */
  async getMessageCount(): Promise<number> {
    return await this.messageList.locator('[data-testid^="message-"]').count()
  }

  /**
   * 等待流式响应完成
   */
  async waitForStreamingComplete(timeout = 60000) {
    // 等待流式响应结束标记
    await this.page.waitForFunction(
      () => {
        const loadingEl = document.querySelector('.streaming-indicator')
        return loadingEl === null
      },
      { timeout }
    )
  }

  /**
   * 验证错误提示显示
   */
  async expectErrorMessage(message: string) {
    const errorToast = this.page.locator('.el-message--error, .error-message')
    await expect(errorToast).toBeVisible()
    await expect(errorToast).toContainText(message)
  }

  /**
   * 验证成功提示显示
   */
  async expectSuccessMessage(message: string) {
    const successToast = this.page.locator('.el-message--success, .success-message')
    await expect(successToast).toBeVisible()
    await expect(successToast).toContainText(message)
  }
}
