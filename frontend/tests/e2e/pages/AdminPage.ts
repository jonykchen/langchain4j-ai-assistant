import { Page, Locator, expect } from '@playwright/test';

/**
 * 管理员后台页面对象模型
 * 使用更宽泛的选择器来匹配实际前端实现
 */
export class AdminPage {
  readonly page: Page;
  readonly dashboard: Locator;
  readonly usersView: Locator;
  readonly costView: Locator;

  // 用户管理元素
  readonly usersTable: Locator;
  readonly searchInput: Locator;

  constructor(page: Page) {
    this.page = page;

    // 仪表盘 - 使用 h2 标题或卡片容器
    this.dashboard = page.locator('.admin-dashboard, h2:has-text("系统概览")');

    // 用户管理 - 使用 el-table 选择器
    this.usersView = page.locator('.users-view, h2:has-text("用户管理")');
    this.usersTable = page.locator('.el-table');
    this.searchInput = page.locator('input[placeholder*="搜索"], input[placeholder*="查询"]');
    this.costView = page.locator('.cost-view, h2:has-text("成本监控")');
  }

  /**
   * 导航到管理后台
   */
  async goto() {
    await this.page.goto('/admin');
    await this.page.waitForLoadState('networkidle');
  }

  /**
   * 导航到用户管理
   */
  async goToUsers() {
    // 点击侧边栏"用户管理"
    await this.page.click('a:has-text("用户管理"), [data-testid="menu-users"]');
    await this.page.waitForLoadState('networkidle');
  }

  /**
   * 导航到成本监控
   */
  async goToCost() {
    await this.page.click('a:has-text("成本监控"), [data-testid="menu-cost"]');
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
    // 使用 h2 标题或 metric-card 来验证仪表盘加载
    await expect(this.page.locator('h2:has-text("系统概览"), .metric-card')).toBeVisible();
  }
}
