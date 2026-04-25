# Agent 未实现功能实现计划

> 基于 `docs/multi-agent/` 技术方案文档审查结果
> 日期：2026-04-25

---

## 1. 实现概览

| 阶段 | 内容 | 预估工时 | 优先级 |
|------|------|----------|--------|
| **Phase 1：安全加固** | AgentSecurityConfig + 安全测试 | 2d | P0 |
| **Phase 2：监控完善** | Grafana 仪表盘 + 告警规则 | 1.5d | P1 |
| **Phase 3：前端完善** | 审计日志页面 + 可靠性监控 | 2d | P1 |
| **Phase 4：测试补充** | Agent 单元测试 + E2E 测试 | 2d | P2 |
| **Phase 5：文档完善** | 开发指南 + 生产级落地指南 | 1d | P2 |

**总预估：8.5 人天**

---

## 2. Phase 1：安全加固（第 1-2 天）

### 2.1 AgentSecurityConfig（0.5d）

**目标**：为 Agent 路径配置独立的 Spring Security 安全链

**文件位置**：`src/main/java/com/jonychen/agent/security/AgentSecurityConfig.java`

**实现要点**：
```java
@Configuration
@EnableWebSecurity
@Order(1)  // 优先于主 SecurityConfig
public class AgentSecurityConfig {
    
    @Bean
    public SecurityFilterChain agentSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/agent/**")
            .authorizeHttpRequests(auth -> auth
                // Agent 列表接口所有认证用户可访问
                .requestMatchers("/api/agent/list").authenticated()
                .requestMatchers("/api/agent/history").authenticated()
                // Agent 执行需要认证
                .requestMatchers("/api/agent/execute").authenticated()
                .requestMatchers("/api/agent/confirm").authenticated()
                .requestMatchers("/api/agent/cancel/**").authenticated()
                // Agent 管理接口需要 ADMIN
                .requestMatchers("/api/admin/agent/**").hasRole("ADMIN")
            )
            .addFilterBefore(new JwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
}
```

**验收标准**：
- [ ] 未认证用户访问 `/api/agent/execute` 返回 401
- [ ] 普通用户访问 `/api/admin/agent/**` 返回 403
- [ ] ADMIN 用户可正常访问所有 Agent 接口

### 2.2 安全测试（1d）

**目标**：验证 Agent 系统的安全边界

**测试文件位置**：`src/test/java/com/jonychen/agent/security/`

#### 2.2.1 SQL 注入测试（0.3d）

```java
@SpringBootTest
class AgentSqlInjectionTest {
    
    @Test
    void testSqlInjectionThroughDataAgent() {
        // 测试通过 DataAgent 执行恶意 SQL
        String maliciousSql = "SELECT * FROM users; DROP TABLE users;";
        // 应该被 DatabaseTools 的 AST 校验拦截
    }
    
    @Test
    void testSqlInjectionViaSubquery() {
        // 测试子查询绕过白名单
        String sql = "SELECT * FROM (SELECT * FROM sensitive_table) AS t";
        // 应该被子查询白名单校验拦截
    }
}
```

#### 2.2.2 越权测试（0.3d）

```java
@SpringBootTest
class AgentAuthorizationTest {
    
    @Test
    @WithMockUser(roles = "USER")
    void testUserCannotExecuteCriticalTool() {
        // 普通用户不能执行 toggle_model_enabled
    }
    
    @Test
    @WithMockUser(roles = "USER")
    void testUserCannotAccessAdminAgent() {
        // 普通用户不能调用 OpsAgent
    }
}
```

#### 2.2.3 命令注入测试（0.4d）

```java
@SpringBootTest
class AgentCommandInjectionTest {
    
    @Test
    void testCommandInjectionThroughTestRunner() {
        // 测试通过 TestRunnerTools 执行恶意命令
        String maliciousClass = "com.jonychen.Test;rm -rf /";
        // 应该被包名校验拦截
    }
    
    @Test
    void testPathTraversalInSourceCodeTools() {
        // 测试路径遍历攻击
        String path = "../../../etc/passwd";
        // 应该被 ProjectPathResolver 拦截
    }
}
```

**验收标准**：
- [ ] 所有 SQL 注入测试通过（恶意 SQL 被拦截）
- [ ] 所有越权测试通过（权限校验生效）
- [ ] 所有命令注入测试通过（参数校验生效）

### 2.3 Redis ConfirmationStore 验证（0.5d）

**目标**：确认为认存储使用 Redis 实现

**检查内容**：
1. `RedisConfirmationStore` 是否为默认 Bean
2. Redis 连接配置是否正确
3. TTL 是否正确设置（默认 5 分钟）

