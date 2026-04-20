import { Page, Locator, expect } from '@playwright/test';

/**
 * 管理员后台页面对象模型
 */
export class AdminPage {
  readonly page: Page;
  readonly sidebar: Locator;
  readonly dashboardMenuItem: Locator;
  readonly usersMenuItem: Locator;
  readonly costMenuItem: Locator;
  readonly testMenuItem: Locator;

  // 仪表盘元素
  readonly totalUsersCard: Locator;
  readonly activeUsersCard: Locator;
  readonly dailyRequestsCard: Locator;

  // 用户管理元素
  readonly usersTable: Locator;
  readonly searchInput: Locator;
  readonly roleFilter: Locator;
  readonly createUserButton: Locator;

  constructor(page: Page) {
    this.page = page;
    // 侧边栏菜单
    this.sidebar = page.locator('[data-testid="admin-sidebar"], .admin-sidebar');
    this.dashboardMenuItem = page.locator('[data-testid="menu-dashboard"], a:has-text("仪表盘")');
    this.usersMenuItem = page.locator('[data-testid="menu-users"], a:has-text("用户管理")');
    this.costMenuItem = page.locator('[data-testid="menu-cost"], a:has-text("成本监控")');
    this.testMenuItem = page.locator('[data-testid="menu-test"], a:has-text("测试管理")');

    // 仪表盘卡片
    this.totalUsersCard = page.locator('[data-testid="total-users-card"]');
    this.activeUsersCard = page.locator('[data-testid="active-users-card"]');
    this.dailyRequestsCard = page.locator('[data-testid="daily-requests-card"]');

    // 用户管理
    this.usersTable = page.locator('[data-testid="users-table"], .el-table');
    this.searchInput = page.locator('[data-testid="search-input"], input[placeholder*="搜索"]');
    this.roleFilter = page.locator('[data-testid="role-filter"], .role-filter');
    this.createUserButton = page.locator('[data-testid="create-user-button"], button:has-text("创建用户")');
  }

  /**
   * 导航到管理后台
   */
  async goto() {
    await this.page.goto('/admin');
    await this.page.waitForLoadState('networkidle');
  }

  /**
   * 导航到仪表盘
   */
  async goToDashboard() {
    await this.dashboardMenuItem.click();
    await this.page.waitForLoadState('networkidle');
  }

  /**
   * 导航到用户管理
   */
  async goToUsers() {
    await this.usersMenuItem.click();
    await this.page.waitForLoadState('networkidle');
  }

  /**
   * 导航到成本监控
   */
  async goToCost() {
    await this.costMenuItem.click();
    await this.page.waitForLoadState('networkidle');
  }

  /**
   * 导航到测试管理
   */
  async goToTest() {
    await this.testMenuItem.click();
    await this.page.waitForLoadState('networkidle');
  }

  /**
   * 搜索用户
   */
  async searchUsers(keyword: string) {
    await this.searchInput.fill(keyword);
    await this.page.keyboard.press('Enter');
    await this.page.waitForLoadState('networkidle');
  }

  /**
   * 获取用户列表行数
   */
  async getUserRowCount(): Promise<number> {
    return await this.usersTable.locator('tbody tr').count();
  }

  /**
   * 点击用户行
   */
  async clickUserRow(rowIndex: number) {
    const rows = this.usersTable.locator('tbody tr');
    await rows.nth(rowIndex).click();
  }

  /**
   * 验证仪表盘数据加载
   */
  async expectDashboardLoaded() {
    await expect(this.totalUsersCard).toBeVisible();
    await expect(this.activeUsersCard).toBeVisible();
    await expect(this.dailyRequestsCard).toBeVisible();
  }

  /**
   * 获取仪表盘统计数值
   */
  async getDashboardStat(cardLocator: Locator): Promise<string> {
    const statValue = cardLocator.locator('.stat-value, [data-testid="stat-value"]');
    return (await statValue.textContent())?.trim() || '0';
  }
}
