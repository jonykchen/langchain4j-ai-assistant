import { test, expect } from '../fixtures/test-fixtures';
import { PlanningPage } from '../pages/PlanningPage';
import { PlanningMock } from '../mocks/planning-mock';

test.describe('任务规划页面测试', () => {
  let planningPage: PlanningPage;
  let planningMock: PlanningMock;

  test.beforeEach(async ({ page }) => {
    planningPage = new PlanningPage(page);
    planningMock = new PlanningMock(page);

    // Mock 认证接口
    await page.route('**/auth/me', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          message: 'success',
          data: {
            id: 'test-admin-id',
            username: 'adminuser',
            role: 'ADMIN',
            createdAt: new Date().toISOString(),
          }
        })
      });
    });

    // 设置管理员认证状态
    await page.goto('/');
    await page.evaluate(() => {
      localStorage.setItem('access_token', 'admin-test-token');
      localStorage.setItem('refresh_token', 'admin-refresh-token');
      localStorage.setItem('token_expiry', String(Date.now() + 3600000));
      localStorage.setItem('user_info', JSON.stringify({
        id: 'test-admin-id',
        username: 'adminuser',
        role: 'ADMIN',
      }));
      // 清空历史记录避免影响测试
      localStorage.removeItem('planning_history');
    });

    await page.context().addCookies([{
      name: 'auth_token',
      value: 'admin-test-token',
      domain: 'localhost',
      path: '/',
    }]);
  });

  test.describe('页面基础功能', () => {
    test('页面加载', async ({ page }) => {
      await planningPage.goto();
      await planningPage.expectPageLoaded();

      // 验证策略选择器存在
      await expect(page.locator('.el-radio-group')).toBeVisible();

      // 验证输入区域存在
      await expect(page.locator('textarea')).toBeVisible();
    });

    test('策略切换', async ({ page }) => {
      await planningPage.goto();

      // 默认是自动选择
      await expect(page.locator('.el-radio-button').first()).toHaveAttribute('aria-checked', 'true');

      // 切换到 ReAct
      await planningPage.selectStrategy('react');
      await expect(page.locator('.el-radio-button:has-text("ReAct")')).toHaveAttribute('aria-checked', 'true');

      // 切换到 Plan-Execute
      await planningPage.selectStrategy('plan-execute');
      await expect(page.locator('.el-radio-button:has-text("Plan-Execute")')).toHaveAttribute('aria-checked', 'true');

      // 切换到预定义任务
      await planningPage.selectStrategy('predefined');
      await expect(page.locator('.el-radio-button:has-text("预定义任务")')).toHaveAttribute('aria-checked', 'true');

      // 验证步骤构建器在预定义模式下显示
      await expect(page.locator('.step-builder')).toBeVisible();
    });
  });

  test.describe('执行功能测试', () => {
    test('自动模式执行', async ({ page }) => {
      await planningMock.mockExecute();
      await planningPage.goto();

      // 输入目标
      await planningPage.fillGoal('分析数据并生成报告');

      // 执行
      await planningPage.execute();

      // 等待结果
      await page.waitForTimeout(500);

      // 验证结果面板显示
      await expect(page.locator('.execution-result-panel')).toBeVisible();

      // 验证成功状态
      await expect(page.locator('.execution-result-panel .el-tag--success')).toBeVisible();
    });

    test('ReAct 模式执行', async ({ page }) => {
      await planningMock.mockReAct();
      await planningPage.goto();

      // 选择 ReAct 模式
      await planningPage.selectStrategy('react');

      // 输入问题
      await planningPage.fillGoal('什么是机器学习？');

      // 执行
      await planningPage.execute();

      // 等待结果
      await page.waitForTimeout(500);

      // 验证结果
      await expect(page.locator('.execution-result-panel')).toBeVisible();

      // 验证迭代次数显示（ReAct 特有）
      await expect(page.locator('.el-descriptions-item:has-text("迭代次数")')).toBeVisible();
    });

    test('Plan-Execute 模式执行', async ({ page }) => {
      await planningMock.mockPlanExecute();
      await planningPage.goto();

      // 选择 Plan-Execute 模式
      await planningPage.selectStrategy('plan-execute');

      // 输入目标
      await planningPage.fillGoal('完成数据分析流程');

      // 执行
      await planningPage.execute();

      // 等待结果
      await page.waitForTimeout(500);

      // 验证结果
      await expect(page.locator('.execution-result-panel')).toBeVisible();

      // 验证步骤时间线（Plan-Execute 应有多个步骤）
      await expect(page.locator('.el-timeline')).toBeVisible();
    });

    test('预定义任务执行', async ({ page }) => {
      await planningMock.mockPredefinedTask();
      await planningPage.goto();

      // 选择预定义模式
      await planningPage.selectStrategy('predefined');

      // 输入目标
      await planningPage.fillGoal('执行数据处理流程');

      // 添加步骤
      await planningPage.addStep('步骤1：读取数据', '读取 CSV 文件');
      await planningPage.addStep('步骤2：处理数据', null, 'calculator', { operation: 'sum' });

      // 执行
      await planningPage.execute();

      // 等待结果
      await page.waitForTimeout(500);

      // 验证结果
      await expect(page.locator('.execution-result-panel')).toBeVisible();
    });

    test('执行失败处理', async ({ page }) => {
      await planningMock.mockExecuteFailure('execute', '参数验证失败');
      await planningPage.goto();

      await planningPage.fillGoal('测试失败场景');
      await planningPage.execute();

      await page.waitForTimeout(500);

      // 验证失败状态显示
      await expect(page.locator('.execution-result-panel .el-tag--danger')).toBeVisible();

      // 验证错误信息显示
      await expect(page.locator('.el-alert--error')).toBeVisible();
    });

    test('服务端错误处理', async ({ page }) => {
      await planningMock.mockServerError();
      await planningPage.goto();

      await planningPage.fillGoal('测试错误场景');
      await planningPage.execute();

      await page.waitForTimeout(500);

      // 验证错误提示
      await expect(page.locator('.el-message--error')).toBeVisible();
    });
  });

  test.describe('步骤构建器测试', () => {
    test.beforeEach(async ({ page }) => {
      await planningPage.goto();
      await planningPage.selectStrategy('predefined');
    });

    test('添加步骤', async ({ page }) => {
      // 添加第一个步骤
      await planningPage.addStep('步骤描述1', '执行动作1');

      // 验证步骤卡片存在
      const stepCards = page.locator('.step-item');
      await expect(stepCards).toHaveCount(1);

      // 添加第二个步骤
      await planningPage.addStep('步骤描述2', null, 'search', { query: 'test' });

      await expect(stepCards).toHaveCount(2);
    });

    test('删除步骤', async ({ page }) => {
      // 添加两个步骤
      await planningPage.addStep('步骤1', '动作1');
      await planningPage.addStep('步骤2', '动作2');

      const stepCards = page.locator('.step-item');
      await expect(stepCards).toHaveCount(2);

      // 删除第一个步骤
      await planningPage.removeStep(0);

      await expect(stepCards).toHaveCount(1);
    });

    test('切换步骤类型', async ({ page }) => {
      await planningPage.addStep('测试步骤', '初始动作');

      const stepCard = page.locator('.step-item').first();

      // 验证动作模式选中
      await expect(stepCard.locator('.el-radio[value="action"]')).toHaveAttribute('aria-checked', 'true');

      // 切换到工具模式
      await stepCard.locator('.el-radio[value="tool"]').click();

      // 验证工具模式选中
      await expect(stepCard.locator('.el-radio[value="tool"]')).toHaveAttribute('aria-checked', 'true');

      // 验证工具输入框显示
      await expect(stepCard.locator('input[placeholder*="工具名称"]')).toBeVisible();
    });
  });

  test.describe('执行历史测试', () => {
    test('执行后添加历史', async ({ page }) => {
      await planningMock.mockExecute();
      await planningPage.goto();

      // 初始历史为空
      const historyItems = page.locator('.history-item');
      await expect(historyItems).toHaveCount(0);

      // 执行任务
      await planningPage.fillGoal('第一次执行');
      await planningPage.execute();
      await page.waitForTimeout(500);

      // 验证历史记录添加
      await expect(historyItems).toHaveCount(1);

      // 再执行一次
      await planningPage.fillGoal('第二次执行');
      await planningPage.execute();
      await page.waitForTimeout(500);

      await expect(historyItems).toHaveCount(2);
    });

    test('从历史加载结果', async ({ page }) => {
      await planningMock.mockExecute();
      await planningPage.goto();

      // 执行任务
      await planningPage.fillGoal('测试历史加载');
      await planningPage.execute();
      await page.waitForTimeout(500);

      // 点击历史记录
      await page.locator('.history-item').first().click();

      // 验证结果面板显示历史结果
      await expect(page.locator('.execution-result-panel')).toBeVisible();
    });

    test('清空历史', async ({ page }) => {
      await planningMock.mockExecute();
      await planningPage.goto();

      // 执行任务添加历史
      await planningPage.fillGoal('测试清空');
      await planningPage.execute();
      await page.waitForTimeout(500);

      // 验证历史存在
      await expect(page.locator('.history-item')).toHaveCount(1);

      // 点击清空按钮
      await page.locator('.execution-history-panel button:has-text("清空")').click();

      // 确认对话框
      await page.locator('.el-message-box__btns button:has-text("确定")').click();

      // 验证历史清空
      await expect(page.locator('.history-item')).toHaveCount(0);
    });
  });

  test.describe('表单验证测试', () => {
    test('空目标验证', async ({ page }) => {
      await planningPage.goto();

      // 不输入目标直接执行
      await planningPage.execute();

      // 验证警告提示
      await expect(page.locator('.el-message--warning')).toBeVisible();
    });

    test('预定义任务空步骤验证', async ({ page }) => {
      await planningMock.mockPredefinedTask();
      await planningPage.goto();

      // 选择预定义模式但不添加步骤
      await planningPage.selectStrategy('predefined');
      await planningPage.fillGoal('测试空步骤');

      // 执行
      await planningPage.execute();

      // 验证警告提示
      await expect(page.locator('.el-message--warning')).toBeVisible();
    });
  });

  test.describe('重置功能测试', () => {
    test('重置表单', async ({ page }) => {
      await planningMock.mockExecute();
      await planningPage.goto();

      // 输入数据
      await planningPage.fillGoal('测试数据');
      await planningPage.fillSessionId('test-session');

      // 执行获取结果
      await planningPage.execute();
      await page.waitForTimeout(500);

      // 验证结果存在
      await expect(page.locator('.execution-result-panel')).toBeVisible();

      // 重置
      await planningPage.reset();

      // 验证表单清空
      await expect(page.locator('textarea').first()).toHaveValue('');
      await expect(page.locator('.el-form-item:has-text("Session") input')).toHaveValue('');

      // 验证结果面板隐藏
      await expect(page.locator('.execution-result-panel')).not.toBeVisible();
    });
  });

  test.describe('统计卡片测试', () => {
    test('执行后统计更新', async ({ page }) => {
      await planningMock.mockExecute();
      await planningPage.goto();

      // 初始统计为 0
      const initialCount = await page.locator('.el-statistic .el-statistic__number').first().textContent();
      expect(initialCount).toBe('0');

      // 执行任务
      await planningPage.fillGoal('测试统计');
      await planningPage.execute();
      await page.waitForTimeout(500);

      // 验证统计更新
      const newCount = await page.locator('.el-statistic .el-statistic__number').first().textContent();
      expect(parseInt(newCount || '0')).toBeGreaterThan(0);
    });
  });

  test.describe('复制功能测试', () => {
    test('复制输出结果', async ({ page }) => {
      await planningMock.mockExecute({
        output: '这是测试输出内容，可以复制到剪贴板。'
      });
      await planningPage.goto();

      await planningPage.fillGoal('测试复制');
      await planningPage.execute();
      await page.waitForTimeout(500);

      // 点击复制按钮
      await page.locator('.execution-result-panel button:has-text("复制")').click();

      // 验证成功提示
      await expect(page.locator('.el-message--success')).toBeVisible();
    });
  });
});

test.describe('侧边栏导航测试', () => {
  test.beforeEach(async ({ page }) => {
    // Mock 认证
    await page.route('**/auth/me', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          message: 'success',
          data: {
            id: 'test-admin-id',
            username: 'adminuser',
            role: 'ADMIN',
            createdAt: new Date().toISOString(),
          }
        })
      });
    });

    // 设置管理员认证
    await page.goto('/');
    await page.evaluate(() => {
      localStorage.setItem('access_token', 'admin-test-token');
      localStorage.setItem('user_info', JSON.stringify({
        id: 'test-admin-id',
        username: 'adminuser',
        role: 'ADMIN',
      }));
    });
  });

  test('从侧边栏导航到任务规划', async ({ page }) => {
    await page.goto('/admin');
    await page.waitForLoadState('networkidle');

    // 点击侧边栏"任务规划"
    await page.click('.el-menu-item:has-text("任务规划")');
    await page.waitForLoadState('networkidle');

    // 验证跳转到正确页面
    expect(page.url()).toContain('/admin/planning');

    // 验证页面内容加载
    await expect(page.locator('.planning-view')).toBeVisible();
  });
});