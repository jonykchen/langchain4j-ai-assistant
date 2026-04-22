import { test as base, Page } from '@playwright/test';
import { ChatPage } from '../pages/ChatPage';
import { LoginPage } from '../pages/LoginPage';
import { AdminPage } from '../pages/AdminPage';
import { PlanningPage } from '../pages/PlanningPage';

/**
 * 自定义测试夹具
 * 扩展 Playwright test 对象，注入页面对象
 */
type TestFixtures = {
  chatPage: ChatPage;
  loginPage: LoginPage;
  adminPage: AdminPage;
  planningPage: PlanningPage;
  authenticatedPage: Page;
};

export const test = base.extend<TestFixtures>({
  // 聊天页面对象
  chatPage: async ({ page }, use) => {
    const chatPage = new ChatPage(page);
    await use(chatPage);
  },

  // 登录页面对象
  loginPage: async ({ page }, use) => {
    const loginPage = new LoginPage(page);
    await use(loginPage);
  },

  // 管理员页面对象
  adminPage: async ({ page }, use) => {
    const adminPage = new AdminPage(page);
    await use(adminPage);
  },

  // 任务规划页面对象
  planningPage: async ({ page }, use) => {
    const planningPage = new PlanningPage(page);
    await use(planningPage);
  },

  // 已认证的页面
  authenticatedPage: async ({ page }, use) => {
    // Mock /auth/me 接口，避免后端依赖
    await page.route('**/auth/me', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          message: 'success',
          data: {
            id: 'test-user-id',
            username: 'testuser',
            role: 'ADMIN',
            createdAt: new Date().toISOString(),
          }
        })
      });
    });

    // 设置认证 Token
    await page.context().addCookies([{
      name: 'auth_token',
      value: process.env.TEST_AUTH_TOKEN || 'test-jwt-token',
      domain: 'localhost',
      path: '/',
    }]);

    // 先导航到有效页面，再设置 localStorage
    await page.goto('/');

    // 设置 localStorage 认证信息
    await page.evaluate((token) => {
      localStorage.setItem('access_token', token);
      localStorage.setItem('refresh_token', 'test-refresh-token');
      localStorage.setItem('token_expiry', String(Date.now() + 3600000));
      localStorage.setItem('user_info', JSON.stringify({
        id: 'test-user-id',
        username: 'testuser',
        role: 'ADMIN',
      }));
    }, process.env.TEST_AUTH_TOKEN || 'test-jwt-token');

    await use(page);
  },
});

export { expect } from '@playwright/test';
