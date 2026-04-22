import { Page, Route } from '@playwright/test';

/**
 * 任务规划 API Mock
 * 覆盖 PlanningController 的四个接口
 */
export class PlanningMock {
  private page: Page;

  constructor(page: Page) {
    this.page = page;
  }

  /**
   * Mock 自动执行 API
   */
  async mockExecute(result?: Partial<MockTaskResult>) {
    await this.page.route('**/api/planning/execute', async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          message: 'success',
          data: this.buildResult(result)
        })
      });
    });
  }

  /**
   * Mock ReAct 执行 API
   */
  async mockReAct(result?: Partial<MockTaskResult>) {
    await this.page.route('**/api/planning/react', async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          message: 'success',
          data: this.buildResult({
            iterations: 3,
            stepResults: [
              { success: true, output: '思考：需要先搜索相关信息', error: null, observation: null },
              { success: true, output: '行动：调用搜索工具', error: null, observation: '搜索结果：找到相关数据' },
              { success: true, output: '基于搜索结果生成答案', error: null, observation: null },
            ],
            ...result
          })
        })
      });
    });
  }

  /**
   * Mock Plan-Execute 执行 API
   */
  async mockPlanExecute(result?: Partial<MockTaskResult>) {
    await this.page.route('**/api/planning/plan-execute', async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          message: 'success',
          data: this.buildResult({
            stepResults: [
              { success: true, output: '规划：制定执行计划', error: null, observation: '计划包含3个步骤' },
              { success: true, output: '执行步骤1：数据收集', error: null, observation: '收集完成' },
              { success: true, output: '执行步骤2：数据分析', error: null, observation: '分析完成' },
              { success: true, output: '执行步骤3：生成报告', error: null, observation: null },
            ],
            ...result
          })
        })
      });
    });
  }

  /**
   * Mock 预定义任务执行 API
   */
  async mockPredefinedTask(result?: Partial<MockTaskResult>) {
    await this.page.route('**/api/planning/task', async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          message: 'success',
          data: this.buildResult({
            stepResults: [
              { success: true, output: '步骤1执行完成', error: null, observation: '中间结果1' },
              { success: true, output: '步骤2执行完成', error: null, observation: '中间结果2' },
            ],
            ...result
          })
        })
      });
    });
  }

  /**
   * Mock 执行失败
   */
  async mockExecuteFailure(strategy: string = 'execute', errorMessage: string = '任务执行失败') {
    const pathMap: Record<string, string> = {
      execute: '**/api/planning/execute',
      react: '**/api/planning/react',
      'plan-execute': '**/api/planning/plan-execute',
      predefined: '**/api/planning/task'
    };
    const pattern = pathMap[strategy] || pathMap.execute;

    await this.page.route(pattern, async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          message: 'success',
          data: this.buildResult({
            success: false,
            error: errorMessage,
            stepResults: [
              { success: true, output: '步骤1完成', error: null, observation: null },
              { success: false, output: null, error: errorMessage, observation: '部分执行失败' },
            ]
          })
        })
      });
    });
  }

  /**
   * Mock 网络超时
   */
  async mockNetworkTimeout() {
    await this.page.route('**/api/planning/**', async (route: Route) => {
      await route.abort('timedout');
    });
  }

  /**
   * Mock 服务端错误
   */
  async mockServerError() {
    await this.page.route('**/api/planning/**', async (route: Route) => {
      await route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 500,
          message: '服务内部错误',
          data: null
        })
      });
    });
  }

  /**
   * 构建标准结果
   */
  private buildResult(overrides?: Partial<MockTaskResult>): MockTaskResult {
    return {
      taskId: `task-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
      success: true,
      output: '任务执行完成，结果如下：分析报告已生成。',
      stepResults: [
        { success: true, output: '步骤执行成功', error: null, observation: null }
      ],
      error: null,
      executionTimeMs: 1500,
      iterations: 1,
      ...overrides
    };
  }

  /**
   * 移除所有 Mock
   */
  async unrouteAll() {
    await this.page.unroute('**/api/planning/execute');
    await this.page.unroute('**/api/planning/react');
    await this.page.unroute('**/api/planning/plan-execute');
    await this.page.unroute('**/api/planning/task');
    await this.page.unroute('**/api/planning/**');
  }
}

/** Mock 结果类型 */
interface MockTaskResult {
  taskId: string;
  success: boolean;
  output: string | null;
  stepResults: Array<{
    success: boolean;
    output: string | null;
    error: string | null;
    observation: string | null;
  }>;
  error: string | null;
  executionTimeMs: number;
  iterations: number;
}
