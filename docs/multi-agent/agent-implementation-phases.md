# 分阶段实现计划

> 多 Agent 生产级系统技术方案 - 子文档

---

## 1. 总体计划

### 1.1 阶段划分

| 阶段 | 时间 | 内容 | 交付物 |
|------|------|------|--------|
| **P0：基础设施** | 第 1-2 周 | Agent 框架、SSE 协议、工具体系 | 可运行的框架 + 1 个 Agent |
| **P1：核心 Agent** | 第 3-4 周 | OpsAgent、DataAgent、前端面板 | 2 个可用 Agent + 执行面板 |
| **P2：路由与协作** | 第 5-6 周 | RouterAgent、Agent 间委托 | 智能路由 + 多 Agent 协作 |
| **P3：扩展 Agent** | 第 7-8 周 | PromptAgent、TestAgent | 4 个完整 Agent |
| **P4：生产加固** | 第 9-10 周 | 安全、审计、限流、监控、前端可靠性 | 生产级系统 |

### 1.2 里程碑

```
Week 1-2  ─── P0 完成 ─── Agent 框架可运行，OpsAgent 原型可用
Week 3-4  ─── P1 完成 ─── 前端执行面板上线，OpsAgent + DataAgent 可用
Week 5-6  ─── P2 完成 ─── 智能路由替代关键词匹配，Agent 可互相委托
Week 7-8  ─── P3 完成 ─── 四大 Agent 全部可用
Week 9-10 ─── P4 完成 ─── 安全加固、审计日志、监控告警
```

---

## 2. P0：基础设施（第 1-2 周）

### 2.1 目标

- Agent 抽象框架可运行
- SSE 协议扩展完成
- 工具体系统一
- 1 个 Agent（OpsAgent）可执行

### 2.2 任务清单

#### 后端

| # | 任务 | 优先级 | 预估 |
|---|------|--------|------|
| B1 | 创建 `agent/core` 包，定义 `Agent` 接口、`AbstractAgent`、`AgentEvent` | P0 | 1d |
| B2 | 实现 `AgentExecutor`（LangChain4j ChatModel + Tool 集成，**移除正则解析**，**chatHistory 滑动窗口限制 20 条**） | P0 | 1d |
| B3 | 实现 `AgentRegistry`（Agent 注册与发现） | P0 | 0.5d |
| B4 | 重构 `ToolRegistry`，增加 `riskLevel`、`requiresConfirmation`、`allowedRoles` | P0 | 1d |
| B5 | `AgentExecutionController` 提取 `clientIp`/`userAgent` 注入 `AgentRequest`（避免异步审计依赖 `HttpServletRequest`） | P0 | 0.5d |
| B6 | `DataAgent` 的 `allowedTables` 改为通过 `DatabaseTools` 依赖注入获取（消除双重配置） | P0 | 0.5d |
| B7 | **修复 `DefaultToolRegistry` 确认机制 Bug**：确认生命周期统一到 `AgentContext`，`ToolRegistry.execute()` 仅返回 pending 状态 | **P0** | **0.5d** |
| B8 | **修复 `DefaultToolRegistry.checkPermissions` 逻辑反转**：改为 `required.stream().allMatch(r -> userRoles.contains(r))` | **P0** | **0.5d** |
| B9 | 实现 `AgentExecutionController`（SSE 端点 + **心跳机制**，使用 `Flux<ServerSentEvent>` 实现流式响应） | P0 | 1d |
| B10 | 实现 OpsAgent + ModelStateTools（**含确认恢复机制**） | P0 | 2d |
| B11 | 重构 `AgentOrchestrator`，支持 `Flux<AgentEvent>` + **activeExecutions 定时清理** + **心跳线程池共享** + **@PreDestroy 关闭** | P0 | 1d |
| B12 | **实现 `AgentInputValidator` + `AgentOutputFilter`（SQL AST 校验、敏感数据脱敏、子查询递归白名单）** | **P0** | **1d** |
| B13 | **实现 `AgentPermissionService`（基础 RBAC，ADMIN 不豁免 CRITICAL，`valueOf` 防非法参数）** | **P0** | **0.5d** |
| B14 | **LangChain4j ToolCalling + 结构化输出 POC 验证**（验证 `AiMessage.toolExecutionRequests()` 格式和 JSON 解析可靠性） | **P0** | **0.5d** |
| B15 | ~~补充 `TokenUsage` record 定义（promptTokens, completionTokens, totalTokens）~~（已前置完成） | - | - |
| B16 | ~~补充 `AgentMetadata.agentType` 字段~~（已前置完成） | - | - |
| B17 | ~~补充 `AgentDone.agentName` 字段~~（已前置完成） | - | - |
| B18 | 编写单元测试 | P0 | 1d |

