# Agent 开发指南

> 本文档指导开发者如何在本项目中创建和扩展 Agent。

---

## 1. 概述

本项目采用基于 LangChain4j 的多 Agent 架构，支持智能路由、工具调用、流式执行等能力。本文档将指导你如何开发新的 Agent。

### 1.1 架构层次

```
┌─────────────────────────────────────────────────────────────┐
│                    AgentExecutionController                  │
│                    (REST API + SSE 端点)                      │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                     AgentOrchestrator                        │
│                   (协调器 + 会话管理)                          │
└─────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
        ┌──────────┐    ┌──────────┐    ┌──────────┐
        │RouterAgent│    │ OpsAgent │    │DataAgent │ ...
        └──────────┘    └──────────┘    └──────────┘
              │               │               │
              └───────────────┼───────────────┘
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      ToolRegistry                            │
│                    (工具注册与执行)                            │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 核心概念

| 概念 | 说明 |
|------|------|
| **Agent** | 自主执行单元，接收用户请求并通过工具完成任务 |
| **Tool** | Agent 可调用的能力单元，如数据库查询、API 调用等 |
| **Router** | 智能路由器，根据用户意图选择合适的 Agent |
| **Context** | 执行上下文，包含会话状态、追踪信息等 |
| **Event** | SSE 事件，用于实时推送执行进度到前端 |

---

## 2. 快速开始：创建一个新 Agent

### 2.1 最简 Agent 示例

```java
package com.jonychen.agent.impl;

import com.jonychen.agent.core.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Set;

/**
 * 自定义 Agent 示例
 */
public class MyCustomAgent extends AbstractAgent {

    public MyCustomAgent(
            ChatLanguageModel chatModel,
            ToolRegistry toolRegistry,
            AgentTraceService traceService) {
        super(chatModel, toolRegistry, traceService);
    }

    @Override
    public AgentMetadata getMetadata() {
        return new AgentMetadata(
            "my-custom",                    // Agent 名称
            AgentType.CUSTOM,               // Agent 类型
            "自定义助手",                     // 显示名称
            "执行自定义任务",                 // 功能描述
            "1.0.0",                        // 版本号
            Set.of("custom:execute"),       // 能力标签
            Set.of("USER"),                 // 所需权限
            5,                              // 最大迭代次数
            Duration.ofMinutes(3),          // 执行超时
            true                            // 支持流式
        );
    }

    @Override
    protected String buildSystemPrompt() {
        return """
            你是一个自定义助手，负责执行特定任务。

            ## 工作流程
            1. 理解用户需求
            2. 调用工具执行任务
            3. 返回执行结果

            ## 输出格式
            使用清晰、简洁的语言回答。
            """;
    }

    @Override
    protected AgentExecutor buildExecutor(AgentContext context) {
        // 注册工具（如果有）
        return new AgentExecutor(
            chatModel,
            buildSystemPrompt(),
            List.of(),  // 工具列表
            Map.of()    // 工具执行器映射
        );
    }
}
```

### 2.2 注册 Agent

```java
@Configuration
public class AgentConfig {

    @Bean
    public MyCustomAgent myCustomAgent(
            LoadBalancedChatModel chatModel,
            ToolRegistry toolRegistry,
            AgentTraceService traceService) {
        return new MyCustomAgent(chatModel, toolRegistry, traceService);
    }

    @Bean
    public AgentRegistry agentRegistry(
            MyCustomAgent myCustomAgent,
            // ... 其他 Agent
            ) {
        AgentRegistry registry = new AgentRegistry();
        registry.register(myCustomAgent);
        // ... 注册其他 Agent
        return registry;
    }
}
```

---

## 3. Agent 核心接口详解

### 3.1 Agent 接口

```java
public interface Agent {

    /**
     * 获取 Agent 元信息
     * 必须实现，用于注册和路由决策
     */
    AgentMetadata getMetadata();

    /**
     * 同步执行（简单场景）
     */
    AgentResult execute(AgentRequest request, AgentContext context);

