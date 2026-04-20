import { test as setup, expect } from '@playwright/test';

/**
 * 认证设置测试
 * 在运行其他测试前，先进行认证设置
 * 将认证状态保存到 storageState 中
 */

const authFile = 'playwright/.auth/user.json';

setup('authenticate', async ({ page }) => {
  // 如果测试环境提供了 Token，直接使用
  if (process.env.TEST_AUTH_TOKEN) {
    await page.goto('/');

    // 设置 localStorage
    await page.evaluate((token) => {
      localStorage.setItem('token', token);
      localStorage.setItem('user', JSON.stringify({
        id: 'test-user-id',
        username: 'testuser',
        role: 'ADMIN',
      }));
    }, process.env.TEST_AUTH_TOKEN);

    // 设置 cookie
    await page.context().addCookies([{
      name: 'auth_token',
      value: process.env.TEST_AUTH_TOKEN,
      domain: 'localhost',
      path: '/',
    }]);
  } else {
    // 否则通过登录获取认证
    await page.goto('/login');

    // 使用测试账号登录
    await page.fill('[data-testid="username-input"]', process.env.TEST_USERNAME || 'testuser');
    await page.fill('[data-testid="password-input"]', process.env.TEST_PASSWORD || 'password123');
    await page.click('[data-testid="login-button"]');

    // 等待登录成功跳转
    await page.waitForURL(/^(?!.*login).*$/, { timeout: 10000 });
  }

  // 验证认证成功
  await page.goto('/');
  await expect(page.locator('[data-testid="user-info"]')).toBeVisible();

  // 保存认证状态
  await page.context().storageState({ path: authFile });
});