#### 前端

| # | 任务 | 优先级 | 预估 |
|---|------|--------|------|
| F1 | 新增 `agent.ts` API 模块（SSE 客户端 + **事件序号检测 + 自动重连 + 心跳超时**） | P0 | 1d |
| F2 | 新增 `agent` Pinia Store（**状态持久化 + 批量更新 + 错误分级**） | P0 | 1d |
| F3 | 新增 Agent 类型定义（含 `AgentResultEvent`、精确联合类型替代 string/unknown） | P0 | 0.5d |
| F4 | 添加 `/agent` 路由 | P0 | 0.5d |

### 2.3 验收标准

- [ ] `POST /api/agent/execute` 返回 SSE 事件流
- [ ] OpsAgent 能执行"检查模型健康状态"任务
- [ ] SSE 事件包含 `step_start`、`tool_call`、`tool_result`、`agent_done`
- [ ] 前端能接收并解析 Agent SSE 事件

---

## 3. P1：核心 Agent + 前端面板（第 3-4 周）

### 3.1 目标

- 前端 Agent 执行面板完成
- OpsAgent 功能完整（健康检查、权重调整、熔断器操作）
- DataAgent 可用（自然语言查库）

### 3.2 任务清单

#### 后端

| # | 任务 | 优先级 | 预估 |
|---|------|--------|------|
| 1 | OpsAgent 完善：权重调整确认流程 | P0 | 1d |
| 2 | 实现 CircuitBreakerTools | P0 | 0.5d |
| 3 | 实现 TokenUsageTools | P1 | 0.5d |
| 4 | 实现 DataAgent + DatabaseTools（**含 JSqlParser AST 白名单校验**） | P0 | 2d |
| 5 | ~~实现 DatabaseTools SQL 安全校验~~（已前置到 P0） | - | - |
| 6 | 实现 `POST /api/agent/confirm` 确认接口 | P0 | 0.5d |
| 7 | 集成测试 | P0 | 1d |

#### 前端

| # | 任务 | 优先级 | 预估 |
|---|------|--------|------|
| 8 | `ExecutionStepCard` 组件（**拆分 StepHeader/Thought/ToolCall/ToolResult/Confirmation**，支持折叠展开） | P0 | 2d |
| 9 | `ConfirmationDialog` 组件（敏感操作确认，**移动端底部固定栏**） | P0 | 1d |
| 10 | `AgentExecutionView` 页面（布局、步骤列表、**ReconnectAlert 断线提示**） | P0 | 1d |
| 11 | `AgentSelector` 组件（**带权限过滤**） | P1 | 0.5d |
| 12 | `ExecutionStats` 组件 | P1 | 0.5d |
| 13 | `JsonViewer` 组件（工具参数/结果展示，使用第三方库 `vue-json-pretty`） | P1 | 1d |
| 14 | `MarkdownRenderer` 组件（最终输出渲染，使用 `markdown-it` + `highlight.js` + **DOMPurify**） | P1 | 0.5d |
| 15 | **虚拟滚动**：步骤 >30 时启用 `vue-virtual-scroller` | P1 | 0.5d |
| 16 | E2E 测试 | P1 | 1d |

### 3.3 验收标准

- [ ] 前端执行面板能实时展示 Agent 执行步骤
- [ ] OpsAgent 能完成"检查并优化模型权重"全流程
- [ ] DataAgent 能完成"查询上周 token 消耗"任务
- [ ] 敏感操作（权重调整）弹出确认对话框
- [ ] 用户确认后 Agent 继续执行
- [ ] 每步执行都有耗时和状态标记

---

## 4. P2：路由与协作（第 5-6 周）

### 4.1 目标

- RouterAgent 替代关键词匹配
- Agent 间可互相委托（OpsAgent 调 DataAgent 查数据）
- 路由置信度低于阈值时询问用户