    /**
     * 流式执行（推荐）
     * 返回 Flux<AgentEvent>，支持实时推送
     */
    Flux<AgentEvent> executeStream(AgentRequest request, AgentContext context);

    /**
     * 判断是否支持该任务
     * 返回 0-1 置信度分数，用于 Router 决策
     */
    default double canHandle(AgentRequest request) {
        return 0.0;
    }

    /**
     * 获取可用工具列表
     */
    default List<ToolDefinition> getAvailableTools() {
        return Collections.emptyList();
    }
}
```

### 3.2 AgentMetadata

```java
public record AgentMetadata(
    String name,                    // Agent 名称标识（唯一）
    AgentType agentType,            // Agent 类型枚举
    String displayName,             // 显示名称
    String description,             // 功能描述
    String version,                 // 版本号
    Set<String> capabilities,       // 能力标签
    Set<String> requiredPermissions,// 需要的权限
    int maxIterations,              // 最大迭代次数
    Duration timeout,               // 执行超时
    boolean supportsStreaming       // 是否支持流式
) {
    // 工厂方法示例
    public static AgentMetadata ops() {
        return new AgentMetadata(
            "ops", AgentType.OPS, "运维助手",
            "智能运维：模型诊断与故障处置",
            "1.0.0",
            Set.of("model:read", "model:write"),
            Set.of("ADMIN"),
            10, Duration.ofMinutes(5), true
        );
    }
}
```

### 3.3 AgentRequest 和 AgentContext

```java
/**
 * Agent 执行请求
 */
public record AgentRequest(
    String sessionId,           // 会话 ID
    String userId,              // 用户 ID
    String userInput,           // 用户输入
    Map<String, Object> params, // 额外参数
    AgentRequestOptions options,// 执行选项
    String clientIp,            // 客户端 IP
    String userAgent            // 客户端 UA
) {}

/**
 * Agent 执行选项
 */
public record AgentRequestOptions(
    int maxIterations,          // 最大迭代次数
    Duration timeout,           // 超时时间
    boolean requireConfirmation,// 敏感操作是否需要确认
    boolean debugMode           // 调试模式
) {}

/**
 * Agent 执行上下文
 */
public class AgentContext {
    private final String traceId;        // 追踪 ID
    private final String sessionId;      // 会话 ID
    private final String userId;         // 用户 ID
    private final ToolRegistry toolRegistry;
    private final AgentTraceService traceService;
    private final List<AgentEvent> events;
    private volatile boolean cancelled;  // 取消标志

    // 确认等待机制
    private final ConcurrentHashMap<String, CompletableFuture<Boolean>> pendingConfirmations;

    /**
     * 取消执行
     */
    public void cancel() {
        this.cancelled = true;
    }

    /**
     * 等待用户确认
     */
    public CompletableFuture<Boolean> awaitConfirmation(String confirmationId) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        pendingConfirmations.put(confirmationId, future);
        return future;
    }

    /**
     * 完成确认
     */
    public void resolveConfirmation(String confirmationId, boolean approved) {
        CompletableFuture<Boolean> future = pendingConfirmations.remove(confirmationId);
        if (future != null && !future.isDone()) {
            future.complete(approved);
        }
    }
}
```

---

## 4. 开发工具（Tool）

### 4.1 工具定义

```java
package com.jonychen.tool;

import java.util.Map;
import java.util.Set;

/**
 * 工具定义
 */
public record ToolDefinition(
    String name,                    // 工具名称
    String description,             // 工具描述
    Map<String, ParamDefinition> params,  // 参数定义
    boolean requiresConfirmation,   // 是否需要确认
    RiskLevel riskLevel,            // 风险级别
    Set<String> requiredPermissions // 所需权限
) {
    /**
     * 参数定义
     */
    public record ParamDefinition(
        String name,
        String description,
        String type,        // string, number, boolean, object, array
        boolean required,
        Object defaultValue
    ) {}
}

/**
 * 风险级别
 */
