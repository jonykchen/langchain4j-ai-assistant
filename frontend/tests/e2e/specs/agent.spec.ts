import { test, expect } from '../fixtures/test-fixtures'

/**
 * Agent 执行功能 E2E 测试
 *
 * <p>测试场景：
 * <ul>
 *   <li>Agent 选择和执行</li>
 *   <li>执行步骤展示</li>
 *   <li>敏感操作确认</li>
 *   <li>执行统计显示</li>
 *   <li>错误处理和重连</li>
 * </ul>
 */
test.describe('Agent 执行功能', () => {
  test.describe('Agent 选择', () => {
    test.beforeEach(async ({ authenticatedPage }) => {
      // 导航到 Agent 执行页面
      await authenticatedPage.goto('/agent')
    })

    test('应显示可用的 Agent 列表', async ({ authenticatedPage }) => {
      // Mock Agent 列表 API
      await authenticatedPage.route('**/api/agent/list', async route => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([
            {
              name: 'router',
              agentType: 'ROUTER',
              displayName: 'RouterAgent',
              description: '智能路由，自动选择合适的 Agent',
              version: '1.0.0',
              capabilities: ['routing', 'classification'],
              requiredPermissions: [],
              maxIterations: 10,
              timeout: '60s',
              supportsStreaming: true,
            },
            {
              name: 'data',
              agentType: 'DATA',
              displayName: 'DataAgent',
              description: '数据查询和统计 Agent',
              version: '1.0.0',
              capabilities: ['query', 'statistics'],
              requiredPermissions: ['data:read'],
              maxIterations: 5,
              timeout: '30s',
              supportsStreaming: true,
            },
          ]),
        })
      })

      // 等待 Agent 选择器加载
      await authenticatedPage.locator('[data-testid="agent-selector"]').waitFor({ timeout: 10000 })

      // 验证 Agent 选项数量
      const options = await authenticatedPage.locator('[data-testid="agent-option"]').all()
      expect(options.length).toBeGreaterThanOrEqual(1)
    })

    test('选择 Agent 后应显示描述信息', async ({ authenticatedPage }) => {
      // Mock Agent 列表
      await authenticatedPage.route('**/api/agent/list', async route => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([
            {
              name: 'router',
              agentType: 'ROUTER',
              displayName: 'RouterAgent',
              description: '智能路由，自动选择合适的 Agent',
              version: '1.0.0',
              capabilities: ['routing', 'classification'],
              requiredPermissions: [],
              maxIterations: 10,
              timeout: '60s',
              supportsStreaming: true,
            },
          ]),
        })
      })

      await authenticatedPage.locator('[data-testid="agent-selector"]').waitFor({ timeout: 10000 })

      // 选择 Router Agent
      await authenticatedPage.locator('[data-testid="agent-selector"]').click()
      await authenticatedPage.locator('[data-testid="agent-option"]').first().click()

      // 验证描述信息显示
      await expect(authenticatedPage.locator('[data-testid="agent-description"]')).toContainText(
        '智能路由'
      )
    })
  })

  test.describe('Agent 执行', () => {
    test.beforeEach(async ({ authenticatedPage }) => {
      await authenticatedPage.goto('/agent')

      // Mock Agent 列表
      await authenticatedPage.route('**/api/agent/list', async route => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([
            {
              name: 'router',
              agentType: 'ROUTER',
              displayName: 'RouterAgent',
              description: '智能路由 Agent',
              version: '1.0.0',
              capabilities: ['routing'],
              requiredPermissions: [],
              maxIterations: 10,
              timeout: '60s',
              supportsStreaming: true,
            },
          ]),
        })
      })
    })

    test('执行 Agent 并显示步骤', async ({ authenticatedPage }) => {
      // Mock SSE 执行响应
      await authenticatedPage.route('**/api/agent/execute', async route => {
        const events = [
          {
            eventType: 'step_start',
            stepIndex: 0,
            type: 'THOUGHT',
            agentName: 'router',
            sequenceNumber: 1,
          },
          { eventType: 'thought', stepIndex: 0, content: '分析用户输入...', sequenceNumber: 2 },
          {
            eventType: 'step_end',
            stepIndex: 0,
            success: true,
            summary: '已分析',
            sequenceNumber: 3,
          },
          {
            eventType: 'step_start',
            stepIndex: 1,
            type: 'TOOL_CALL',
            agentName: 'router',
            sequenceNumber: 4,
          },
          { eventType: 'tool_call', stepIndex: 1, toolName: 'classify_intent', sequenceNumber: 5 },
          {
            eventType: 'tool_result',
            stepIndex: 1,
            toolName: 'classify_intent',
            success: true,
            sequenceNumber: 6,
          },
          {
            eventType: 'step_end',
            stepIndex: 1,
            success: true,
            summary: '意图分类完成',
            sequenceNumber: 7,
          },
          {
            eventType: 'agent_done',
            agentName: 'router',
            output: '已选择 DataAgent',
            totalSteps: 2,
            sequenceNumber: 8,
          },
        ]

        const body =
          events.map(e => `event:${e.eventType}\ndata:${JSON.stringify(e)}\n\n`).join('') +
          'event:done\ndata:[DONE]\n\n'

        await route.fulfill({
          status: 200,
          contentType: 'text/event-stream;charset=UTF-8',
          body,
        })
      })

      // 等待 Agent 选择器加载
      await authenticatedPage.locator('[data-testid="agent-selector"]').waitFor({ timeout: 10000 })

      // 输入问题
      await authenticatedPage.locator('[data-testid="agent-input"]').fill('查询上周数据')
      await authenticatedPage.locator('[data-testid="execute-button"]').click()

      // 等待执行步骤显示
      await authenticatedPage
        .locator('[data-testid="execution-step"]')
        .first()
        .waitFor({ timeout: 30000 })

      // 验证步骤数量
      const steps = await authenticatedPage.locator('[data-testid="execution-step"]').all()
      expect(steps.length).toBeGreaterThanOrEqual(1)
    })

    test('执行完成后应显示统计信息', async ({ authenticatedPage }) => {
      // Mock SSE 执行响应（简化版）
      await authenticatedPage.route('**/api/agent/execute', async route => {
        const events = [
          {
            eventType: 'step_start',
            stepIndex: 0,
            type: 'THOUGHT',
            agentName: 'router',
            sequenceNumber: 1,
          },
          { eventType: 'thought', stepIndex: 0, content: '思考...', sequenceNumber: 2 },
          {
            eventType: 'step_end',
            stepIndex: 0,
            success: true,
            summary: '完成',
            sequenceNumber: 3,
          },
          {
            eventType: 'agent_done',
            agentName: 'router',
            output: '执行完成',
            totalSteps: 1,
            tokenUsage: { promptTokens: 50, completionTokens: 100, totalTokens: 150 },
            durationMs: 2000,
            sequenceNumber: 4,
          },
        ]

        const body =
          events.map(e => `event:${e.eventType}\ndata:${JSON.stringify(e)}\n\n`).join('') +
          'event:done\ndata:[DONE]\n\n'

        await route.fulfill({
          status: 200,
          contentType: 'text/event-stream;charset=UTF-8',
          body,
        })
      })

      await authenticatedPage.locator('[data-testid="agent-selector"]').waitFor({ timeout: 10000 })

      await authenticatedPage.locator('[data-testid="agent-input"]').fill('测试')
      await authenticatedPage.locator('[data-testid="execute-button"]').click()

      // 等待执行完成
      await authenticatedPage.locator('[data-testid="execution-stats"]').waitFor({ timeout: 30000 })

      // 验证统计信息显示
      await expect(authenticatedPage.locator('[data-testid="token-count"]')).toContainText('150')
    })

    test('执行错误应显示错误信息', async ({ authenticatedPage }) => {
      // Mock SSE 错误响应
      await authenticatedPage.route('**/api/agent/execute', async route => {
        const events = [
          {
            eventType: 'step_start',
            stepIndex: 0,
            type: 'THOUGHT',
            agentName: 'router',
            sequenceNumber: 1,
          },
          {
            eventType: 'agent_error',
            errorCode: 'EXECUTION_FAILED',
            message: '执行失败，请稍后重试',
            recoverable: true,
            sequenceNumber: 2,
          },
        ]

        const body =
          events.map(e => `event:${e.eventType}\ndata:${JSON.stringify(e)}\n\n`).join('') +
          'event:done\ndata:[DONE]\n\n'

        await route.fulfill({
          status: 200,
          contentType: 'text/event-stream;charset=UTF-8',
          body,
        })
      })

      await authenticatedPage.locator('[data-testid="agent-selector"]').waitFor({ timeout: 10000 })

      await authenticatedPage.locator('[data-testid="agent-input"]').fill('触发错误')
      await authenticatedPage.locator('[data-testid="execute-button"]').click()

      // 等待错误显示
      await authenticatedPage.locator('[data-testid="error-message"]').waitFor({ timeout: 30000 })

      // 验证错误信息
      await expect(authenticatedPage.locator('[data-testid="error-message"]')).toContainText(
        '执行失败'
      )
    })
  })

  test.describe('敏感操作确认', () => {
    test('敏感操作应显示确认对话框', async ({ authenticatedPage }) => {
      await authenticatedPage.goto('/agent')

      // Mock Agent 列表
      await authenticatedPage.route('**/api/agent/list', async route => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([
            {
              name: 'ops',
              agentType: 'OPS',
              displayName: 'OpsAgent',
              description: '运维操作 Agent',
              version: '1.0.0',
              capabilities: ['deployment', 'restart'],
              requiredPermissions: ['ADMIN'],
              maxIterations: 5,
              timeout: '60s',
              supportsStreaming: true,
            },
          ]),
        })
      })

      // Mock 需要确认的 SSE 响应
      await authenticatedPage.route('**/api/agent/execute', async route => {
        const events = [
          {
            eventType: 'step_start',
            stepIndex: 0,
            type: 'TOOL_CALL',
            agentName: 'ops',
            sequenceNumber: 1,
          },
          {
            eventType: 'confirmation_required',
            stepIndex: 0,
            confirmationId: 'conf-123',
            operation: 'restart_service',
            description: '即将重启生产服务，请确认',
            riskLevel: 'HIGH',
            sequenceNumber: 2,
          },
        ]

        const body = events.map(e => `event:${e.eventType}\ndata:${JSON.stringify(e)}\n\n`).join('')

        await route.fulfill({
          status: 200,
          contentType: 'text/event-stream;charset=UTF-8',
          body,
        })
      })

      await authenticatedPage.locator('[data-testid="agent-selector"]').waitFor({ timeout: 10000 })

      await authenticatedPage.locator('[data-testid="agent-input"]').fill('重启服务')
      await authenticatedPage.locator('[data-testid="execute-button"]').click()

      // 等待确认对话框显示
      await authenticatedPage
        .locator('[data-testid="confirmation-dialog"]')
        .waitFor({ timeout: 30000 })

      // 验证对话框内容
      await expect(
        authenticatedPage.locator('[data-testid="confirmation-operation"]')
      ).toContainText('restart_service')
      await expect(
        authenticatedPage.locator('[data-testid="confirmation-risk-level"]')
      ).toContainText('HIGH')
    })

    test('批准确认后继续执行', async ({ authenticatedPage }) => {
      await authenticatedPage.goto('/agent')

      // Mock Agent 列表
      await authenticatedPage.route('**/api/agent/list', async route => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([
            {
              name: 'ops',
              agentType: 'OPS',
              displayName: 'OpsAgent',
              description: '运维操作 Agent',
              version: '1.0.0',
              capabilities: ['deployment'],
              requiredPermissions: ['ADMIN'],
              maxIterations: 5,
              timeout: '60s',
              supportsStreaming: true,
            },
          ]),
        })
      })

      // Mock 确认 API
      await authenticatedPage.route('**/api/agent/confirm', async route => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ success: true, message: '已批准' }),
        })
      })

      // Mock SSE 执行（第一阶段需要确认，第二阶段执行完成）
      let callCount = 0
      await authenticatedPage.route('**/api/agent/execute', async route => {
        callCount++
        if (callCount === 1) {
          // 第一阶段：需要确认
          const events = [
            {
              eventType: 'confirmation_required',
              confirmationId: 'conf-123',
              operation: 'deploy',
              description: '即将部署到生产环境',
              riskLevel: 'CRITICAL',
              sequenceNumber: 1,
            },
          ]
          const body = events
            .map(e => `event:${e.eventType}\ndata:${JSON.stringify(e)}\n\n`)
            .join('')
          await route.fulfill({
            status: 200,
            contentType: 'text/event-stream;charset=UTF-8',
            body,
          })
        } else {
          // 第二阶段：执行完成
          const events = [
            {
              eventType: 'agent_done',
              agentName: 'ops',
              output: '部署成功',
              totalSteps: 2,
              sequenceNumber: 2,
            },
          ]
          const body =
            events.map(e => `event:${e.eventType}\ndata:${JSON.stringify(e)}\n\n`).join('') +
            'event:done\ndata:[DONE]\n\n'
          await route.fulfill({
            status: 200,
            contentType: 'text/event-stream;charset=UTF-8',
            body,
          })
        }
      })

      await authenticatedPage.locator('[data-testid="agent-selector"]').waitFor({ timeout: 10000 })

      await authenticatedPage.locator('[data-testid="agent-input"]').fill('部署到生产')
      await authenticatedPage.locator('[data-testid="execute-button"]').click()

      // 等待确认对话框
      await authenticatedPage
        .locator('[data-testid="confirmation-dialog"]')
        .waitFor({ timeout: 30000 })

      // 点击批准按钮
      await authenticatedPage.locator('[data-testid="approve-button"]').click()

      // 等待执行完成
      await authenticatedPage
        .locator('[data-testid="execution-output"]')
        .waitFor({ timeout: 30000 })

      // 验证输出
      await expect(authenticatedPage.locator('[data-testid="execution-output"]')).toContainText(
        '部署成功'
      )
    })
  })

  test.describe('执行历史', () => {
    test('应显示执行历史列表', async ({ authenticatedPage }) => {
      await authenticatedPage.goto('/agent')

      // Mock 执行历史 API
      await authenticatedPage.route('**/api/agent/history**', async route => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            data: [
              {
                traceId: 'trace-1',
                userId: 'user-1',
                agentName: 'router',
                eventType: 'EXECUTION_END',
                status: 'SUCCESS',
                timestamp: new Date().toISOString(),
                details: { durationMs: 2000 },
              },
              {
                traceId: 'trace-2',
                userId: 'user-1',
                agentName: 'data',
                eventType: 'EXECUTION_END',
                status: 'SUCCESS',
                timestamp: new Date().toISOString(),
                details: { durationMs: 1500 },
              },
            ],
            total: 2,
            page: 0,
            size: 20,
          }),
        })
      })

      // 导航到历史页面（如果有）
      // 验证历史列表显示
      // 这个测试可以根据实际页面结构调整
    })
  })
})
