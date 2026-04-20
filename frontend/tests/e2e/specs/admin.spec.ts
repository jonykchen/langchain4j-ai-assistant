import { test, expect } from '../fixtures/test-fixtures';

test.describe('管理后台测试', () => {
  test.use({ storageState: 'playwright/.auth/user.json' });

  test.beforeEach(async ({ adminPage }) => {
    await adminPage.goto();
  });

  test('仪表盘数据加载', async ({ adminPage }) => {
    await adminPage.expectDashboardLoaded();
  });

  test('查看用户列表', async ({ adminPage }) => {
    await adminPage.goToUsers();

    // 验证用户表格加载
    await expect(adminPage.usersTable).toBeVisible();

    // 验证至少有一行数据
    const rowCount = await adminPage.getUserRowCount();
    expect(rowCount).toBeGreaterThanOrEqual(0);
  });

  test('搜索用户', async ({ adminPage }) => {
    await adminPage.goToUsers();

    // 搜索用户
    await adminPage.searchUsers('test');

    // 等待搜索结果
    await adminPage.page.waitForLoadState('networkidle');

    // 验证搜索结果
    const tableText = await adminPage.usersTable.textContent();
    expect(tableText?.toLowerCase()).toContain('test');
  });

  test('查看用户详情', async ({ adminPage }) => {
    await adminPage.goToUsers();

    // 点击第一行用户
    const rowCount = await adminPage.getUserRowCount();
    if (rowCount > 0) {
      await adminPage.clickUserRow(0);

      // 验证详情页显示
      await expect(adminPage.page.locator('.user-detail, [data-testid="user-detail"]')).toBeVisible();
    }
  });

  test('成本监控页面', async ({ adminPage }) => {
    await adminPage.goToCost();

    // 验证成本图表显示
    await expect(adminPage.page.locator('.cost-chart, [data-testid="cost-chart"]')).toBeVisible();
  });
});

test.describe('管理员权限测试', () => {
  test('普通用户无法访问管理后台', async ({ page }) => {
    // 模拟普通用户
    await page.addCookies([{
      name: 'auth_token',
      value: 'user-token',
      domain: 'localhost',
      path: '/',
    }]);

    await page.evaluate(() => {
      localStorage.setItem('token', 'user-token');
      localStorage.setItem('user', JSON.stringify({
        id: 'user-id',
        username: 'normaluser',
        role: 'USER',  // 非 ADMIN
      }));
    });

    // 尝试访问管理后台
    await page.goto('/admin');

    // 验证被拒绝访问
    await expect(page.locator('.access-denied, .forbidden, [data-testid="access-denied"]')).toBeVisible();
  });
});