public enum RiskLevel {
    LOW,        // 低风险：只读操作
    MEDIUM,     // 中风险：可能影响用户体验
    HIGH,       // 高风险：可能导致数据变更
    CRITICAL    // 严重风险：不可逆操作
}
```

### 4.2 创建工具类

```java
package com.jonychen.tool.impl;

import com.jonychen.tool.*;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 自定义工具示例
 */
@Component
public class CustomTools {

    private final ToolRegistry toolRegistry;

    public CustomTools(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
        registerTools();
    }

    private void registerTools() {
        // 注册工具
        toolRegistry.register(ToolDefinition.builder()
            .name("execute_custom_task")
            .description("执行自定义任务")
            .param("taskType", "string", "任务类型", true)
            .param("params", "object", "任务参数", false)
            .requiresConfirmation(false)
            .riskLevel(RiskLevel.LOW)
            .requiredPermissions(Set.of("custom:execute"))
            .executor(this::executeCustomTask)
            .build());
    }

    /**
     * 工具执行方法
     */
    private ToolResult executeCustomTask(
            Map<String, Object> params,
            ToolExecutionContext context) {

        String taskType = (String) params.get("taskType");

        try {
            // 执行任务逻辑
            Object result = doExecute(taskType, params);

            return ToolResult.success(result);
        } catch (Exception e) {
            return ToolResult.failure(e.getMessage());
        }
    }

    private Object doExecute(String taskType, Map<String, Object> params) {
        // 具体实现
        return Map.of("status", "completed", "taskType", taskType);
    }
}
```

### 4.3 敏感操作工具

```java
/**
 * 需要确认的敏感工具
 */
@Component
public class SensitiveTools {

    private final ToolRegistry toolRegistry;

    public SensitiveTools(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
        registerTools();
    }

    private void registerTools() {
        toolRegistry.register(ToolDefinition.builder()
            .name("delete_data")
            .description("删除数据")
            .param("tableName", "string", "表名", true)
            .param("condition", "string", "删除条件", true)
            .requiresConfirmation(true)  // 需要确认
            .riskLevel(RiskLevel.CRITICAL)  // 严重风险
            .requiredPermissions(Set.of("data:delete"))
            .executor(this::deleteData)
            .build());
    }

    private ToolResult deleteData(
            Map<String, Object> params,
            ToolExecutionContext context) {

        // 权限校验
        if (!context.permissions().contains("data:delete")) {
            return ToolResult.failure("权限不足");
        }

        // 执行删除（用户确认后才会到达这里）
        String tableName = (String) params.get("tableName");
        String condition = (String) params.get("condition");

        // ... 执行删除逻辑

        return ToolResult.success(Map.of("deleted", true));
    }
}
```

---

## 5. 事件与流式输出

### 5.1 事件类型

```java
/**
 * Agent 事件类型
 */
public sealed interface AgentEvent permits
    AgentEvent.StepStart,
    AgentEvent.StepEnd,
    AgentEvent.Thought,
    AgentEvent.ToolCall,
    AgentEvent.ToolResult,
    AgentEvent.ConfirmationRequired,
    AgentEvent.AgentDone,
    AgentEvent.AgentError,
    AgentEvent.Heartbeat {

    String traceId();
    int sequenceNumber();
    Instant timestamp();
    String eventType();
}
```

### 5.2 发送事件

```java
@Override
protected void executeWithSink(
        AgentRequest request,
        AgentContext context,
        FluxSink<AgentEvent> sink) {

    String traceId = context.getTraceId();
    AtomicInteger seq = context.getSequenceCounter();

    // 发送步骤开始事件
    emit(sink, AgentEvent.stepStart(
        traceId, seq.getAndIncrement(),
        0, StepType.LLM_CALL, "my-agent"
    ));

    // 发送思考过程
    emit(sink, AgentEvent.thought(
        traceId, seq.getAndIncrement(),
        0, "正在分析用户需求..."
    ));

    // 发送工具调用事件
    emit(sink, AgentEvent.toolCall(
        traceId, seq.getAndIncrement(),
        0, "execute_custom_task", Map.of("taskType", "analysis")
    ));

    // 发送工具结果
    emit(sink, AgentEvent.toolResult(
        traceId, seq.getAndIncrement(),
        0, "execute_custom_task",
        Map.of("status", "completed"),
        true, null
    ));

    // 发送完成事件
    emit(sink, AgentEvent.done(
        traceId, seq.getAndIncrement(),
        "my-agent", "任务执行完成",
        1, new TokenUsage(100, 50), 1000
    ));
}

