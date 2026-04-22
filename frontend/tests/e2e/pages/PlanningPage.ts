import { Page, Locator, expect } from '@playwright/test';

/**
 * 任务规划页面对象模型
 */
export class PlanningPage {
  readonly page: Page;
  readonly container: Locator;

  // 统计卡片
  readonly executionCount: Locator;
  readonly successCount: Locator;
  readonly failureCount: Locator;
  readonly avgExecutionTime: Locator;

  // 策略选择
  readonly strategySelector: Locator;
  readonly autoStrategy: Locator;
  readonly reactStrategy: Locator;
  readonly planExecuteStrategy: Locator;
  readonly predefinedStrategy: Locator;

  // 输入表单
  readonly goalInput: Locator;
  readonly sessionIdInput: Locator;

  // 步骤构建器
  readonly stepBuilder: Locator;
  readonly addStepButton: Locator;
  readonly executeButton: Locator;
  readonly resetButton: Locator;

  // 执行结果
  readonly resultPanel: Locator;
  readonly resultStatus: Locator;
  readonly resultOutput: Locator;
  readonly copyOutputButton: Locator;
  readonly stepTimeline: Locator;

  // 执行历史
  readonly historyPanel: Locator;
  readonly historyList: Locator;
  readonly clearHistoryButton: Locator;

  constructor(page: Page) {
    this.page = page;

    // 页面容器
    this.container = page.locator('.planning-view');

    // 统计卡片
    this.executionCount = page.locator('.el-statistic').filter({ hasText: '总执行次数' });
    this.successCount = page.locator('.el-statistic').filter({ hasText: '成功' });
    this.failureCount = page.locator('.el-statistic').filter({ hasText: '失败' });
    this.avgExecutionTime = page.locator('.el-statistic').filter({ hasText: '平均耗时' });

    // 策略选择
    this.strategySelector = page.locator('.el-radio-group');
    this.autoStrategy = page.locator('.el-radio-button').filter({ hasText: '自动选择' });
    this.reactStrategy = page.locator('.el-radio-button').filter({ hasText: 'ReAct' });
    this.planExecuteStrategy = page.locator('.el-radio-button').filter({ hasText: 'Plan-Execute' });
    this.predefinedStrategy = page.locator('.el-radio-button').filter({ hasText: '预定义任务' });

    // 输入表单
    this.goalInput = page.locator('textarea').first();
    this.sessionIdInput = page.locator('input[placeholder*="Session"]').or(
      page.locator('.el-form-item:has-text("Session") input')
    );

    // 步骤构建器
    this.stepBuilder = page.locator('.step-builder');
    this.addStepButton = page.locator('button:has-text("添加步骤")');

    // 操作按钮
    this.executeButton = page.locator('button:has-text("执行任务")');
    this.resetButton = page.locator('button:has-text("重置")');

    // 执行结果
    this.resultPanel = page.locator('.execution-result-panel');
    this.resultStatus = page.locator('.execution-result-panel .el-tag');
    this.resultOutput = page.locator('.output-textarea textarea');
    this.copyOutputButton = page.locator('.execution-result-panel button:has-text("复制")');
    this.stepTimeline = page.locator('.el-timeline');

    // 执行历史
    this.historyPanel = page.locator('.execution-history-panel');
    this.historyList = page.locator('.history-list');
    this.clearHistoryButton = page.locator('.execution-history-panel button:has-text("清空")');
  }

  /**
   * 导航到任务规划页面
   */
  async goto() {
    await this.page.goto('/admin/planning');
    await this.page.waitForLoadState('networkidle');
  }

  /**
   * 选择执行策略
   */
  async selectStrategy(strategy: 'auto' | 'react' | 'plan-execute' | 'predefined') {
    const selectors: Record<string, Locator> = {
      auto: this.autoStrategy,
      react: this.reactStrategy,
      'plan-execute': this.planExecuteStrategy,
      predefined: this.predefinedStrategy
    };
    await selectors[strategy].click();
    await this.page.waitForTimeout(100);
  }

  /**
   * 输入目标
   */
  async fillGoal(goal: string) {
    await this.goalInput.fill(goal);
  }

  /**
   * 输入 Session ID
   */
  async fillSessionId(sessionId: string) {
    const input = this.page.locator('.el-form-item:has-text("Session") input');
    await input.fill(sessionId);
  }

  /**
   * 添加步骤（预定义模式）
   */
  async addStep(description: string, action?: string, tool?: string, params?: Record<string, unknown>) {
    await this.addStepButton.click();
    await this.page.waitForTimeout(100);

    // 获取最新的步骤卡片
    const stepCards = this.page.locator('.step-item');
    const lastCard = stepCards.last();

    // 填写描述
    await lastCard.locator('input[placeholder*="描述"]').fill(description);

    if (tool) {
      // 选择工具模式
      await lastCard.locator('.el-radio[value="tool"]').click();
      await lastCard.locator('input[placeholder*="工具名称"]').fill(tool);
      if (params) {
        await lastCard.locator('.params-input textarea').fill(JSON.stringify(params));
      }
    } else if (action) {
      // 动作模式
      await lastCard.locator('.el-radio[value="action"]').click();
      await lastCard.locator('input[placeholder*="动作"]').fill(action);
    }
  }

  /**
   * 删除步骤
   */
  async removeStep(index: number) {
    const stepCards = this.page.locator('.step-item');
    const card = stepCards.nth(index);
    await card.locator('button.el-button--danger').click();
  }

  /**
   * 执行任务
   */
  async execute() {
    await this.executeButton.click();
  }

  /**
   * 重置表单
   */
  async reset() {
    await this.resetButton.click();
  }

  /**
   * 清空历史
   */
  async clearHistory() {
    await this.clearHistoryButton.click();
    // 确认对话框
    await this.page.locator('.el-message-box__headerBtn + .el-message-box__btns button').first().click();
  }

  /**
   * 从历史加载结果
   */
  async loadFromHistory(index: number) {
    const historyItems = this.page.locator('.history-item');
    await historyItems.nth(index).click();
  }

  /**
   * 获取历史记录数量
   */
  async getHistoryCount(): Promise<number> {
    return await this.page.locator('.history-item').count();
  }

  /**
   * 验证页面加载
   */
  async expectPageLoaded() {
    await expect(this.container).toBeVisible();
    await expect(this.strategySelector).toBeVisible();
    await expect(this.goalInput).toBeVisible();
  }

  /**
   * 验证执行结果状态
   */
  async expectResultStatus(success: boolean) {
    const tagType = success ? 'success' : 'danger';
    await expect(this.resultPanel.locator(`.el-tag--${tagType}`)).toBeVisible();
  }

  /**
   * 验证步骤时间线存在
   */
  async expectStepTimelineVisible() {
    await expect(this.stepTimeline).toBeVisible();
  }

  /**
   * 获取步骤数量
   */
  async getStepCount(): Promise<number> {
    return await this.stepTimeline.locator('.el-timeline-item').count();
  }
}