**实现**：
```java
@Configuration
@ConditionalOnProperty(name = "spring.data.redis.host")
class RedisConfirmationConfig {
    
    @Bean
    @Primary
    public ConfirmationStore redisConfirmationStore(RedisTemplate<String, Object> redisTemplate) {
        return new RedisConfirmationStore(redisTemplate);
    }
    
    @Bean
    @ConditionalOnMissingBean(ConfirmationStore.class)
    public ConfirmationStore inMemoryConfirmationStore() {
        return new InMemoryConfirmationStore();
    }
}
```

---

## 3. Phase 2：监控完善（第 3-4 天）

### 3.1 Grafana Agent 执行仪表盘（1d）

**目标**：创建专业的 Agent 监控仪表盘

**文件位置**：`docs/grafana/agent-dashboard.json`

**仪表盘面板**：

| 面板 | 指标 | 说明 |
|------|------|------|
| 执行概览 | `agent_executions_total` | 按状态分组的执行计数 |
| 活跃执行数 | `agent_active_executions` | 当前执行中的 Agent 数量 |
| 执行时长分布 | `agent_execution_duration` | P50/P90/P99 延迟 |
| 工具调用统计 | `agent_tool_calls_total` | 按工具名称和状态分组 |
| 路由决策分布 | `agent_routing_targets` | 各 Agent 被路由的次数 |
| 路由置信度分布 | `agent_routing_confidence` | 置信度分布直方图 |
| 委托调用统计 | `agent_delegations_total` | Agent 间委托调用次数 |
| 限流触发统计 | `agent_rate_limit_hits_total` | 各类型限流触发次数 |
| Token 消耗 | `agent_token_usage_total` | 按模型分组的 Token 消耗 |
| 错误统计 | `agent_errors_total` | 按错误类型分组 |

**实现步骤**：
1. 创建 Dashboard JSON 文件
2. 配置 Prometheus 数据源
3. 添加变量：`agent_name`、`status`、`time_range`
4. 导入 Grafana（通过文件或 API）

### 3.2 Agent 执行异常告警（0.5d）

**目标**：配置关键指标告警规则

**文件位置**：`docs/prometheus/agent-alerts.yml`

**告警规则**：
```yaml
groups:
  - name: agent_alerts
    rules:
      # 执行失败率过高
      - alert: AgentHighFailureRate
        expr: |
          rate(agent_executions_total{status="failure"}[5m]) 
          / rate(agent_executions_total[5m]) > 0.1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Agent 执行失败率超过 10%"
          description: "Agent {{ $labels.agent }} 在过去 5 分钟内失败率 {{ $value | humanizePercentage }}"
      
      # 执行超时过多
      - alert: AgentHighTimeoutRate
        expr: rate(agent_errors_total{type="TIMEOUT"}[5m]) > 0.05
        for: 3m
        labels:
          severity: warning
        annotations:
          summary: "Agent 执行超时率过高"
      
      # 活跃执行数过多
      - alert: AgentTooManyActiveExecutions
        expr: agent_active_executions > 15
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "活跃 Agent 执行数过多"
      
      # 限流触发过多
      - alert: AgentHighRateLimitHits
        expr: rate(agent_rate_limit_hits_total[5m]) > 1
        for: 5m
        labels:
          severity: info
        annotations:
          summary: "Agent 限流触发频繁，请考虑调整配额"
      
      # Token 消耗异常
      - alert: AgentHighTokenUsage
        expr: rate(agent_token_usage_total{type="total"}[1h]) > 100000
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "Agent Token 消耗异常高"
```

**验收标准**：
- [ ] Grafana 仪表盘可正常展示 Agent 指标
- [ ] 告警规则已配置并生效
- [ ] 测试告警触发正常

---

## 4. Phase 3：前端完善（第 5-6 天）

### 4.1 Agent 审计日志前端页面（1d）

**目标**：实现管理后台的 Agent 执行历史查询页面

**文件位置**：`frontend/src/views/admin/AgentHistoryView.vue`

**功能设计**：
1. **搜索过滤**：
   - 用户 ID 过滤
   - Agent 名称过滤
   - 事件类型过滤
   - 时间范围过滤
2. **列表展示**：
   - 分页表格
   - 显示：traceId、用户、Agent、状态、耗时、时间
3. **详情查看**：
   - 点击行展开详情
   - 显示执行步骤列表
   - JSON 格式化展示参数和结果

**API 集成**：
- `GET /api/agent/history` - 分页查询
- `GET /api/agent/history/{traceId}` - 获取详情

