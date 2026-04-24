# 多 Agent 生产级系统技术方案

> 版本：1.0
> 日期：2026-04-24
> 目标：构建生产级多 Agent 协作系统，支持智能运维、数据分析、Prompt 工程、测试生成四大场景

---

## 文档索引

| 文档 | 内容 |
|------|------|
| [本文档](#) | 架构总览、设计原则、模块关系 |
| [agent-framework.md](agent-framework.md) | Agent 框架设计、Router、四大业务 Agent |
| [tool-system.md](tool-system.md) | 工具系统设计、安全边界（权限控制定义见 [agent-security.md](agent-security.md)） |
| [agent-sse-protocol.md](agent-sse-protocol.md) | SSE 协议扩展、步骤事件、前后端契约 |
| [agent-frontend-panel.md](agent-frontend-panel.md) | 前端执行面板、组件设计、交互流程 |
| [agent-security.md](agent-security.md) | 安全校验、权限模型、审计日志 |
| [agent-implementation-phases.md](agent-implementation-phases.md) | 分阶段实现计划、里程碑、验收标准 |

---

## 1. 现状分析

### 1.1 现有架构

```
当前架构（ChatAssistant 单一入口）：

用户请求 → ChatController → AiService → ChatAssistant (AiServices)
                                            ↓
                                     LoadBalancedChatModel
                                            ↓
                                    多模型负载均衡 + 熔断
```

**已有能力：**
- LangChain4j 1.13.0 AiServices（`@Tool` 注解、`@SystemMessage`）
- 多模型负载均衡 + 熔断器（`LoadBalancedChatModel`）
- SSE 流式输出（`event: token` / `event: done` / `event: error`）
- Agent 可观测性（`AgentTraceService`、状态快照、评测）
- 工具注册体系（`ToolRegistry`、`@AgentTool` 注解）

**现有问题：**
| 问题 | 影响 |
|------|------|
| `ReActAgent` 手写 ReAct 循环 | 正则解析脆弱，换模型可能失效 |
| `AgentOrchestrator` 关键词路由 | 中文关键词硬编码，不可靠 |
| 工具系统两套（`@Tool` vs `ToolRegistry`） | 体系割裂，维护困难 |
| Agent 执行过程黑盒 | 用户看不到中间步骤 |
| 无 Agent 间协作 | 每个 Agent 孤立，无法组合 |

### 1.2 设计目标

| 目标 | 说明 |
|------|------|
| **生产级可靠性** | 结构化输出替代正则，错误恢复，超时控制 |
| **可观测性** | 每步执行透明，实时推送到前端 |
| **可扩展性** | 新增 Agent 只需实现接口 + 注册，不改核心代码 |
| **安全性** | 敏感工具需确认，只读操作白名单，审计日志 |
| **协作能力** | Agent 可委托其他 Agent，Router 智能路由 |
| **前端可靠性** | SSE 断线自动重连、事件去重、状态持久化（刷新恢复）、批量 UI 更新 |

---

## 2. 总体架构

### 2.1 分层架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         表现层 (Presentation)                     │
│  AgentExecutionController ─── SSE 端点 ─── AgentExecutionPanel   │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                        应用层 (Application)                       │
│  AgentOrchestrator ─── RouterAgent ─── 路由到专业 Agent           │
└─────────────────────────────────────────────────────────────────┘
                                │
        ┌───────────────────────┼───────────────────────┐
        ▼                       ▼                       ▼
┌──────────────┐        ┌──────────────┐        ┌──────────────┐
│  OpsAgent    │        │  DataAgent   │        │  PromptAgent │
│  (运维助手)   │        │  (数据分析)   │        │  (Prompt工程) │
└──────────────┘        └──────────────┘        └──────────────┘
        │                       │                       │
        └───────────────────────┼───────────────────────┘
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                        工具层 (Tools)                            │
│  ToolRegistry ─── ToolDefinition ─── 权限校验 ─── 执行           │
│  内置工具：ModelStateTools, DatabaseTools, PromptTools...       │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                      基础设施层 (Infrastructure)                  │
│  LoadBalancedChatModel ─── AgentTraceService ─── AgentState     │
│  多模型负载均衡            执行追踪               状态快照          │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 核心组件职责

| 组件 | 职责 | 所在层 |
|------|------|--------|
| `AgentExecutionController` | 接收 Agent 执行请求，SSE 流式返回步骤 | 表现层 |
| `AgentOrchestrator` | 协调 Agent 执行，管理上下文 | 应用层 |
| `RouterAgent` | LLM 智能路由，替代关键词匹配 | 应用层 |
| `OpsAgent` | 智能运维：模型诊断、故障处置 | 应用层 |
| `DataAgent` | 数据分析：自然语言查库、图表生成 | 应用层 |
| `PromptAgent` | Prompt 工程：优化、评测、A/B 测试 | 应用层 |
| `TestAgent` | 测试生成：单元/集成/E2E 测试生成 | 应用层 |
| `ToolRegistry` | 工具注册、发现、执行 | 工具层 |
| `AgentTraceService` | 执行追踪、步骤记录 | 基础设施层 |

### 2.3 数据流

```
用户: "检查模型健康状态，优化权重分配"
        │
        ▼
┌─────────────────────────────────────────────────────┐
│ 1. AgentExecutionController 接收请求               │
│    - 生成 traceId、sessionId                        │
│    - 创建 SSE 连接                                   │
└─────────────────────────────────────────────────────┘
        │ emit event: step_start {sequenceNumber: 0, stepIndex: 0, type: "router"}
        ▼
┌─────────────────────────────────────────────────────┐
│ 2. RouterAgent 分析意图                             │
│    - LLM 判断任务类型                               │
│    - 选择目标 Agent: OpsAgent                        │
│    - 返回理由                                       │
└─────────────────────────────────────────────────────┘
        │ emit event: step_end {sequenceNumber: 1, stepIndex: 0, result: "OpsAgent"}
        │ emit event: step_start {sequenceNumber: 2, stepIndex: 1, type: "agent", agent: "OpsAgent"}
        ▼
┌─────────────────────────────────────────────────────┐
│ 3. OpsAgent 执行                                    │
│    Step 3.1: 获取模型健康状态                        │
│    - 调用 get_model_health() 工具                  │
│    - emit event: tool_call                          │
│    - emit event: tool_result                        │
│                                                      │
│    Step 3.2: 分析结果                                │
│    - LLM 思考：deepseek 延迟过高                     │
│    - emit event: thought                            │
│                                                      │
│    Step 3.3: 调整权重                                │
│    - 调用 adjust_model_weight("deepseek", 5)       │
│    - 检查权限 → 需要确认                             │
│    - emit event: confirmation_required               │
│    - AgentContext.awaitConfirmation() 挂起等待      │
│    - 用户通过 POST /api/agent/confirm 确认           │
│    - AgentOrchestrator 调用 resolveConfirmation()   │
│    - CompletableFuture 完成，Agent 恢复执行          │
│                                                      │
│    Step 3.4: 验证结果                                │
│    - 调用 get_model_health() 验证                  │
│    - 生成最终结论                                   │
└─────────────────────────────────────────────────────┘
        │ emit event: step_end {sequenceNumber: 3, stepIndex: 1}
        │ emit event: agent_done {summary, tokenUsage, duration}
        ▼
┌─────────────────────────────────────────────────────┐
│ 4. 响应完成                                         │
│    - 记录追踪数据到数据库                            │
│    - SSE 连接关闭                                   │
└─────────────────────────────────────────────────────┘
```

---

## 3. Agent 类型设计

### 3.1 Agent 类型枚举

```java
public enum AgentType {
    ROUTER("router", "路由 Agent", "分析意图，选择合适的 Agent"),
    OPS("ops", "运维助手", "模型诊断、故障处置、健康监控"),
    DATA("data", "数据分析助手", "自然语言查库、图表生成、数据导出"),
    PROMPT("prompt", "Prompt 工程", "Prompt 优化、评测、A/B 测试"),
    TEST("test", "测试生成助手", "单元/集成/E2E 测试生成与执行"),
    CHAT("chat", "通用对话", "普通问答、闲聊");
    
    private final String code;
    private final String displayName;
    private final String description;
}
```

### 3.2 Agent 元信息

```java
public record AgentMetadata(
    String name,                    // Agent 名称标识
    AgentType agentType,            // Agent 类型枚举
    String displayName,            // 显示名称
    String description,            // 功能描述
    String version,               // 版本号
    Set<String> capabilities,      // 能力标签
    Set<String> requiredPermissions,// 需要的权限
    int maxIterations,            // 最大迭代次数
    Duration timeout,             // 执行超时
    boolean supportsStreaming     // 是否支持流式
) {
    // 工厂方法
    public static AgentMetadata ops() {
        return new AgentMetadata(
            "ops", AgentType.OPS, "运维助手", "智能运维：模型诊断与故障处置",
            "1.0.0",
            Set.of("model:read", "model:write", "circuit-breaker:read"),
            Set.of("ADMIN"),
            10, Duration.ofMinutes(5), true
        );
    }
}

// TokenUsage 定义详见 agent-framework.md（避免重复定义导致维护不一致）
```

### 3.3 四大业务 Agent 概览

| Agent | 场景 | 核心工具 | 权限要求 |
|-------|------|----------|----------|
| **OpsAgent** | 模型诊断、故障处置、健康监控 | `get_model_health`, `adjust_model_weight`, `get_circuit_breaker_status` | ADMIN |
| **DataAgent** | 自然语言查库、图表生成、数据导出 | `execute_readonly_query`, `list_tables`, `generate_chart` | USER+ |
| **PromptAgent** | Prompt 优化、评测、A/B 测试 | `get_prompt_versions`, `evaluate_prompt`, `create_prompt_version` | ADMIN |
| **TestAgent** | 单元/集成/E2E 测试生成 | `read_source_code`, `generate_test`, `run_test` | ADMIN |

---

## 4. 与现有系统集成

### 4.1 复用现有基础设施

| 现有组件 | 复用方式 |
|----------|----------|
| `LoadBalancedChatModel` | Agent 直接使用，继承多模型 HA 能力 |
| `AgentTraceService` | 每步执行记录 trace/span |
| `AgentStateService` | 支持断点续传 |
| `SecurityConfig` | Agent 接口走现有 JWT 认证 |
| `ModelHealthController` | OpsAgent 的工具底层调用此 API |

### 4.2 扩展点

| 扩展点 | 现有 | 扩展 |
|--------|------|------|
| SSE 事件类型 | `token`/`done`/`error` | 增加 `step_start`/`tool_call`/`tool_result`/`thought`/`confirmation_required` |
| 工具注册 | `@Tool` + `ToolRegistry` 两套 | 统一为 `@AgentTool` + `ToolRegistry`，兼容 AiServices |
| Agent 执行 | 无 | 新增 `AgentExecutionController` + `AgentOrchestrator` |
| 前端展示 | MessageItem 消息流 | 新增 AgentExecutionPanel 步骤卡片（含断线重连、状态持久化、批量更新） |

### 4.3 兼容性策略

- **向后兼容**：现有 `/api/chat` 和 `/api/chat/stream` 接口不变
- **新增端点**：`/api/agent/execute` 用于 Agent 执行
- **前端路由**：`/agent` 新增 Agent 执行页面，`/chat` 保留原有聊天

---

## 5. 设计原则

### 5.1 核心原则

1. **最小惊讶原则**：Agent 行为符合用户直觉，错误信息清晰
2. **渐进式披露**：简单任务自动执行，敏感操作需确认
3. **失败安全**：任何步骤失败都能回滚或恢复
4. **可观测优先**：每步都可追踪，调试信息完整
5. **扩展开放**：新增 Agent 只需实现接口 + 注册

### 5.2 约束清单

| 约束 | 说明 |
|------|------|
| 单次执行 Token 上限 | 100K tokens，防止单次消耗过多 |
| 单次工具调用上限 | 20 次，防止无限循环 |
| 执行超时 | 默认 5 分钟，可配置 |
| 敏感操作审批 | 数据库写入、模型配置修改需 ADMIN 确认 |
| 只读白名单 | DataAgent 只能查询白名单表 |

---

## 6. 风险与缓解

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|----------|
| LLM 路由选择错误 | 中 | 中 | 增加路由置信度阈值，低于阈值时询问用户确认 |
| 工具参数解析失败 | 中 | 高 | JSON Schema 校验 + 重试机制 |
| Agent 执行超时 | 低 | 中 | 超时后保存状态，支持断点续传 |
| 敏感操作误执行 | 低 | 高 | 敏感操作强制人工确认 + 审计日志 |
| Token 消耗过高 | 中 | 中 | 单次执行 Token 上限 + 实时监控 |
| SSE 断线导致状态丢失 | 中 | 高 | 前端自动重连（指数退避）+ 事件序号去重 + sessionStorage 状态持久化 |

---

## 7. 成功标准

| 维度 | 标准 |
|------|------|
| **功能** | 四大 Agent 全部可用，Router 正确路由率 > 90% |
| **可靠性** | Agent 执行成功率 > 95%，错误恢复率 100% |
| **性能** | 单步工具调用 < 2s，总执行时间 < 5min |
| **可观测** | 100% 执行可追踪，步骤信息完整 |
| **安全** | 敏感操作 100% 确认，审计日志完整 |
| **扩展** | 新增 Agent < 2 小时（只实现接口 + 注册工具） |
| **前端可靠性** | SSE 断线 100% 自动恢复，页面刷新后 30min 内可恢复执行状态 |