private void emit(FluxSink<AgentEvent> sink, AgentEvent event) {
    sink.next(event);
}
```

### 5.3 前端接收事件

```typescript
// frontend/src/api/agent.ts
export interface AgentEvent {
  eventType: string;
  traceId: string;
  sequenceNumber: number;
  timestamp: string;
  // ... 其他字段根据事件类型而定
}

export function executeAgent(request: AgentRequest): EventSource {
  const url = `/api/agent/execute?input=${encodeURIComponent(request.userInput)}`;
  return new EventSource(url);
}

// 使用示例
const eventSource = executeAgent({ userInput: '查询用户数据' });

eventSource.addEventListener('step_start', (event) => {
  const data = JSON.parse(event.data);
  console.log('Step started:', data);
});

eventSource.addEventListener('tool_call', (event) => {
  const data = JSON.parse(event.data);
  console.log('Tool calling:', data.toolName);
});

eventSource.addEventListener('agent_done', (event) => {
  const data = JSON.parse(event.data);
  console.log('Agent finished:', data.output);
  eventSource.close();
});

eventSource.addEventListener('agent_error', (event) => {
  const data = JSON.parse(event.data);
  console.error('Agent error:', data.message);
  eventSource.close();
});
```

---

## 6. 追踪与可观测性

### 6.1 自动追踪

通过 `AgentTraceAspect` 自动追踪 Agent 执行：

```java
@Around("execution(* com.jonychen.agent.impl.*.execute(..))")
public Object traceAgentExecution(ProceedingJoinPoint joinPoint) throws Throwable {
    // 自动记录开始、结束、Token 使用等
}
```

### 6.2 手动记录

```java
@Override
protected void executeWithSink(AgentRequest request, AgentContext context, FluxSink<AgentEvent> sink) {
    // 开始追踪
    AgentTrace trace = traceService.startTrace(
        context.getSessionId(),
        context.getUserId(),
        getMetadata().name(),
        request.userInput()
    );

    try {
        // 记录思考过程
        traceService.recordThought(trace.getTraceId(), "分析用户需求...");

        // 记录工具调用
        traceService.recordToolCall(
            trace.getTraceId(),
            "execute_custom_task",
            params,
            result,
            durationMs,
            true,
            null
        );

        // 结束追踪（成功）
        traceService.endTraceSuccess(trace.getTraceId(), output, tokenUsage);

    } catch (Exception e) {
        // 结束追踪（失败）
        traceService.endTraceFailed(trace.getTraceId(), e.getMessage());
        throw e;
    }
}
```

### 6.3 查看追踪数据

通过管理后台查看：
- `/admin/traces` - 追踪列表
- `/admin/traces/{traceId}` - 追踪详情
- `/admin/traces/{traceId}/spans` - 执行步骤

---

## 7. 测试 Agent

### 7.1 单元测试

```java
@SpringBootTest
class MyCustomAgentTest {

    @Autowired
    private MyCustomAgent agent;

    @Mock
    private ToolRegistry toolRegistry;

    @Mock
    private AgentTraceService traceService;

    @Test
    void testExecute() {
        // 准备请求
        AgentRequest request = AgentRequest.of("执行测试任务", "test-user");
        AgentContext context = new AgentContext(
            "trace-123", "session-123", "test-user",
            toolRegistry, null, traceService,
            AgentRequestOptions.defaults()
        );

        // 执行
        AgentResult result = agent.execute(request, context);

        // 验证
        assertThat(result.status()).isEqualTo(AgentStatus.SUCCESS);
        assertThat(result.output()).isNotEmpty();
    }