**组件结构**：
```vue
<template>
  <div class="agent-history-view">
    <!-- 搜索过滤区 -->
    <div class="filter-section">
      <el-input v-model="filters.userId" placeholder="用户 ID" />
      <el-select v-model="filters.agentName" placeholder="Agent">
        <el-option v-for="a in agents" :key="a" :label="a" :value="a" />
      </el-select>
      <el-date-picker v-model="filters.timeRange" type="datetimerange" />
      <el-button @click="loadHistory">查询</el-button>
    </div>
    
    <!-- 历史列表 -->
    <el-table :data="historyList" @row-click="showDetail">
      <el-table-column prop="traceId" label="Trace ID" />
      <el-table-column prop="userId" label="用户" />
      <el-table-column prop="agentName" label="Agent" />
      <el-table-column prop="status" label="状态">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="durationMs" label="耗时">
        <template #default="{ row }">{{ formatDuration(row.durationMs) }}</template>
      </el-table-column>
      <el-table-column prop="startTime" label="时间" />
    </el-table>
    
    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="执行详情">
      <ExecutionDetailPanel :detail="selectedDetail" />
    </el-drawer>
  </div>
</template>
```

### 4.2 前端可靠性监控（1d）

**目标**：采集并上报前端可靠性指标

**文件位置**：`frontend/src/utils/agentMetrics.ts`

**监控指标**：
```typescript
// 可靠性指标接口
interface AgentReliabilityMetrics {
  // SSE 重连次数
  sseReconnectCount: number
  // SSE 总连接次数
  sseTotalConnections: number
  // 事件丢失数（sequenceNumber 检测到的间隙）
  eventGapsDetected: number
  // 事件重复数
  eventDuplicatesDetected: number
  // 状态恢复成功数
  stateRestoreSuccess: number
  // 状态恢复失败数
  stateRestoreFailure: number
  // 平均事件处理延迟（ms）
  avgEventProcessingTime: number
}

class AgentMetricsCollector {
  private metrics: AgentReliabilityMetrics = { ... }
  
  // 记录 SSE 重连
  recordReconnect() {
    this.metrics.sseReconnectCount++
    this.report()
  }
  
  // 记录事件间隙
  recordEventGap(expectedSeq: number, actualSeq: number) {
    this.metrics.eventGapsDetected++
    console.warn(`[AgentMetrics] Event gap: expected ${expectedSeq}, got ${actualSeq}`)
  }
  
  // 记录状态恢复
  recordStateRestore(success: boolean) {
    if (success) {
      this.metrics.stateRestoreSuccess++
    } else {
      this.metrics.stateRestoreFailure++
    }
    this.report()
  }
  
  // 上报到后端
  private report() {
    navigator.sendBeacon('/api/agent/metrics', JSON.stringify(this.metrics))
  }
}
```

**集成到 agent.ts**：
- 在 `executeAgent()` 中注入 metrics collector
- 在 SSE 事件处理中记录指标
- 页面卸载时批量上报

**后端接收接口**：
```java
@PostMapping("/metrics")
public void receiveMetrics(@RequestBody AgentReliabilityMetrics metrics) {
    // 记录到 Prometheus
    meterRegistry.counter("agent_frontend_reconnects").increment(metrics.sseReconnectCount);
    meterRegistry.counter("agent_frontend_event_gaps").increment(metrics.eventGapsDetected);
    // ...
}
```

**验收标准**：
- [ ] 前端可靠性指标可正常采集
- [ ] 指标可上报到后端并展示在 Grafana
- [ ] 页面刷新后状态恢复率可统计

---

## 5. Phase 4：测试补充（第 7-8 天）

### 5.1 Agent 单元测试（1d）

**目标**：补充各 Agent 的单元测试

**待补充测试**：
| 测试文件 | 覆盖内容 | 预估 |
|---------|---------|------|
| `RouterAgentTest.java` | 路由决策、置信度计算 | 0.2d |
| `OpsAgentTest.java` | 模型状态工具集成 | 0.2d |
| `DataAgentTest.java` | 数据库查询、SQL 校验 | 0.2d |
| `PromptAgentTest.java` | Prompt 工具集成 | 0.2d |
| `TestAgentTest.java` | 测试生成工具集成 | 0.2d |

**测试框架**：
- JUnit 5 + Mockito
- 使用 `@MockBean` 模拟外部依赖
- 测试正常路径和边界条件

### 5.2 Agent E2E 测试（1d）

**目标**：验证前端 Agent 执行面板的完整功能

**文件位置**：`frontend/tests/e2e/specs/agent-execution.spec.ts`