### 4.2 任务清单

#### 后端

| # | 任务 | 优先级 | 预估 |
|---|------|--------|------|
| 1 | 实现 RouterAgent（LLM 路由 + 结构化输出） | P0 | 2d |
| 2 | 实现路由置信度阈值 + 用户确认 | P0 | 1d |
| 3 | 实现 Agent 间委托（AgentA 调用 AgentB） | P0 | 1d |
| 4 | 实现 `agent_call` / `agent_result` SSE 事件 | P0 | 0.5d |
| 5 | 重构 `AgentOrchestrator`，整合 RouterAgent | P0 | 1d |
| 6 | 集成测试 | P0 | 1d |

#### 前端

| # | 任务 | 优先级 | 预估 |
|---|------|--------|------|
| 7 | 步骤卡片支持 Agent 间委托展示 | P0 | 1d |
| 8 | 路由选择确认 UI | P1 | 0.5d |
| 9 | Agent 嵌套步骤的折叠/展开 | P1 | 0.5d |
| 10 | **前端状态持久化**：sessionStorage 保存执行状态，刷新后恢复 | P0 | 0.5d |

### 4.3 验收标准

- [ ] 用户输入"检查模型状态"自动路由到 OpsAgent
- [ ] 用户输入"查一下上周的数据"自动路由到 DataAgent
- [ ] 路由置信度 < 70% 时前端显示选择确认
- [ ] OpsAgent 执行中可委托 DataAgent 查询数据
- [ ] 前端正确展示 Agent 间委托关系

---

## 5. P3：扩展 Agent（第 7-8 周）

### 5.1 目标

- PromptAgent 可用（Prompt 优化、评测、A/B 测试）
- TestAgent 可用（测试生成、执行、分析）
- 四大 Agent 全部可用

### 5.2 任务清单

#### 后端

| # | 任务 | 优先级 | 预估 |
|---|------|--------|------|
| 1 | 实现 PromptAgent + PromptTools | P0 | 2d |
| 2 | 实现 TestAgent + TestGeneratorTools | P0 | 2d |
| 3 | 实现 SourceCodeTools（文件读取） | P0 | 1d |
| 4 | 实现 TestRunnerTools（Maven 执行） | P0 | 1d |
| 5 | RouterAgent 增加新 Agent 的路由规则 | P0 | 0.5d |
| 6 | 集成测试 | P0 | 1d |

#### 前端

| # | 任务 | 优先级 | 预估 |
|---|------|--------|------|
| 7 | Agent 选择器增加 Prompt/Test Agent | P1 | 0.5d |
| 8 | PromptAgent 专用展示（版本对比、评测分数） | P1 | 1d |
| 9 | TestAgent 专用展示（测试代码、执行结果） | P1 | 1d |
| 10 | E2E 测试 | P1 | 1d |

### 5.3 验收标准

- [ ] PromptAgent 能完成"优化 xxx prompt 并 A/B 测试"
- [ ] TestAgent 能完成"为 ChatController 生成单元测试"
- [ ] RouterAgent 正确路由到四个 Agent
- [ ] 前端正确展示各 Agent 的专用内容

---

## 6. P4：生产加固（第 9-10 周）

### 6.1 目标

- 安全体系完善（权限、审计、限流）
- 执行监控和告警
- 文档完善

### 6.2 任务清单

#### 安全

| # | 任务 | 优先级 | 预估 |
|---|------|--------|------|
| 1 | ~~实现 `AgentPermissionService`~~（已前置到 P0） | - | - |
| 2 | ~~实现 `AgentInputValidator`~~（已前置到 P0） | - | - |
| 3 | ~~实现 `AgentOutputFilter`~~（已前置到 P0） | - | - |
| 4 | 实现 `AgentExecutionControlService` **+ RedisQuotaProvider** | P0 | 1d |
| 4.1 | `AgentOrchestrator` 增加全局并发限流（`AtomicInteger`）+ 心跳线程池 `@PreDestroy` 关闭 | P0 | 0.5d |
| 4.2 | `AgentAuditService` 移除 `HttpServletRequest` 依赖，`logExecution` / `logResult` 精准定位 | P0 | 0.5d |
| 5 | 实现 `AgentAuditService` + 审计日志表 | P0 | 1d |
| 6 | Spring Security Agent 路径配置 | P0 | 0.5d |
| 7 | 实现 `ConfirmationStore` Redis 持久化 | P0 | 0.5d |
| 8 | 安全测试（渗透测试、SQL 注入、越权） | P0 | 1d |

