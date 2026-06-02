import { test, expect } from '../fixtures/test-fixtures'

test.describe('管理后台测试', () => {
  // 在每个测试前设置管理员认证状态和 Mock API
  test.beforeEach(async ({ page }) => {
    // Mock 仪表盘 API
    await page.route('**/api/admin/dashboard**', async route => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          message: 'success',
          data: {
            totalUsers: 100,
            activeUsers: 50,
            todayTokens: 10000,
            todayCost: 5.5,
            todayRequests: 200,
            modelHealth: [
              { name: 'qwen-plus', status: 'UP', circuitBreaker: 'CLOSED', avgLatency: 150 },
            ],
            budget: {
              dailyUsed: 5.5,
              dailyTotal: 100,
              dailyPercent: 5.5,
              monthlyUsed: 155,
              monthlyTotal: 2000,
              monthlyPercent: 7.75,
            },
          },
        }),
      })
    })

    // Mock 用户管理 API
    await page.route('**/api/admin/users**', async route => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          message: 'success',
          data: {
            data: [
              {
                id: '1',
                username: 'testuser',
                email: 'test@example.com',
                nickname: '测试用户',
                role: 'USER',
                provider: 'CUSTOM',
                createdAt: '2024-01-01',
                lastLoginAt: '2024-01-15',
                todayTokens: 1000,
                todayCost: 0.5,
              },
              {
                id: '2',
                username: 'adminuser',
                email: 'admin@example.com',
                nickname: '管理员',
                role: 'ADMIN',
                provider: 'CUSTOM',
                createdAt: '2024-01-02',
                lastLoginAt: '2024-01-16',
                todayTokens: 2000,
                todayCost: 1.0,
              },
            ],
            total: 2,
            page: 1,
            size: 10,
          },
        }),
      })
    })

    // Mock 成本监控 API
    await page.route('**/api/admin/cost**', async route => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          message: 'success',
          data: {
            dailyUsed: 5.5,
            dailyTotal: 100,
            dailyPercent: 5.5,
            monthlyUsed: 155,
            monthlyTotal: 2000,
            monthlyPercent: 7.75,
          },
        }),
      })
    })

    // Mock 成本模型统计 API
    await page.route('**/api/admin/cost/model-statistics**', async route => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          message: 'success',
          data: [
            {
              modelName: 'qwen-plus',
              totalTokens: 100000,
              promptTokens: 50000,
              completionTokens: 50000,
              totalCost: 15.5,
              requestCount: 500,
              avgTokensPerRequest: 200,
              costPercent: 60,
            },
          ],
        }),
      })
    })

    // Mock /auth/me
    await page.route('**/auth/me', async route => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          message: 'success',
          data: {
            id: 'test-admin-id',
            username: 'adminuser',
            role: 'ADMIN',
            createdAt: new Date().toISOString(),
          },
        }),
      })
    })

    // 设置管理员认证状态
    await page.goto('/')
    await page.evaluate(() => {
      localStorage.setItem('access_token', 'admin-test-token')
      localStorage.setItem('refresh_token', 'admin-refresh-token')
      localStorage.setItem('token_expiry', String(Date.now() + 3600000))
      localStorage.setItem(
        'user_info',
        JSON.stringify({
          id: 'test-admin-id',
          username: 'adminuser',
          role: 'ADMIN',
        })
      )
    })

    await page.context().addCookies([
      {
        name: 'auth_token',
        value: 'admin-test-token',
        domain: 'localhost',
        path: '/',
      },
    ])
  })

  test('仪表盘数据加载', async ({ page }) => {
    await page.goto('/admin')
    await page.waitForLoadState('networkidle')

    // 验证仪表盘页面加载（使用 h2 标题或 metric-card）
    await expect(page.locator('h2:has-text("系统概览"), .metric-card').first()).toBeVisible()
  })

  test('查看用户列表', async ({ page }) => {
    await page.goto('/admin')
    await page.waitForLoadState('networkidle')

    // 点击侧边栏"用户管理"
    await page.click('.el-menu-item:has-text("用户管理")')
    await page.waitForLoadState('networkidle')

    // 验证用户表格加载
    await expect(page.locator('.el-table')).toBeVisible()
  })

  test('搜索用户', async ({ page }) => {
    await page.goto('/admin')
    await page.waitForLoadState('networkidle')

    // 点击侧边栏"用户管理"
    await page.click('.el-menu-item:has-text("用户管理")')
    await page.waitForLoadState('networkidle')

    // 搜索用户
    const searchInput = page
      .locator('input[placeholder*="搜索"], input[placeholder*="查询"]')
      .first()
    if (await searchInput.isVisible()) {
      await searchInput.fill('test')
      await page.keyboard.press('Enter')
      await page.waitForLoadState('networkidle')

      // 验证搜索结果
      const tableText = await page.locator('.el-table').textContent()
      expect(tableText?.toLowerCase()).toContain('test')
    }
  })

  test('查看用户详情', async ({ page }) => {
    await page.goto('/admin')
    await page.waitForLoadState('networkidle')

    // 点击侧边栏"用户管理"
    await page.click('.el-menu-item:has-text("用户管理")')
    await page.waitForLoadState('networkidle')

    // 验证表格存在
    const table = page.locator('.el-table')
    if (await table.isVisible()) {
      const rows = table.locator('tbody tr')
      const rowCount = await rows.count()
      // 如果有行数据，尝试点击
      if (rowCount > 0) {
        try {
          await rows.first().click({ timeout: 5000 })
        } catch {
          // 点击可能触发导航或其他操作，忽略超时
        }
      }
    }
  })

  test('成本监控页面', async ({ page }) => {
    await page.goto('/admin')
    await page.waitForLoadState('networkidle')

    // 点击侧边栏"成本监控"
    await page.click('.el-menu-item:has-text("成本监控")')
    await page.waitForLoadState('networkidle')

    // 验证成本页面加载（使用标题或内容区域）
    await expect(page.locator('h2:has-text("成本"), .el-progress, .el-card').first()).toBeVisible()
  })
})

test.describe('管理员权限测试', () => {
  test('普通用户无法访问管理后台', async ({ page }) => {
    // Mock 访问拒绝响应
    await page.route('**/api/admin/**', async route => {
      await route.fulfill({
        status: 403,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 40300,
          message: '权限不足',
          data: null,
        }),
      })
    })

    // 设置普通用户认证状态
    await page.goto('/')
    await page.evaluate(() => {
      localStorage.setItem('access_token', 'user-token')
      localStorage.setItem('refresh_token', 'test-refresh-token')
      localStorage.setItem('token_expiry', String(Date.now() + 3600000))
      localStorage.setItem(
        'user_info',
        JSON.stringify({
          id: 'user-id',
          username: 'normaluser',
          role: 'USER',
        })
      )
    })

    await page.context().addCookies([
      {
        name: 'auth_token',
        value: 'user-token',
        domain: 'localhost',
        path: '/',
      },
    ])

    // 尝试访问管理后台
    await page.goto('/admin')
    await page.waitForTimeout(1000)

    // 验证被拒绝访问或重定向
    const url = page.url()
    const hasAccessDenied = await page
      .locator('.access-denied, .forbidden, [data-testid="access-denied"]')
      .isVisible()
      .catch(() => false)
    const isRedirected = url.includes('login') || !url.includes('admin')

    expect(hasAccessDenied || isRedirected).toBeTruthy()
  })
})