**测试用例**：
```typescript
describe('Agent Execution Panel', () => {
  test('should execute agent and show steps', async ({ page }) => {
    await page.goto('/agent')
    
    // 输入指令
    await page.fill('[data-testid="agent-input"]', '检查模型健康状态')
    await page.click('[data-testid="execute-button"]')
    
    // 等待步骤卡片出现
    await expect(page.locator('.step-card').first()).toBeVisible({ timeout: 10000 })
    
    // 验证步骤状态
    const statusBadge = page.locator('.step-card .status-badge')
    await expect(statusBadge).toContainText(/成功|运行中/)
  })
  
  test('should show confirmation dialog for sensitive operation', async ({ page }) => {
    // 选择 OpsAgent
    await page.selectOption('[data-testid="agent-selector"]', 'ops')
    
    // 输入敏感操作指令
    await page.fill('[data-testid="agent-input"]', '调整 deepseek 模型权重为 5')
    await page.click('[data-testid="execute-button"]')
    
    // 等待确认对话框出现
    const confirmDialog = page.locator('.confirmation-dialog')
    await expect(confirmDialog).toBeVisible({ timeout: 30000 })
    
    // 验证风险等级显示
    await expect(confirmDialog.locator('.risk-level')).toContainText('高风险')
    
    // 点击确认
    await confirmDialog.locator('button:has-text("确认执行")').click()
    
    // 验证执行继续
    await expect(page.locator('.step-card.running')).toBeVisible()
  })
  
  test('should restore state after page refresh', async ({ page }) => {
    // 开始执行
    await page.goto('/agent')
    await page.fill('[data-testid="agent-input"]', '获取模型状态')
    await page.click('[data-testid="execute-button"]')
    
    // 等待执行开始
    await page.waitForSelector('.step-card')
    
    // 刷新页面
    await page.reload()
    
    // 验证执行状态恢复
    const restoredCard = page.locator('.step-card').first()
    await expect(restoredCard).toBeVisible()
  })
})
```

**验收标准**：
- [ ] 所有 Agent 单元测试通过
- [ ] E2E 测试覆盖核心功能
- [ ] CI 中集成测试运行

---

## 6. Phase 5：文档完善（第 9 天）

### 6.1 Agent 开发指南（0.5d）

**文件位置**：`docs/agent-development-guide.md`

**内容大纲**：
```markdown
# Agent 开发指南

## 1. 快速开始
### 1.1 创建新 Agent
### 1.2 实现接口方法
### 1.3 注册 Agent Bean

## 2. Agent 核心概念
### 2.1 Agent 接口
### 2.2 AbstractAgent 模板方法
### 2.3 AgentContext 执行上下文

## 3. 工具开发
### 3.1 @AgentTool 注解
### 3.2 参数校验
### 3.3 确认机制

## 4. 最佳实践
### 4.1 System Prompt 编写
### 4.2 错误处理
### 4.3 测试策略

## 5. 示例：从零实现一个 Agent
```

### 6.2 前端生产级落地指南（0.5d）

**文件位置**：`docs/frontend-production-guide.md`

**内容大纲**：
```markdown
# 前端 Agent 生产级落地指南

## 1. SSE 客户端可靠性
### 1.1 自动重连机制
### 1.2 事件序号检测
### 1.3 心跳超时处理

## 2. 状态持久化
### 2.1 sessionStorage 存储
### 2.2 状态恢复策略
### 2.3 过期清理

## 3. 性能优化
### 3.1 批量 UI 更新
### 3.2 虚拟滚动（大量步骤）
### 3.3 懒加载组件

## 4. 移动端适配
### 4.1 响应式布局
### 4.2 底部固定确认栏
### 4.3 触控优化

## 5. 安全考虑
### 5.1 XSS 防护
### 5.2 敏感数据脱敏
```

---

## 7. 任务跟踪

完成以下任务跟踪表：

| 阶段 | 任务 | 状态 | 负责人 | 完成日期 |
|------|------|------|--------|----------|
| P1 | AgentSecurityConfig | ⬜ 待开始 | - | - |
| P1 | SQL 注入测试 | ⬜ 待开始 | - | - |
| P1 | 越权测试 | ⬜ 待开始 | - | - |
| P1 | 命令注入测试 | ⬜ 待开始 | - | - |
| P1 | Redis ConfirmationStore 验证 | ⬜ 待开始 | - | - |
| P2 | Grafana 仪表盘 | ⬜ 待开始 | - | - |
| P2 | 告警规则配置 | ⬜ 待开始 | - | - |
| P3 | 审计日志前端页面 | ⬜ 待开始 | - | - |
| P3 | 前端可靠性监控 | ⬜ 待开始 | - | - |
| P4 | Agent 单元测试 | ⬜ 待开始 | - | - |
| P4 | Agent E2E 测试 | ⬜ 待开始 | - | - |
| P5 | Agent 开发指南 | ⬜ 待开始 | - | - |
| P5 | 前端生产级指南 | ⬜ 待开始 | - | - |

---

## 8. 验收标准

### 整体验收

- [ ] 所有安全测试通过
- [ ] Grafana 仪表盘可正常展示
- [ ] 告警规则触发正常
- [ ] 前端审计日志页面可用
- [ ] 可靠性指标可采集上报
- [ ] 单元测试覆盖率 > 70%
- [ ] E2E 测试全部通过
- [ ] 文档完整可读