#### 监控

| # | 任务 | 优先级 | 预估 |
|---|------|--------|------|
| 9 | Agent 执行指标（Prometheus） | P1 | 0.5d |
| 10 | Grafana Agent 执行仪表盘 | P1 | 1d |
| 11 | Token 消耗实时监控 | P1 | 0.5d |
| 12 | Agent 执行异常告警 | P2 | 0.5d |
| 13 | **前端可靠性监控**：SSE 重连次数、事件丢失率、状态恢复成功率 | P1 | 0.5d |

#### 管理

| # | 任务 | 优先级 | 预估 |
|---|------|--------|------|
| 13 | Agent 审计日志查询接口 | P1 | 0.5d |
| 14 | Agent 审计日志前端页面 | P1 | 1d |
| 15 | Agent 执行历史前端页面 | P1 | 1d |

#### 文档

| # | 任务 | 优先级 | 预估 |
|---|------|--------|------|
| 16 | 更新 CLAUDE.md | P1 | 0.5d |
| 17 | 编写 Agent 开发指南 | P2 | 1d |
| 18 | **编写前端生产级落地指南**（SSE 重连、状态持久化、移动端适配） | P1 | 0.5d |

### 6.3 验收标准

- [ ] 普通用户无法执行 ADMIN Agent
- [ ] SQL 注入被阻止
- [ ] 敏感数据被脱敏
- [ ] 并发执行限制生效
- [ ] 审计日志记录完整
- [ ] Prometheus 指标可查
- [ ] Grafana 仪表盘可展示 Agent 执行状态

---

## 7. 风险应对

### 7.1 技术风险

| 风险 | 应对 |
|------|------|
| LangChain4j Tool Calling 格式与预期不符 | P0 阶段优先验证，提前做 POC |
| SSE 事件流在 Nginx 代理下被缓冲 | 配置 `proxy_buffering off` |
| Agent 执行超时导致 SSE 连接断开 | 实现心跳机制 + 断线重连 |
| 前端 SSE 解析在弱网环境下丢事件 | 事件序号 + 缺失检测 + 重试 + Set 去重 |
| 页面刷新导致执行状态丢失 | sessionStorage 状态持久化 + 刷新后自动恢复 |

### 7.2 进度风险

| 风险 | 应对 |
|------|------|
| P0 阶段框架设计耗时超预期 | 简化 AbstractAgent，优先跑通流程 |
| 工具安全校验复杂度超预期 | P0 只做基础校验，P4 完善 |
| 前端组件复杂度超预期 | P0 只做最简步骤展示，P1 完善 |

---

## 8. 新增 Agent 扩展指南

### 8.1 步骤

新增一个 Agent 只需以下步骤（预估 2-4 小时）：

1. **实现 Agent 接口**

```java
@Component
public class MyAgent extends AbstractAgent {
    // 1. getMetadata() - 返回元信息
    // 2. buildSystemPrompt() - 返回提示词
    // 3. buildExecutor() - 注册工具
}
```

2. **实现工具类**

```java
@Component
public class MyTools {
    @AgentTool(name = "my_tool", description = "工具描述")
    public ToolResult myTool(@ToolParam(name = "param") String param) {
        return ToolResult.success(result);
    }
}
```

3. **注册 Agent Bean**

```java
@Bean
public MyAgent myAgent(...) { ... }
```

4. **注册工具到 ToolRegistry**

```java
registry.registerAnnotatedTools(myTools);
```

5. **更新 RouterAgent 提示词**（自动从 AgentRegistry 读取）

6. **前端无需修改**（步骤卡片是通用的）

### 8.2 扩展检查清单

- [ ] Agent 实现了 `Agent` 接口
- [ ] `getMetadata()` 返回正确的元信息
- [ ] 工具使用 `@AgentTool` 注解
- [ ] 敏感操作标记了 `requiresConfirmation`
- [ ] 权限配置了 `requiredPermissions`
- [ ] 编写了单元测试
- [ ] 更新了文档