    @Test
    void testCanHandle() {
        AgentRequest request = AgentRequest.of("执行自定义任务", "test-user");
        double confidence = agent.canHandle(request);
        assertThat(confidence).isGreaterThan(0.5);
    }
}
```

### 7.2 集成测试

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AgentIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void testAgentExecutionViaAPI() {
        webTestClient.post()
            .uri("/api/agent/execute")
            .header("Authorization", "Bearer " + getTestToken())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "userInput", "执行测试任务",
                "sessionId", "test-session"
            ))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.traceId").exists()
            .jsonPath("$.status").isEqualTo("SUCCESS");
    }
}
```

---

## 8. 最佳实践

### 8.1 系统提示词设计

```java
@Override
protected String buildSystemPrompt() {
    return """
        你是一个[具体角色]，负责[具体任务]。

        ## 可用工具
        - tool1: 工具1描述
        - tool2: 工具2描述

        ## 工作流程
        1. 步骤1
        2. 步骤2
        3. 步骤3

        ## 安全约束
        - 约束1
        - 约束2

        ## 输出格式
        具体格式说明
        """;
}
```

### 8.2 错误处理

```java
@Override
protected void executeWithSink(AgentRequest request, AgentContext context, FluxSink<AgentEvent> sink) {
    try {
        // 执行逻辑
    } catch (TimeoutException e) {
        emit(sink, new AgentEvent.AgentError(
            traceId, seq.getAndIncrement(), Instant.now(),
            "TIMEOUT", "执行超时", null, true
        ));
    } catch (CancellationException e) {
        emit(sink, new AgentEvent.AgentError(
            traceId, seq.getAndIncrement(), Instant.now(),
            "CANCELLED", "执行已取消", null, false
        ));
    } catch (Exception e) {
        emit(sink, new AgentEvent.AgentError(
            traceId, seq.getAndIncrement(), Instant.now(),
            "EXECUTION_ERROR", e.getMessage(), null, true
        ));
    }
}
```

### 8.3 权限控制

```java
@Override
public AgentMetadata getMetadata() {
    return new AgentMetadata(
        // ...
        Set.of("admin:access"),  // 需要 ADMIN 权限
        // ...
    );
}

// 工具层也需校验
private ToolResult executeSensitiveOperation(
        Map<String, Object> params,
        ToolExecutionContext context) {

    if (!context.roles().contains("ADMIN")) {
        return ToolResult.failure("权限不足：需要 ADMIN 角色");
    }

    // 执行操作
}
```

### 8.4 性能优化

```java
// 1. 缓存系统提示词
private volatile String cachedSystemPrompt;

@Override
protected String buildSystemPrompt() {
    if (cachedSystemPrompt == null) {
        cachedSystemPrompt = buildPromptInternal();
    }
    return cachedSystemPrompt;
}

// 2. 使用滑动窗口限制历史消息
private static final int MAX_HISTORY = 20;

// 3. 异步执行耗时操作
private CompletableFuture<Object> asyncExecute(String task) {
    return CompletableFuture.supplyAsync(() -> {
        // 耗时操作
    });
}
```

---

## 9. 故障排查

### 9.1 常见问题

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| Agent 未被路由到 | `canHandle()` 返回值过低 | 提高关键词匹配置信度 |
| 工具调用失败 | 参数格式错误或权限不足 | 检查参数类型和用户权限 |
| 执行超时 | 任务耗时过长 | 增加超时时间或优化逻辑 |
| SSE 断线 | 网络问题或服务器超时 | 前端实现自动重连 |

### 9.2 调试技巧

```java
// 启用调试模式
AgentRequestOptions options = new AgentRequestOptions(
    10, Duration.ofMinutes(5), true, true  // debugMode = true
);

// 在日志中查看详细执行过程
logging.level.com.jonychen.agent=DEBUG
```

---

## 10. 参考资源

- [Agent 可观测性技术实现方案](./Agent可观测性技术实现方案.md)
- [多 Agent 系统设计](./multi-agent/multi-agent-system-design.md)
- [工具系统设计](./multi-agent/tool-system.md)
- [LangChain4j 官方文档](https://docs.langchain4j.dev/)
