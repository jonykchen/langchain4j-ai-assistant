import { Page, Route } from '@playwright/test';

/**
 * 认证 API Mock
 * 返回格式与前端类型定义 OAuthCallbackResponse / TokenResponse 一致
 */
export class AuthMock {
  private page: Page;

  constructor(page: Page) {
    this.page = page;
  }

  /**
   * Mock 登录成功
   * 返回格式与后端 OAuthCallbackResponse 一致
   */
  async mockLoginSuccess(token: string = 'test-jwt-token') {
    await this.page.route('**/auth/login', async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          message: 'success',
          data: {
            token: {
              accessToken: token,
              refreshToken: 'test-refresh-token',
              tokenType: 'Bearer',
              expiresIn: 3600,
            },
            user: {
              id: 'test-user-id',
              username: 'testuser',
              role: 'USER',
            }
          }
        })
      });
    });

    // 同时 Mock /auth/me 接口（登录后前端可能立即调用）
    await this.mockGetUserInfo({ username: 'testuser', role: 'USER' });
  }

  /**
   * Mock 登录失败
   */
  async mockLoginFailure(message: string = '用户名或密码错误') {
    await this.page.route('**/auth/login', async (route: Route) => {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 40101,
          message,
          data: null
        })
      });
    });
  }

  /**
   * Mock 注册成功
   */
  async mockRegisterSuccess() {
    await this.page.route('**/auth/register', async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          message: 'success',
          data: {
            userId: 'new-user-id',
            message: '注册成功'
          }
        })
      });
    });
  }

  /**
   * Mock 用户已存在
   */
  async mockUserAlreadyExists() {
    await this.page.route('**/auth/register', async (route: Route) => {
      await route.fulfill({
        status: 409,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 40901,
          message: '用户名已存在',
          data: null
        })
      });
    });
  }

  /**
   * Mock Token 刷新
   * 返回格式与后端 TokenResponse 一致
   */
  async mockTokenRefresh(newToken: string = 'new-jwt-token') {
    await this.page.route('**/auth/refresh', async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          message: 'success',
          data: {
            accessToken: newToken,
            refreshToken: 'new-refresh-token',
            tokenType: 'Bearer',
            expiresIn: 3600,
          }
        })
      });
    });
  }

  /**
   * Mock Token 过期
   * 当请求包含过期 Token 时返回 401
   */
  async mockTokenExpired() {
    await this.page.route('**/api/**', async (route: Route) => {
      const request = route.request();
      const authHeader = request.headers()['authorization'];

      if (authHeader?.includes('expired')) {
        await route.fulfill({
          status: 401,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 40102,
            message: 'Token 已过期',
            data: null
          })
        });
      } else {
        await route.continue();
      }
    });
  }

  /**
   * Mock GitHub OAuth 回调
   */
  async mockGitHubOAuthSuccess() {
    await this.page.route('**/auth/github/callback**', async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          message: 'success',
          data: {
            token: {
              accessToken: 'github-oauth-token',
              refreshToken: 'github-refresh-token',
              tokenType: 'Bearer',
              expiresIn: 3600,
            },
            user: {
              id: 'github-user-id',
              username: 'githubuser',
              provider: 'GITHUB',
              role: 'USER',
            }
          }
        })
      });
    });
  }

  /**
   * Mock GitLab OAuth 回调
   */
  async mockGitLabOAuthSuccess() {
    await this.page.route('**/auth/gitlab/callback**', async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          message: 'success',
          data: {
            token: {
              accessToken: 'gitlab-oauth-token',
              refreshToken: 'gitlab-refresh-token',
              tokenType: 'Bearer',
              expiresIn: 3600,
            },
            user: {
              id: 'gitlab-user-id',
              username: 'gitlabuser',
              provider: 'GITLAB',
              role: 'USER',
            }
          }
        })
      });
    });
  }

  /**
   * Mock 获取用户信息
   */
  async mockGetUserInfo(user?: { id?: string; username?: string; role?: string }) {
    await this.page.route('**/auth/me', async (route: Route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          message: 'success',
          data: {
            id: user?.id || 'test-user-id',
            username: user?.username || 'testuser',
            role: user?.role || 'USER',
            createdAt: new Date().toISOString(),
          }
        })
      });
    });
  }

  /**
   * 设置认证状态
   */
  async setAuthState(token: string, user: any) {
    await this.page.context().addCookies([{
      name: 'auth_token',
      value: token,
      domain: 'localhost',
      path: '/',
    }]);

    await this.page.goto('/');
    await this.page.evaluate((authData) => {
      localStorage.setItem('access_token', authData.token);
      localStorage.setItem('refresh_token', 'test-refresh-token');
      localStorage.setItem('token_expiry', String(Date.now() + 3600000));
      localStorage.setItem('user_info', JSON.stringify(authData.user));
    }, { token, user });
  }

  /**
   * 清除认证状态
   */
  async clearAuthState() {
    await this.page.context().clearCookies();
    // 只有在页面已加载时才清除 localStorage
    const url = this.page.url();
    if (url && url !== 'about:blank') {
      await this.page.evaluate(() => {
        localStorage.clear();
        sessionStorage.clear();
      }).catch(() => {
        // 页面可能未加载，忽略错误
      });
    }
  }
}
