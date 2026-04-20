import { test, expect } from '../fixtures/test-fixtures';
import { AuthMock } from '../mocks/auth-mock';

test.describe('认证流程', () => {
  let authMock: AuthMock;

  test.beforeEach(async ({ loginPage }) => {
    authMock = new AuthMock(loginPage.page);
  });

  test.afterEach(async ({ loginPage }) => {
    await authMock?.clearAuthState();
  });

  test('用户名密码登录成功', async ({ loginPage }) => {
    await authMock.mockLoginSuccess();
    await loginPage.goto();
    await loginPage.login('testuser', 'password123');

    // 验证跳转到聊天页面
    await loginPage.expectLoginSuccess();
  });

  test('登录失败显示错误提示', async ({ loginPage }) => {
    await authMock.mockLoginFailure('用户名或密码错误');
    await loginPage.goto();
    await loginPage.login('wronguser', 'wrongpass');

    await loginPage.expectLoginFailure('用户名或密码错误');
  });

  test('GitHub OAuth 登录', async ({ loginPage }) => {
    await authMock.mockGitHubOAuthSuccess();
    await loginPage.goto();
    await loginPage.loginWithGitHub();

    // 验证 OAuth 登录成功
    await loginPage.expectLoginSuccess();
  });

  test('GitLab OAuth 登录', async ({ loginPage }) => {
    await authMock.mockGitLabOAuthSuccess();
    await loginPage.goto();
    await loginPage.loginWithGitLab();

    // 验证 OAuth 登录成功
    await loginPage.expectLoginSuccess();
  });

  test('Token 过期后自动刷新', async ({ page }) => {
    // 设置即将过期的 Token
    await page.addCookies([{
      name: 'auth_token',
      value: 'expired_token',
      domain: 'localhost',
      path: '/'
    }]);

    await authMock.mockTokenExpired();
    await authMock.mockTokenRefresh('new-refreshed-token');

    await page.goto('/');

    // 验证 Token 刷新
    const newToken = await page.evaluate(() => localStorage.getItem('token'));
    expect(newToken).toBe('new-refreshed-token');
  });

  test('未登录访问受保护页面跳转到登录', async ({ page }) => {
    await authMock.clearAuthState();

    // 访问需要认证的页面
    await page.goto('/');

    // 验证跳转到登录页面
    await expect(page).toHaveURL(/.*login.*/);
  });

  test('注册新用户', async ({ page }) => {
    await authMock.mockRegisterSuccess();
    await page.goto('/register');

    // 填写注册表单
    await page.fill('[data-testid="username-input"]', 'newuser');
    await page.fill('[data-testid="password-input"]', 'password123');
    await page.fill('[data-testid="confirm-password-input"]', 'password123');
    await page.click('[data-testid="register-button"]');

    // 验证注册成功
    await expect(page.locator('.el-message--success')).toBeVisible();
  });

  test('注册已存在用户显示错误', async ({ page }) => {
    await authMock.mockUserAlreadyExists();
    await page.goto('/register');

    await page.fill('[data-testid="username-input"]', 'existinguser');
    await page.fill('[data-testid="password-input"]', 'password123');
    await page.fill('[data-testid="confirm-password-input"]', 'password123');
    await page.click('[data-testid="register-button"]');

    // 验证错误提示
    await expect(page.locator('.el-message--error')).toBeVisible();
  });

  test('退出登录', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/');

    // 点击退出按钮
    await authenticatedPage.click('[data-testid="logout-button"]');

    // 验证跳转到登录页面
    await expect(authenticatedPage).toHaveURL(/.*login.*/);

    // 验证本地存储已清除
    const token = await authenticatedPage.evaluate(() => localStorage.getItem('token'));
    expect(token).toBeNull();
  });
});
