import { test as setup, expect } from '@playwright/test';

/**
 * 认证设置测试
 * 在运行其他测试前，先进行认证设置
 * 将认证状态保存到 storageState 中
 *
 * 策略：直接通过 Mock 方式创建认证状态，不依赖后端服务
 */

const authFile = 'playwright/.auth/user.json';

setup('authenticate', async ({ page }) => {
  // Mock 所有认证相关 API，避免依赖后端服务
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

  await page.route('**/auth/login', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        message: 'success',
        data: {
          token: {
            accessToken: 'test-jwt-token',
            refreshToken: 'test-refresh-token',
            tokenType: 'Bearer',
            expiresIn: 3600,
          },
          user: {
            id: 'test-user-id',
            username: 'testuser',
            role: 'ADMIN',
          }
        }
      })
    });
  });

  await page.route('**/auth/refresh', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        message: 'success',
        data: {
          accessToken: 'new-jwt-token',
          refreshToken: 'new-refresh-token',
          tokenType: 'Bearer',
          expiresIn: 3600,
        }
      })
    });
  });

  // 导航到首页
  await page.goto('/');

  // 直接设置认证状态（不依赖后端登录）
  await page.evaluate(() => {
    localStorage.setItem('access_token', 'test-jwt-token');
    localStorage.setItem('refresh_token', 'test-refresh-token');
    localStorage.setItem('token_expiry', String(Date.now() + 3600000));
    localStorage.setItem('user_info', JSON.stringify({
      id: 'test-user-id',
      username: 'testuser',
      role: 'ADMIN',
    }));
  });

  // 设置 cookie
  await page.context().addCookies([{
    name: 'auth_token',
    value: 'test-jwt-token',
    domain: 'localhost',
    path: '/',
  }]);

  // 保存认证状态
  await page.context().storageState({ path: authFile });
});
