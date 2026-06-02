import { defineConfig, devices } from '@playwright/test'

/**
 * Playwright E2E 测试配置
 * 支持多浏览器、移动端测试
 */
export default defineConfig({
  // 测试目录
  testDir: './specs',

  // 并行执行测试
  fullyParallel: true,

  // CI 环境禁止 only 测试
  forbidOnly: !!process.env.CI,

  // CI 环境重试次数
  retries: process.env.CI ? 2 : 0,

  // CI 环境单线程执行
  workers: process.env.CI ? 1 : undefined,

  // 报告配置
  reporter: [
    ['html', { outputFolder: 'playwright-report' }],
    ['junit', { outputFile: 'junit-results.xml' }],
    ['list'],
  ],

  // 全局配置
  use: {
    // 基础 URL
    baseURL: 'http://localhost:5173',

    // 失败时追踪
    trace: 'on-first-retry',

    // 失败时截图
    screenshot: 'only-on-failure',

    // 失败时保留视频
    video: 'retain-on-failure',

    // 浏览器上下文配置
    contextOptions: {
      // 忽略 HTTPS 错误（用于本地开发）
      ignoreHTTPSErrors: true,
    },
  },

  // 测试项目配置
  projects: [
    // 认证设置项目
    {
      name: 'setup',
      testMatch: /.*\.setup\.ts/,
    },

    // Chromium 测试
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
      dependencies: ['setup'],
    },

    // Firefox 测试
    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'] },
      dependencies: ['setup'],
    },

    // WebKit 测试（Safari）
    {
      name: 'webkit',
      use: { ...devices['Desktop Safari'] },
      dependencies: ['setup'],
    },

    // 移动端测试
    {
      name: 'Mobile Chrome',
      use: { ...devices['Pixel 5'] },
      dependencies: ['setup'],
    },

    // 平板测试
    {
      name: 'iPad',
      use: { ...devices['iPad Pro'] },
      dependencies: ['setup'],
    },
  ],

  // 开发服务器配置
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
    timeout: 120000,
  },
})
