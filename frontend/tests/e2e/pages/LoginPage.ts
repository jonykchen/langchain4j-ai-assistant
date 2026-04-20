import { Page, Locator, expect } from '@playwright/test';

/**
 * 登录页面对象模型
 */
export class LoginPage {
  readonly page: Page;
  readonly usernameInput: Locator;
  readonly passwordInput: Locator;
  readonly loginButton: Locator;
  readonly githubLoginButton: Locator;
  readonly gitlabLoginButton: Locator;
  readonly errorMessage: Locator;
  readonly registerLink: Locator;

  constructor(page: Page) {
    this.page = page;
    this.usernameInput = page.locator('[data-testid="username-input"], input[type="text"]').first();
    this.passwordInput = page.locator('[data-testid="password-input"], input[type="password"]').first();
    this.loginButton = page.locator('[data-testid="login-button"], button:has-text("登录")');
    this.githubLoginButton = page.locator('[data-testid="github-login"], button:has-text("GitHub")');
    this.gitlabLoginButton = page.locator('[data-testid="gitlab-login"], button:has-text("GitLab")');
    this.errorMessage = page.locator('.el-message--error, .error-message');
    this.registerLink = page.locator('[data-testid="register-link"], a:has-text("注册")');
  }

  /**
   * 导航到登录页面
   */
  async goto() {
    await this.page.goto('/login');
    await this.page.waitForLoadState('networkidle');
  }

  /**
   * 执行登录
   */
  async login(username: string, password: string) {
    await this.usernameInput.fill(username);
    await this.passwordInput.fill(password);
    await this.loginButton.click();
  }

  /**
   * 验证登录成功
   */
  async expectLoginSuccess() {
    await expect(this.page).toHaveURL(/^(?!.*login).*$/, { timeout: 10000 });
  }

  /**
   * 验证登录失败
   */
  async expectLoginFailure(message?: string) {
    await expect(this.errorMessage).toBeVisible();
    if (message) {
      await expect(this.errorMessage).toContainText(message);
    }
  }

  /**
   * GitHub OAuth 登录
   */
  async loginWithGitHub() {
    await this.githubLoginButton.click();
  }

  /**
   * GitLab OAuth 登录
   */
  async loginWithGitLab() {
    await this.gitlabLoginButton.click();
  }

  /**
   * 点击注册链接
   */
  async goToRegister() {
    await this.registerLink.click();
  }
}
