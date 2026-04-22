# Agent 开发指南

本指南帮助开发者快速理解并掌握本项目的 Agent 开发完整流程，包括 Agent 架构设计、工具开发、可观测性集成等核心内容。

## 目录

1. [整体架构概览](#1-整体架构概览)
2. [快速开始](#2-快速开始)
3. [Agent 核心组件详解](#3-agent-核心组件详解)
4. [工具开发指南](#4-工具开发指南)
5. [可观测性系统](#5-可观测性系统)
6. [状态持久化与断点续传](#6-状态持久化与断点续传)
7. [评测框架](#7-评测框架)
8. [Prompt 版本管理](#8-prompt-版本管理)
9. [最佳实践](#9-最佳实践)
10. [常见问题](#10-常见问题)

---

## 1. 整体架构概览

### 1.1 系统架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                         REST API Layer                          │
│                    PlanningController                           │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                       Agent Orchestrator                        │
│              (任务类型判断与 Agent 路由)                         │
└─────────────────────────────────────────────────────────────────┘
                                │
          ┌─────────────────────┼─────────────────────┐
          ▼                     ▼                     ▼
┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
│   ReActAgent     │  │ PlanExecuteAgent │  │  Simple Executor │
│  (推理-行动循环) │  │  (规划-执行模式) │  │   (简单任务)     │
└──────────────────┘  └──────────────────┘  └──────────────────┘
          │                     │
          └──────────┬──────────┘
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│                        Tool Registry                            │
│    (工具注册、执行、安全校验、弹性控制、审计)                    │
└─────────────────────────────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Observability System                        │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌───────────┐ │
│  │   Trace     │ │ Evaluation  │ │   Prompt    │ │   State   │ │
│  │  (追踪)     │ │  (评测)     │ │  (模板管理) │ │ (持久化)  │ │
│  └─────────────┘ └─────────────┘ └─────────────┘ └───────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

### 1.2 核心子系统

| 子系统 | 包路径 | 职责 |
|--------|--------|------|
| Planning | `com.jonychen.planning` | Agent 执行引擎（ReAct / Plan-Execute） |
| Tool | `com.jonychen.tool` | 工具注册、执行、安全、弹性、审计 |
| Observability | `com.jonychen.observability` | 追踪、评测、Prompt 管理、状态持久化 |
| Assistant | `com.jonychen.assistant` | 简化版对话助手（LangChain4j AiServices） |

### 1.3 Agent 类型对比

| Agent 类型 | 适用场景 | 执行模式 | 关键词示例 |
|-----------|---------|---------|-----------|
| ReAct | 复杂推理任务 | Thought → Action → Observation 循环 | 分析、比较、评估、决策、探索 |
| Plan-Execute | 多步骤任务 | Planning → Execution → Replanning | 然后、接着、步骤、依次、顺序 |
| Simple | 简单单步任务 | 直接执行 | 其他简单请求 |

---

## 2. 快速开始

### 2.1 使用现有 Agent

通过 REST API 调用 Agent：

```bash
# 自动选择 Agent 类型
curl -X POST http://localhost:8082/api/planning/execute \
  -H "Content-Type: application/json" \
  -d '{"goal": "计算 (10 + 5) * 2 的结果"}'

# 指定使用 ReAct Agent
curl -X POST http://localhost:8082/api/planning/react \
  -H "Content-Type: application/json" \
  -d '{"question": "分析当前时间并转换为 ISO 格式"}'

# 指定使用 Plan-Execute Agent
curl -X POST http://localhost:8082/api/planning/plan-execute \
  -H "Content-Type: application/json" \
  -d '{"goal": "获取当前日期，然后计算一周后的日期"}'
```

### 2.2 在代码中使用 Agent

```java
@Service
@RequiredArgsConstructor
public class MyService {

    private final AgentOrchestrator orchestrator;
    private final ReActAgent reActAgent;
    private final ToolRegistry toolRegistry;

    public String processQuestion(String question) {
        // 创建执行上下文
        TaskContext context = TaskContext.builder()
                .sessionId(UUID.randomUUID().toString())
                .userId("user-001")
                .toolRegistry(toolRegistry)
                .build();

        // 自动选择 Agent 执行
        TaskResult result = orchestrator.execute(question, context);

        if (result.success()) {
            return String.valueOf(result.finalOutput());
        } else {
            throw new RuntimeException(result.error());
        }
    }

    public String useReActDirectly(String question) {
        TaskContext context = TaskContext.create("session-001", "user-001");
        ReActResult result = reActAgent.execute(question, context);
        return result.answer();
    }
}
```

---

## 3. Agent 核心组件详解

### 3.1 ReActAgent（推理-行动循环）

ReActAgent 实现了经典的 ReAct（Reasoning + Acting）模式，通过"思考-行动-观察"循环逐步解决问题。

**执行流程：**

```
用户问题 → 构建 Prompt（工具列表 + 问题）
     ↓
┌─────────────────────────────────┐
│  循环（最多 10 次）              │
│    ↓                            │
│  LLM 生成响应                   │
│    ↓                            │
│  解析响应：Thought/Action/Final │
│    ↓                            │
│  ┌─ Final Answer? → 返回结果    │
│  └─ Action? → 执行工具 → 观察   │
│    ↓                            │
│  更新历史，继续循环              │
└─────────────────────────────────┘
```

**核心代码位置：** `src/main/java/com/jonychen/planning/agent/ReActAgent.java`

**Prompt 模板结构：**

```
你是一个智能助手，使用 ReAct 模式解决问题。

遵循以下格式：

Thought: 思考当前情况，分析下一步该做什么
Action: 工具名称
Action Input: JSON 格式的参数，如 {"param": "value"}
Observation: 工具返回的结果
... (重复 Thought/Action/Observation 直到可以回答)
Thought: 我现在知道最终答案了
Final Answer: 最终回答

可用工具：
{tools}

规则：
1. 每次只能执行一个工具
2. Action 必须是可用工具之一
3. Action Input 必须是有效的 JSON
4. 如果已经有足够信息，直接给出 Final Answer

开始！

用户问题：{question}
{history}
```

### 3.2 PlanExecuteAgent（规划-执行模式）

PlanExecuteAgent 先让 LLM 生成完整计划，然后按顺序执行每个步骤，遇到失败时可重新规划。

**执行流程：**

```
用户目标 → LLM 生成步骤列表（JSON）
     ↓
┌─────────────────────────────────┐
│  循环执行步骤                    │
│    ↓                            │
│  获取下一个待执行步骤            │
│    ↓                            │
│  ┌─ 有工具? → 执行工具           │
│  └─ 无工具? → LLM 执行           │
│    ↓                            │
│  步骤成功? → 继续下一步          │
│  步骤失败? → 重新规划（最多3次） │
└─────────────────────────────────┘
     ↓
构建最终结果
```

**核心代码位置：** `src/main/java/com/jonychen/planning/agent/PlanExecuteAgent.java`

### 3.3 AgentOrchestrator（编排器）

AgentOrchestrator 根据任务目标的关键词自动选择合适的 Agent 类型。

**路由规则：**

```java
private TaskType determineTaskType(String goal) {
    String lowerGoal = goal.toLowerCase();

    // 复杂任务关键词 → ReAct
    if (lowerGoal.contains("分析") || lowerGoal.contains("比较")
            || lowerGoal.contains("评估") || lowerGoal.contains("决策")
            || lowerGoal.contains("不确定") || lowerGoal.contains("探索")) {
        return TaskType.COMPLEX;
    }

    // 多步骤关键词 → Plan-Execute
    if (lowerGoal.contains("然后") || lowerGoal.contains("接着")
            || lowerGoal.contains("之后") || lowerGoal.contains("步骤")
            || lowerGoal.contains("依次") || lowerGoal.contains("顺序")) {
        return TaskType.MULTI_STEP;
    }

    // 其他 → Simple
    return TaskType.SIMPLE;
}
```

**核心代码位置：** `src/main/java/com/jonychen/planning/AgentOrchestrator.java`

### 3.4 数据模型

#### Task（任务）

```java
public record Task(
    String taskId,           // 任务 ID
    String goal,             // 任务目标
    TaskType type,           // 任务类型
    List<Step> steps,        // 步骤列表
    TaskStatus status,       // 状态：PENDING/EXECUTING/COMPLETED/FAILED
    long totalExecutionTimeMs
) {
    // 创建任务
    public static Task create(String goal);
    
    // 添加步骤
    public Task addStep(Step step);
    
    // 更新步骤
    public Task updateStep(String stepId, Step updatedStep);
    
    // 获取下一个待执行步骤
    public Step getNextPendingStep();
}
```

#### Step（步骤）

```java
public record Step(
    String stepId,           // 步骤 ID
    int order,               // 执行顺序
    String description,      // 步骤描述
    String action,           // 具体行动
    String tool,             // 工具名称（可选）
    Map<String, Object> params, // 工具参数
    StepStatus status,       // 状态：PENDING/RUNNING/COMPLETED/FAILED
    List<String> dependsOn,  // 依赖的步骤 ID
    StepResult result        // 执行结果
) {
    // 创建普通步骤
    public static Step create(int order, String description, String action);
    
    // 创建工具步骤
    public static Step createWithTool(int order, String description, 
                                       String tool, Map<String, Object> params);
}
```

#### TaskContext（执行上下文）

```java
public class TaskContext {
    private String sessionId;           // 会话 ID
    private String userId;              // 用户 ID
    private Map<String, Object> variables;     // 变量存储
    private Map<String, StepResult> stepResults; // 步骤结果
    private ToolRegistry toolRegistry;  // 工具注册中心
    private ChatMemory chatMemory;      // 对话记忆
    
    public static TaskContext create(String sessionId, String userId);
}
```

#### ReActStep（ReAct 步骤）

```java
public record ReActStep(
    String thought,          // 思考内容
    String action,           // 行动名称
    String actionInput,      // 行动参数（JSON）
    String observation,      // 观察结果
    String finalAnswer       // 最终答案
) {
    // 判断是否为最终答案
    public boolean isFinalAnswer();
    
    // 判断是否有行动
    public boolean hasAction();
    
    // 创建带观察结果的步骤
    public ReActStep withObservation(String observation);
}
```

---

## 4. 工具开发指南

### 4.1 工具开发方式

项目支持两种工具开发方式：

#### 方式一：声明式（推荐）

使用 `@AgentTool` 和 `@ToolParam` 注解声明工具：

```java
@Component
public class MyTools {

    @AgentTool(
        name = "search_web",           // 工具名称
        description = "在网络上搜索信息", // 功能描述
        category = ToolCategory.SEARCH, // 工具分类
        requiredPermissions = {"search"}, // 所需权限
        timeoutMs = 30000,             // 超时时间（毫秒）
        maxRetries = 2                 // 最大重试次数
    )
    public ToolResult searchWeb(
        @ToolParam(name = "query", description = "搜索关键词")
        String query,
        
        @ToolParam(name = "limit", description = "返回结果数量", required = false)
        Integer limit,
        
        @ToolParam(name = "type", description = "搜索类型", 
                   enumValues = {"web", "news", "image"})
        String type
    ) {
        try {
            // 执行搜索逻辑
            List<SearchResult> results = searchService.search(query, limit, type);
            return ToolResult.success(Map.of(
                "results", results,
                "total", results.size()
            ));
        } catch (Exception e) {
            return ToolResult.failure("搜索失败: " + e.getMessage());
        }
    }
}
```

#### 方式二：编程式

使用 Builder 模式构建工具定义：

```java
@Service
public class ToolRegistrationService {

    public ToolDefinition createDatabaseQueryTool() {
        // 构建参数 Schema
        ToolParameterSchema schema = new ToolParameterSchema()
            .addProperty("sql", ToolParameterSchema.Property.string("SQL 查询语句"))
            .addProperty("limit", ToolParameterSchema.Property.integer("返回行数限制"))
            .addRequired("sql");

        // 创建工具定义
        return ToolDefinition.builder()
            .name("query_database")
            .description("执行 SQL 查询")
            .category(ToolCategory.DATABASE)
            .parameters(schema)
            .executor(params -> {
                String sql = (String) params.get("sql");
                Integer limit = (Integer) params.getOrDefault("limit", 100);
                // 执行查询
                List<Map<String, Object>> results = executeQuery(sql, limit);
                return ToolResult.success(results);
            })
            .requiredPermissions(List.of("db_read"))
            .timeout(Duration.ofSeconds(60))
            .maxRetries(1)
            .build();
    }
}
```

### 4.2 注册工具

在配置类中注册工具：

```java
@Configuration
public class MyToolConfig {

    @Bean
    public ToolRegistryInitializer myToolInitializer(
            ToolRegistry toolRegistry,
            MyTools myTools) {
        return new ToolRegistryInitializer(toolRegistry, myTools);
    }

    public static class ToolRegistryInitializer {
        public ToolRegistryInitializer(ToolRegistry registry, MyTools myTools) {
            // 方式一：通过注解自动注册
            registry.registerAnnotatedTools(myTools);
            
            // 方式二：手动注册
            // registry.register(toolDefinition);
        }
    }
}
```

### 4.3 工具分类

```java
public enum ToolCategory {
    SYSTEM,     // 系统工具（计算器、日期时间等）
    SEARCH,     // 搜索工具
    DATABASE,   // 数据库工具
    FILE,       // 文件操作工具
    EXTERNAL,   // 外部 API 工具
    CUSTOM      // 自定义工具
}
```

### 4.4 工具结果

```java
public record ToolResult(
    boolean success,         // 是否成功
    Object data,             // 返回数据
    String error,            // 错误信息
    long executionTimeMs,    // 执行时间
    Map<String, Object> metadata, // 元数据
    boolean pending,         // 是否等待确认
    String confirmationId    // 确认 ID
) {
    // 创建成功结果
    public static ToolResult success(Object data);
    
    // 创建失败结果
    public static ToolResult failure(String error);
    
    // 创建待确认结果（高风险操作）
    public static ToolResult pendingConfirmation(String confirmationId, String message);
}
```

### 4.5 工具执行管道

工具执行时经过多层处理：

```
ConfirmedToolExecutor（确认层）
  │
  ├─ ToolRiskEvaluator 评估风险等级
  ├─ 高风险 → ToolConfirmationManager（Redis 存储）
  │           → 返回 pendingConfirmation
  └─ 非高风险 → ResilientToolExecutor（弹性层）
                │
                ├─ 超时控制（Future + timeout）
                ├─ 重试（Resilience4j Retry）
                ├─ 熔断（Resilience4j CircuitBreaker）
                └─ 降级（fallbackResult）
                    │
                    └─ IdempotentToolExecutor（幂等层，可选）
                        │
                        ├─ ToolParameterValidator（校验层）
                        ├─ IdempotencyManager（Redis 原子操作）
                        └─ 执行工具 → 返回结果
```

### 4.6 参数校验

`ToolParameterValidator` 提供四层校验：

1. **必需参数校验** - 检查 schema 中 required 字段
2. **类型校验** - String/Integer/Number/Boolean/Array/Object
3. **业务规则校验** - 枚举值、数值范围、字符串长度
4. **安全校验** - SQL 注入检测、路径遍历检测、XSS 检测

### 4.7 内置工具示例

#### CalculatorTools（计算器工具）

```java
@Component
public class CalculatorTools {

    @AgentTool(
        name = "calculate",
        description = "执行基础数学计算：加减乘除",
        category = ToolCategory.SYSTEM
    )
    public ToolResult calculate(
        @ToolParam(name = "a", description = "第一个操作数")
        Double a,
        @ToolParam(name = "operation", description = "运算符：+、-、*、/")
        String operation,
        @ToolParam(name = "b", description = "第二个操作数")
        Double b
    ) {
        double result = switch (operation) {
            case "+" -> a + b;
            case "-" -> a - b;
            case "*" -> a * b;
            case "/" -> a / b;
            default -> throw new IllegalArgumentException("不支持的运算符");
        };
        return ToolResult.success(Map.of("result", result));
    }

    @AgentTool(
        name = "evaluate_expression",
        description = "计算数学表达式的值，支持加减乘除和括号",
        category = ToolCategory.SYSTEM
    )
    public ToolResult evaluateExpression(
        @ToolParam(name = "expression", description = "数学表达式，如 2+3*4")
        String expression
    ) {
        // 表达式求值逻辑...
    }
}
```

#### DateTimeTools（日期时间工具）

```java
@Component
public class DateTimeTools {

    @AgentTool(
        name = "get_current_time",
        description = "获取当前的日期和时间，支持指定时区",
        category = ToolCategory.SYSTEM
    )
    public ToolResult getCurrentTime(
        @ToolParam(name = "timezone", description = "时区，如 Asia/Shanghai", required = false)
        String timezone,
        @ToolParam(name = "format", description = "输出格式：default 或 iso", required = false)
        String format
    ) {
        ZoneId zone = parseTimezone(timezone);
        ZonedDateTime now = ZonedDateTime.now(zone);
        return ToolResult.success(Map.of(
            "datetime", now.format(formatter),
            "timezone", zone.toString(),
            "timestamp", now.toInstant().toEpochMilli()
        ));
    }
}
```

---

## 5. 可观测性系统

### 5.1 追踪系统（Trace）

追踪系统自动记录 Agent 执行全过程，包括思考、工具调用、LLM 调用等。

#### 核心组件

| 组件 | 说明 |
|------|------|
| `AgentTrace` | 追踪实体，记录整个执行过程 |
| `AgentTraceSpan` | Span 实体，记录单个步骤 |
| `AgentTraceService` | 追踪生命周期管理服务 |
| `AgentTraceAspect` | AOP 切面，自动追踪 |
| `TraceContext` | ThreadLocal 上下文 |

#### AgentTrace 结构

```java
@Entity
@Table(name = "agent_traces")
public class AgentTrace {
    private String traceId;           // 追踪 ID
    private String sessionId;         // 会话 ID
    private String userId;            // 用户 ID
    private String agentType;         // Agent 类型：REACT/PLAN_EXECUTE
    private String status;            // 状态：RUNNING/COMPLETED/FAILED/CANCELLED
    private String goal;              // 任务目标
    private String finalOutput;       // 最终输出
    private LocalDateTime startTime;  // 开始时间
    private LocalDateTime endTime;    // 结束时间
    private Long executionTimeMs;     // 执行时间
    private Integer iterations;       // 迭代次数
    private TokenUsage tokenUsage;    // Token 用量
    private List<Map<String, Object>> spans; // Span 列表（JSONB）
}
```

#### AgentTraceSpan 类型

```java
public enum SpanType {
    THOUGHT,         // 思考过程
    ACTION,          // 行动选择
    OBSERVATION,     // 观察结果
    LLM_CALL,        // LLM 调用
    TOOL_EXECUTE,    // 工具执行
    PLANNING,        // 规划
    AGENT_CALL,      // Agent 调用
    STATE_UPDATE     // 状态更新
}
```

#### 使用追踪服务

```java
@Service
@RequiredArgsConstructor
public class MyAgentService {

    private final AgentTraceService traceService;

    public Object executeWithTrace(String question, TaskContext context) {
        // 1. 开始追踪
        AgentTrace trace = traceService.startTrace(
            context.getSessionId(), 
            context.getUserId(), 
            "CUSTOM", 
            question
        );

        try {
            // 2. 记录思考
            traceService.recordThought(trace.getTraceId(), "正在分析问题...");

            // 3. 执行业务逻辑
            Object result = doWork(question);

            // 4. 记录 LLM 调用
            traceService.recordLLMCall(
                trace.getTraceId(), 
                prompt, response, 
                promptTokens, completionTokens, 
                durationMs
            );

            // 5. 结束追踪（成功）
            traceService.endTraceSuccess(
                trace.getTraceId(), 
                String.valueOf(result), 
                promptTokens, completionTokens
            );

            return result;
        } catch (Exception e) {
            // 6. 结束追踪（失败）
            traceService.endTraceFailed(trace.getTraceId(), e.getMessage());
            throw e;
        }
    }
}
```

#### 查询追踪数据

```bash
# 获取追踪列表
GET /api/admin/observability/traces?userId=user-001&status=COMPLETED

# 获取追踪详情
GET /api/admin/observability/traces/{traceId}

# 获取追踪 Span 列表
GET /api/admin/observability/traces/{traceId}/spans

# 获取活跃追踪
GET /api/admin/observability/traces/active

# 获取统计信息
GET /api/admin/observability/traces/statistics
```

### 5.2 审计系统

`ToolExecutionAuditAspect` 自动记录所有工具调用：

```java
@Around("execution(* com.jonychen.tool.ToolRegistry.execute(..))")
public Object auditToolExecution(ProceedingJoinPoint joinPoint) throws Throwable {
    String toolName = (String) joinPoint.getArgs()[0];
    Map<String, Object> params = (Map<String, Object>) joinPoint.getArgs()[1];

    long startTime = System.currentTimeMillis();
    try {
        Object result = joinPoint.proceed();
        
        // 记录成功审计
        saveAudit(toolName, params, result, true, null);
        return result;
    } catch (Exception e) {
        // 记录失败审计
        saveAudit(toolName, params, null, false, e.getMessage());
        throw e;
    }
}
```

---

## 6. 状态持久化与断点续传

### 6.1 概述

状态持久化系统支持 Agent 执行过程中的断点续传，适用于长时间运行的任务。

### 6.2 核心组件

| 组件 | 说明 |
|------|------|
| `AgentStateSnapshot` | 状态快照实体 |
| `AgentStateService` | 状态管理服务 |
| `ResumableReActAgent` | 可恢复的 ReAct Agent |

### 6.3 快照类型

```java
public enum SnapshotType {
    CHECKPOINT,     // 检查点（可恢复）
    ERROR,          // 错误快照（不可恢复）
    PAUSE,          // 暂停快照（可恢复）
    STEP_COMPLETE   // 步骤完成快照
}
```

### 6.4 使用断点续传

```java
@Service
@RequiredArgsConstructor
public class LongRunningTaskService {

    private final ResumableReActAgent resumableAgent;
    private final AgentStateService stateService;

    public ReActResult executeLongTask(String question, TaskContext context) {
        String sessionId = context.getSessionId();

        // 1. 检查是否有可恢复的快照
        if (resumableAgent.canResume(sessionId)) {
            Optional<AgentStateSnapshot> snapshot = resumableAgent.getResumableSnapshot(sessionId);
            if (snapshot.isPresent()) {
                // 从断点恢复
                return resumableAgent.resume(snapshot.get(), question, context);
            }
        }

        // 2. 正常执行
        return resumableAgent.execute(question, context);
    }

    public void pauseExecution(String sessionId, String traceId, 
                               List<ReActStep> completedSteps, int currentStep) {
        // 暂停执行并保存快照
        resumableAgent.pause(sessionId, traceId, completedSteps, currentStep, 10);
    }

    public void abandonResume(String sessionId) {
        // 放弃恢复
        resumableAgent.abandonResume(sessionId);
    }
}
```

### 6.5 API 接口

```bash
# 获取会话快照
GET /api/admin/observability/snapshots/session/{sessionId}

# 获取可恢复快照
GET /api/admin/observability/snapshots/session/{sessionId}/resumable

# 获取快照统计
GET /api/admin/observability/snapshots/statistics

# 清理过期快照
POST /api/admin/observability/snapshots/cleanup
```

---

## 7. 评测框架

### 7.1 概述

评测框架用于量化评估 Agent 执行质量，支持多种评测器自动评测。

### 7.2 评测器接口

```java
public interface AgentEvaluator {
    
    /**
     * 执行评测
     */
    EvaluationResult evaluate(EvaluationContext context);
    
    /**
     * 批量评测
     */
    default BatchEvaluationResult evaluateBatch(List<AgentTrace> traces);
    
    /**
     * 评测器名称
     */
    String getName();
    
    /**
     * 评测器描述
     */
    String getDescription();
    
    // 评测上下文
    record EvaluationContext(
        AgentTrace trace,
        String expectedOutput,
        List<String> expectedTools,
        Map<String, Object> constraints,
        String mode
    ) {}
    
    // 评测结果
    record EvaluationResult(
        String evaluatorId,
        boolean passed,
        double score,
        Map<String, Object> details,
        List<String> issues,
        String recommendation
    ) {}
}
```

### 7.3 内置评测器

#### TaskCompletionEvaluator（任务完成度评测器）

| 维度 | 权重 | 说明 |
|------|------|------|
| 状态成功 | 0.4 | Agent 状态是否为 COMPLETED |
| 输出存在 | 0.3 | 是否有最终输出 |
| 预期匹配 | 0.3 | 输出是否匹配预期值 |

通过标准：总分 >= 0.7

#### ToolExecutionEvaluator（工具执行评测器）

| 维度 | 权重 | 说明 |
|------|------|------|
| 成功率 | 0.5 | 工具调用成功率 |
| 预期工具 | 0.3 | 是否调用了预期的工具 |
| 执行时间 | 0.2 | 执行时间是否合理 |

通过标准：成功率 >= 0.8 且无缺失工具

### 7.4 使用评测服务

```java
@Service
@RequiredArgsConstructor
public class EvaluationExample {

    private final EvaluationService evaluationService;

    public void evaluateAgentExecution(String traceId) {
        // 构建评测请求
        EvaluationService.EvaluationRequest request = 
            new EvaluationService.EvaluationRequest(
                "预期输出内容",                    // expectedOutput
                List.of("calculate", "get_current_time"), // expectedTools
                Map.of("maxIterations", 5),       // constraints
                "auto"                            // mode
            );

        // 执行评测
        EvaluationService.FullEvaluationResult result = 
            evaluationService.evaluateFull(traceId, request);

        // 查看结果
        System.out.println("总分: " + result.overallScore());
        System.out.println("是否通过: " + (result.overallScore() >= 0.7));
        System.out.println("建议: " + result.recommendation());
        
        // 各评测器结果
        for (var evaluatorResult : result.evaluatorResults()) {
            System.out.println(evaluatorResult.evaluatorId() + ": " + evaluatorResult.score());
        }
    }

    public void batchEvaluation(List<String> traceIds) {
        // 批量评测
        List<EvaluationService.FullEvaluationResult> results = 
            evaluationService.evaluateBatch(traceIds);

        // 生成报告
        EvaluationService.EvaluationReport report = 
            evaluationService.generateReport(traceIds);
        
        System.out.println("平均分数: " + report.summary().get("avgOverallScore"));
        System.out.println("通过率: " + report.summary().get("passRate"));
    }
}
```

### 7.5 自定义评测器

```java
@Component
public class MyCustomEvaluator implements AgentEvaluator {

    @Override
    public String getName() {
        return "my_custom_evaluator";
    }

    @Override
    public String getDescription() {
        return "自定义评测器：检查响应质量";
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext context) {
        AgentTrace trace = context.trace();
        
        // 自定义评测逻辑
        double score = 0;
        List<String> issues = new ArrayList<>();
        
        // 示例：检查执行时间
        if (trace.getExecutionTimeMs() > 30000) {
            issues.add("执行时间过长");
            score += 0.3;
        } else {
            score += 0.5;
        }
        
        // 示例：检查迭代次数
        if (trace.getIterations() > 5) {
            issues.add("迭代次数过多");
            score += 0.2;
        } else {
            score += 0.5;
        }

        return new EvaluationResult(
            getName(),
            score >= 0.7,
            score,
            Map.of("executionTime", trace.getExecutionTimeMs()),
            issues,
            issues.isEmpty() ? "表现良好" : "需要优化"
        );
    }
}
```

### 7.6 API 接口

```bash
# 评测单个追踪
POST /api/admin/observability/evaluation/evaluate/{traceId}
Content-Type: application/json
{
    "expectedOutput": "预期输出",
    "expectedTools": ["calculate"],
    "constraints": {},
    "mode": "auto"
}

# 批量评测
POST /api/admin/observability/evaluation/batch
Content-Type: application/json
["traceId1", "traceId2"]

# 生成评测报告
POST /api/admin/observability/evaluation/report
Content-Type: application/json
["traceId1", "traceId2"]

# 获取评测器列表
GET /api/admin/observability/evaluation/evaluators
```

---

## 8. Prompt 版本管理

### 8.1 概述

Prompt 版本管理系统支持模板的版本控制、A/B 测试、灰度发布等功能。

### 8.2 核心功能

| 功能 | 方法 | 说明 |
|------|------|------|
| 创建模板 | `createTemplate()` | 创建新的 Prompt 模板 |
| 创建版本 | `createVersion()` | 基于现有模板创建新版本 |
| 激活版本 | `activateVersion()` | 激活指定版本 |
| 推送生产 | `promoteToProduction()` | 将版本推送到生产环境 |
| 回滚 | `rollback()` | 回滚到指定版本 |
| A/B 测试 | `configureABTest()` | 配置 A/B 测试 |
| 渲染 | `render()` | 渲染模板（变量替换） |

### 8.3 模板变量

模板使用 `{{variable}}` 语法定义变量：

```yaml
# react-agent.yaml
你是一个智能助手，使用 ReAct 模式解决问题。

可用工具：
{tools}

用户问题：{question}
{history}
```

### 8.4 使用 Prompt 服务

```java
@Service
@RequiredArgsConstructor
public class PromptExample {

    private final PromptVersionService promptService;

    public void managePrompts() {
        // 1. 创建模板
        PromptVersionService.CreatePromptRequest request = 
            new PromptVersionService.CreatePromptRequest(
                "my-assistant",           // name
                "1.0.0",                  // version
                "我的助手提示词",          // description
                "你是一个{{role}}，请帮助用户{{task}}", // content
                "assistant,helper",       // tags
                "admin"                   // createdBy
            );
        PromptTemplateEntity template = promptService.createTemplate(request);

        // 2. 创建新版本
        PromptTemplateEntity v2 = promptService.createVersion(
            "my-assistant",
            "你是一个专业的{{role}}，具有丰富的经验。请帮助用户{{task}}并提供详细解释。",
            "添加了专业性和详细解释要求"
        );

        // 3. 激活版本
        promptService.activateVersion("my-assistant", "1.1.0");

        // 4. 渲染模板
        String rendered = promptService.render("my-assistant", Map.of(
            "role", "数据分析师",
            "task", "分析销售数据"
        ));

        // 5. 配置 A/B 测试
        PromptVersionService.ABTestConfigRequest abTest = 
            new PromptVersionService.ABTestConfigRequest(
                "1.0.0",        // baselineVersion
                "1.1.0",        // variantVersion
                "enhanced",     // variantName
                30              // trafficPercentage (30% 流量使用变体)
            );
        promptService.configureABTest("my-assistant", abTest);

        // 6. 获取 A/B 测试版本
        PromptTemplateEntity abVersion = promptService.getABTestVersion("my-assistant", "user-001");

        // 7. 推送到生产
        promptService.promoteToProduction("my-assistant", "1.1.0");

        // 8. 回滚
        promptService.rollback("my-assistant", "1.0.0");

        // 9. 比较版本
        PromptVersionService.PromptDiff diff = promptService.compareVersions(
            "my-assistant", "1.0.0", "1.1.0"
        );
    }
}
```

### 8.5 API 接口

```bash
# 获取模板列表
GET /api/admin/observability/prompts

# 获取模板名称列表
GET /api/admin/observability/prompts/names

# 获取版本历史
GET /api/admin/observability/prompts/{name}/versions

# 创建模板
POST /api/admin/observability/prompts
Content-Type: application/json
{
    "name": "my-assistant",
    "version": "1.0.0",
    "description": "我的助手提示词",
    "content": "你是一个{{role}}，请帮助用户{{task}}",
    "tags": "assistant",
    "createdBy": "admin"
}

# 创建新版本
POST /api/admin/observability/prompts/{name}/versions
Content-Type: application/json
{
    "content": "新版本内容",
    "description": "变更说明"
}

# 激活版本
POST /api/admin/observability/prompts/{name}/versions/{version}/activate

# 推送到生产
POST /api/admin/observability/prompts/{name}/versions/{version}/promote

# 回滚版本
POST /api/admin/observability/prompts/{name}/rollback/{version}

# 配置 A/B 测试
POST /api/admin/observability/prompts/{name}/ab-test
Content-Type: application/json
{
    "baselineVersion": "1.0.0",
    "variantVersion": "1.1.0",
    "variantName": "enhanced",
    "trafficPercentage": 30
}

# 停止 A/B 测试
POST /api/admin/observability/prompts/{name}/ab-test/stop
```

---

## 9. 最佳实践

### 9.1 Agent 开发最佳实践

#### 1. 选择合适的 Agent 类型

| 任务特征 | 推荐 Agent | 原因 |
|---------|-----------|------|
| 需要多步推理、尝试不同方案 | ReActAgent | 自适应探索 |
| 任务步骤明确、可预定义 | PlanExecuteAgent | 高效执行 |
| 简单查询、单步操作 | Simple Executor | 快速响应 |

#### 2. 设计清晰的工具

```java
// ✅ 好的设计：单一职责、清晰描述
@AgentTool(
    name = "get_user_info",
    description = "根据用户ID获取用户基本信息"
)
public ToolResult getUserInfo(
    @ToolParam(name = "userId", description = "用户唯一标识符")
    String userId
) { ... }

// ❌ 不好的设计：职责模糊、描述不清晰
@AgentTool(name = "user_ops", description = "用户相关操作")
public ToolResult userOperations(String userId, String operation, Object data) { ... }
```

#### 3. 合理设置工具参数

```java
@AgentTool(name = "search_products", description = "搜索商品")
public ToolResult searchProducts(
    // 必需参数
    @ToolParam(name = "keyword", description = "搜索关键词")
    String keyword,
    
    // 可选参数（设置 required = false）
    @ToolParam(name = "category", description = "商品分类", required = false)
    String category,
    
    // 枚举参数（限制取值范围）
    @ToolParam(name = "sortBy", description = "排序方式", 
               enumValues = {"price", "sales", "rating"})
    String sortBy,
    
    // 数值参数
    @ToolParam(name = "limit", description = "返回数量（1-100）", required = false)
    Integer limit
) { ... }
```

#### 4. 返回结构化结果

```java
// ✅ 好的设计：返回结构化数据
return ToolResult.success(Map.of(
    "result", calculatedValue,
    "expression", expression,
    "timestamp", System.currentTimeMillis()
));

// ❌ 不好的设计：返回纯文本
return ToolResult.success("计算结果是: " + calculatedValue);
```

### 9.2 可观测性最佳实践

#### 1. 关键节点记录 Span

```java
public ReActResult execute(String question, TaskContext context) {
    String traceId = TraceContext.getCurrentTraceId();
    
    // 记录思考过程
    traceService.recordThought(traceId, "分析用户问题...");
    
    // 记录工具调用
    long start = System.currentTimeMillis();
    ToolResult result = toolRegistry.execute(toolName, params);
    traceService.recordToolCall(traceId, toolName, params, 
        String.valueOf(result.data()), 
        System.currentTimeMillis() - start,
        result.success(), result.error());
    
    // 记录 LLM 调用
    // ...
}
```

#### 2. 合理设置检查点

```java
// 在关键步骤保存检查点
for (int i = 0; i < steps.size(); i++) {
    Step step = steps.get(i);
    StepResult result = executeStep(step);
    
    // 每完成 3 个步骤保存一次检查点
    if (i > 0 && i % 3 == 0) {
        resumableAgent.saveCheckpoint(sessionId, traceId, state, i, steps.size());
    }
}
```

### 9.3 Prompt 设计最佳实践

#### 1. 使用模板变量

```java
// 定义模板
String template = """
    你是一个{{role}}，请帮助用户解决问题。
    
    遵循以下规则：
    1. {{rule1}}
    2. {{rule2}}
    
    用户问题：{{question}}
    """;

// 渲染模板
String prompt = promptService.render("my-agent", Map.of(
    "role", "数据分析专家",
    "rule1", "先分析数据结构",
    "rule2", "给出具体建议",
    "question", userQuestion
));
```

#### 2. 版本管理

```
开发环境（active=true, production=false）
    ↓ 测试通过
预发布环境（active=true, production=false, A/B 测试）
    ↓ 灰度验证
生产环境（active=true, production=true）
```

### 9.4 评测最佳实践

#### 1. 定义明确的评测标准

```java
// 任务完成度评测
- 状态是否为 COMPLETED
- 输出是否存在
- 输出是否匹配预期

// 工具执行评测
- 工具调用成功率 >= 80%
- 执行时间在合理范围
- 调用了预期的工具
```

#### 2. 定期生成评测报告

```java
@Scheduled(cron = "0 0 * * * ?")  // 每小时执行
public void generateHourlyReport() {
    List<String> recentTraceIds = traceService.getRecentTraces(100)
        .stream().map(AgentTrace::getTraceId).toList();
    
    EvaluationService.EvaluationReport report = 
        evaluationService.generateReport(recentTraceIds);
    
    // 发送报告或存储
    reportStorage.save(report);
}
```

---

## 10. 常见问题

### Q1: 如何添加新的 Agent 类型？

**A:** 实现新 Agent 类并注册到 AgentOrchestrator：

```java
@Component
public class MyCustomAgent {
    
    public MyResult execute(String goal, TaskContext context) {
        // 实现执行逻辑
    }
}

// 在 AgentOrchestrator 中添加路由
public TaskResult executeCustom(String goal, TaskContext context) {
    return myCustomAgent.execute(goal, context);
}
```

### Q2: 工具执行超时怎么办？

**A:** 调整工具的超时配置：

```java
@AgentTool(
    name = "slow_operation",
    description = "耗时操作",
    timeoutMs = 60000,  // 60 秒超时
    maxRetries = 3      // 重试 3 次
)
public ToolResult slowOperation() { ... }
```

### Q3: 如何调试 Agent 执行过程？

**A:** 查看追踪数据：

```bash
# 获取追踪详情
GET /api/admin/observability/traces/{traceId}

# 查看 Span 列表
GET /api/admin/observability/traces/{traceId}/spans
```

### Q4: 如何实现工具的权限控制？

**A:** 在工具定义中声明所需权限：

```java
@AgentTool(
    name = "delete_user",
    description = "删除用户",
    requiredPermissions = {"user:delete"}
)
public ToolResult deleteUser(String userId) { ... }

// 执行前检查权限
List<String> userPermissions = getUserPermissions(userId);
List<ToolDefinition> availableTools = toolRegistry.getToolsByPermissions(userPermissions);
```

### Q5: Agent 执行中断后如何恢复？

**A:** 使用 ResumableReActAgent：

```java
// 检查是否有可恢复的快照
if (resumableAgent.canResume(sessionId)) {
    Optional<AgentStateSnapshot> snapshot = resumableAgent.getResumableSnapshot(sessionId);
    if (snapshot.isPresent()) {
        return resumableAgent.resume(snapshot.get(), question, context);
    }
}
```

### Q6: 如何优化 Agent 的响应速度？

**A:** 多种优化策略：

1. **减少迭代次数**：优化 Prompt，让 Agent 更快得出结论
2. **并行工具调用**：对于独立的工具调用，可以并行执行
3. **缓存常用结果**：对于频繁查询的数据，使用 Redis 缓存
4. **选择更快的模型**：对于简单任务，使用响应更快的模型

### Q7: 如何监控 Agent 的性能？

**A:** 使用追踪统计和评测报告：

```java
// 获取追踪统计
TraceStatistics stats = traceService.getStatistics();
System.out.println("总执行数: " + stats.total());
System.out.println("成功率: " + stats.successRate());

// 生成评测报告
EvaluationReport report = evaluationService.generateReport(traceIds);
System.out.println("平均分数: " + report.summary().get("avgOverallScore"));
```

---

## 附录

### A. 相关文件路径

| 组件 | 文件路径 |
|------|---------|
| ReActAgent | `src/main/java/com/jonychen/planning/agent/ReActAgent.java` |
| PlanExecuteAgent | `src/main/java/com/jonychen/planning/agent/PlanExecuteAgent.java` |
| AgentOrchestrator | `src/main/java/com/jonychen/planning/AgentOrchestrator.java` |
| ToolRegistry | `src/main/java/com/jonychen/tool/ToolRegistry.java` |
| DefaultToolRegistry | `src/main/java/com/jonychen/tool/DefaultToolRegistry.java` |
| AgentTraceService | `src/main/java/com/jonychen/observability/trace/AgentTraceService.java` |
| AgentStateService | `src/main/java/com/jonychen/observability/state/AgentStateService.java` |
| EvaluationService | `src/main/java/com/jonychen/observability/evaluation/EvaluationService.java` |
| PromptVersionService | `src/main/java/com/jonychen/observability/prompt/PromptVersionService.java` |
| CalculatorTools | `src/main/java/com/jonychen/tool/builtin/CalculatorTools.java` |
| DateTimeTools | `src/main/java/com/jonychen/tool/builtin/DateTimeTools.java` |

### B. 数据库表结构

| 表名 | 说明 |
|------|------|
| `agent_traces` | Agent 执行追踪记录 |
| `agent_trace_spans` | Agent 执行步骤详情 |
| `prompt_templates` | Prompt 模板版本管理 |
| `agent_state_snapshots` | Agent 状态快照 |
| `evaluation_results` | Agent 评测结果 |
| `tool_execution_audit` | 工具执行审计记录 |

### C. 参考资料

- [LangChain4j 官方文档](https://docs.langchain4j.dev/)
- [ReAct 论文](https://arxiv.org/abs/2210.03629)
- [Plan-and-Solve 论文](https://arxiv.org/abs/2305.04091)
- [Agent 可观测性技术实现方案](./Agent可观测性技术实现方案.md)