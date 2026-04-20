import { test as base, Page } from '@playwright/test';
import { ChatPage } from '../pages/ChatPage';
import { LoginPage } from '../pages/LoginPage';
import { AdminPage } from '../pages/AdminPage';

/**
 * 自定义测试夹具
 * 扩展 Playwright test 对象，注入页面对象
 */
type TestFixtures = {
  chatPage: ChatPage;
  loginPage: LoginPage;
  adminPage: AdminPage;
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

  // 已认证的页面
  authenticatedPage: async ({ page }, use) => {
    // 设置认证 Token
    await page.addCookies([{
      name: 'auth_token',
      value: process.env.TEST_AUTH_TOKEN || 'test-jwt-token',
      domain: 'localhost',
      path: '/',
    }]);

    // 设置 localStorage 认证信息
    await page.goto('/');
    await page.evaluate((token) => {
      localStorage.setItem('token', token);
      localStorage.setItem('user', JSON.stringify({
        id: 'test-user-id',
        username: 'testuser',
        role: 'ADMIN',
      }));
    }, process.env.TEST_AUTH_TOKEN || 'test-jwt-token');

    await use(page);
  },
});

export { expect } from '@playwright/test';
