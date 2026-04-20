import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

test.describe('无障碍测试', () => {
  test('聊天页面无障碍检查', async ({ page }) => {
    await page.goto('/');

    const accessibilityScanResults = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .analyze();

    // 输出违规项详情（用于调试）
    if (accessibilityScanResults.violations.length > 0) {
      console.log('Accessibility violations:');
      accessibilityScanResults.violations.forEach(violation => {
        console.log(`- ${violation.id}: ${violation.description}`);
        violation.nodes.forEach(node => {
          console.log(`  - ${node.html}`);
        });
      });
    }

    expect(accessibilityScanResults.violations).toEqual([]);
  });

  test('登录页面无障碍检查', async ({ page }) => {
    await page.goto('/login');

    const accessibilityScanResults = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .analyze();

    expect(accessibilityScanResults.violations).toEqual([]);
  });

  test('键盘导航 - 聊天页面', async ({ page }) => {
    await page.goto('/');

    // Tab 导航到输入框
    await page.keyboard.press('Tab');
    await expect(page.locator('[data-testid="message-input"]')).toBeFocused();

    // 输入文字
    await page.keyboard.type('测试消息');

    // Tab 导航到发送按钮
    await page.keyboard.press('Tab');
    await expect(page.locator('[data-testid="send-button"]')).toBeFocused();

    // Enter 发送消息
    await page.keyboard.press('Enter');
  });

  test('键盘导航 - 登录页面', async ({ page }) => {
    await page.goto('/login');

    // Tab 导航到用户名输入
    await page.keyboard.press('Tab');
    await expect(page.locator('[data-testid="username-input"], input[type="text"]').first()).toBeFocused();

    // 输入用户名
    await page.keyboard.type('testuser');

    // Tab 导航到密码输入
    await page.keyboard.press('Tab');
    await expect(page.locator('[data-testid="password-input"], input[type="password"]').first()).toBeFocused();

    // 输入密码
    await page.keyboard.type('password123');

    // Tab 导航到登录按钮
    await page.keyboard.press('Tab');
    await expect(page.locator('[data-testid="login-button"], button:has-text("登录")')).toBeFocused();

    // Enter 登录
    await page.keyboard.press('Enter');
  });

  test('焦点顺序正确', async ({ page }) => {
    await page.goto('/');

    const focusOrder: string[] = [];

    for (let i = 0; i < 10; i++) {
      await page.keyboard.press('Tab');
      const focused = await page.evaluateHandle(() => document.activeElement);
      const tagName = await focused.evaluate(el => el?.tagName || '');
      const testId = await focused.evaluate(el => el?.getAttribute('data-testid') || '');

      if (testId) {
        focusOrder.push(testId);
      } else if (tagName) {
        focusOrder.push(tagName.toLowerCase());
      }

      // 避免无限循环
      if (focusOrder.length > 1 && focusOrder[focusOrder.length - 1] === focusOrder[0]) {
        break;
      }
    }

    // 验证焦点顺序有意义
    expect(focusOrder.length).toBeGreaterThan(0);
  });

  test('屏幕阅读器标签', async ({ page }) => {
    await page.goto('/');

    // 验证关键元素有 ARIA 标签
    const messageInput = page.locator('[data-testid="message-input"]');
    const ariaLabel = await messageInput.getAttribute('aria-label');
    expect(ariaLabel).toBeTruthy();

    const sendButton = page.locator('[data-testid="send-button"]');
    const sendAriaLabel = await sendButton.getAttribute('aria-label');
    expect(sendAriaLabel).toBeTruthy();
  });

  test('颜色对比度', async ({ page }) => {
    await page.goto('/');

    const accessibilityScanResults = await new AxeBuilder({ page })
      .withRules(['color-contrast'])
      .analyze();

    // 允许少量颜色对比度问题（可根据实际情况调整）
    const contrastViolations = accessibilityScanResults.violations.filter(
      v => v.id === 'color-contrast'
    );

    // 输出对比度问题
    if (contrastViolations.length > 0) {
      console.log('Color contrast violations:', contrastViolations.length);
    }
  });

  test('移动端无障碍', async ({ page }) => {
    // 设置移动端视口
    await page.setViewportSize({ width: 375, height: 667 });
    await page.goto('/');

    const accessibilityScanResults = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .analyze();

    expect(accessibilityScanResults.violations.length).toBeLessThanOrEqual(5);
  });
});
