# Agent 框架设计

> 多 Agent 生产级系统技术方案 - 子文档

---

## 1. Agent 抽象层

### 1.1 核心接口

```java
package com.jonychen.agent.core;

import reactor.core.publisher.Flux;

/**
 * Agent 基础接口
 * 
 * 所有业务 Agent 需实现此接口，提供同步和流式两种执行模式。
 */
public interface Agent {
    
    /**
     * 获取 Agent 元信息
     */
    AgentMetadata getMetadata();
    
    /**
     * 同步执行（适用于简单场景）
     * 
     * @param request 执行请求
     * @param context 执行上下文
     * @return 执行结果
     */
    AgentResult execute(AgentRequest request, AgentContext context);
    
    /**
     * 流式执行（推荐）
     * 
     * 返回 Flux<AgentEvent>，支持实时推送执行步骤到前端。
     * 
     * @param request 执行请求
     * @param context 执行上下文
     * @return 事件流
     */
    Flux<AgentEvent> executeStream(AgentRequest request, AgentContext context);
    
    /**
     * 是否支持该任务
     * 
     * @param request 执行请求
     * @return 0-1 置信度分数
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

### 1.2 核心数据结构

```java
package com.jonychen.agent.core;

/**
 * Agent 执行请求
 */
public record AgentRequest(
    String sessionId,           // 会话 ID
    String userId,              // 用户 ID
    String userInput,           // 用户输入
    Map<String, Object> params, // 额外参数
    AgentRequestOptions options,// 执行选项
    String clientIp,            // 客户端 IP（用于审计日志，避免异步线程依赖 HttpServletRequest）
    String userAgent            // 客户端 UA（用于审计日志）
) {
    public static AgentRequest of(String userInput, String userId) {
        return new AgentRequest(
            UUID.randomUUID().toString(),
            userId,
            userInput,
            Collections.emptyMap(),
            AgentRequestOptions.defaults(),
            null,
            null
        );
    }

    public static AgentRequest of(String userInput, String userId, String clientIp, String userAgent) {
        return new AgentRequest(
            UUID.randomUUID().toString(),
            userId,
            userInput,
            Collections.emptyMap(),
            AgentRequestOptions.defaults(),
            clientIp,
            userAgent
        );
    }
}

/**
 * Agent 执行选项
 */
public record AgentRequestOptions(
    int maxIterations,          // 最大迭代次数
    Duration timeout,           // 超时时间
    boolean requireConfirmation,// 敏感操作是否需要确认
    boolean debugMode           // 调试模式（输出更多信息）
) {
    public static AgentRequestOptions defaults() {
        return new AgentRequestOptions(
            10,
            Duration.ofMinutes(5),
            true,
            false
        );
    }
}

/**
 * Agent 执行上下文
 */
public class AgentContext {
    private final String traceId;
    private final String sessionId;
    private final String userId;
    private final AgentType agentType;
    private final ToolRegistry toolRegistry;
    private final ChatMemory chatMemory;
    private final AgentTraceService traceService;
    private final AgentRequestOptions options;
    private final Map<String, Object> variables;
    private final List<AgentEvent> events;
    private volatile boolean cancelled;
    private final AtomicInteger sequenceCounter; // 全局事件序号（由 Orchestrator 初始化，确保跨组件序号连续）
    
    // 确认等待机制（统一管理入口，ToolRegistry 不再自行管理确认生命周期）
    // 生产环境建议外置到 Redis
    private final ConcurrentHashMap<String, CompletableFuture<Boolean>> pendingConfirmations = new ConcurrentHashMap<>();
    
    // 构造器、getter 省略...
    
    public AgentRequestOptions getOptions() {
        return options;
    }
    
    public AtomicInteger getSequenceCounter() {
        return sequenceCounter;
    }
    
    /**
     * 记录事件（用于 SSE 推送）
     */
    public void emitEvent(AgentEvent event) {
        events.add(event);
    }
    
    /**
     * 取消执行
     */
    public void cancel() {
        this.cancelled = true;
        // 取消所有等待中的确认
        pendingConfirmations.values().forEach(f -> f.completeExceptionally(new CancellationException("执行已取消")));
    }
    
    /**
     * 检查是否已取消
     */
    public boolean isCancelled() {
        return cancelled;
    }
    
    /**
     * 注册等待中的确认
     */
    public CompletableFuture<Boolean> awaitConfirmation(String confirmationId) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        pendingConfirmations.put(confirmationId, future);
        return future;
    }
    
    /**
     * 完成确认（由 AgentOrchestrator 在用户确认后调用）
     */
    public void resolveConfirmation(String confirmationId, boolean approved) {
        CompletableFuture<Boolean> future = pendingConfirmations.remove(confirmationId);
        if (future != null && !future.isDone()) {
            future.complete(approved);
        }
    }
}

/**
 * Agent 执行结果
 */
public record AgentResult(
    String traceId,
    AgentStatus status,         // SUCCESS, FAILED, CANCELLED, TIMEOUT
    String output,              // 最终输出
    List<AgentStep> steps,      // 执行步骤
    TokenUsage tokenUsage,      // Token 使用量
    long durationMs,            // 执行耗时
    String errorMessage         // 错误信息（如果有）
) {
    public static AgentResult success(String traceId, String output, List<AgentStep> steps) {
        return new AgentResult(traceId, AgentStatus.SUCCESS, output, steps, null, 0, null);
    }
    
    public static AgentResult failure(String traceId, String errorMessage) {
        return new AgentResult(traceId, AgentStatus.FAILED, null, null, null, 0, errorMessage);
    }
}

/**
 * Agent 执行步骤
 */
public record AgentStep(
    int stepIndex,
    StepType type,              // THOUGHT, TOOL_CALL, TOOL_RESULT, LLM_CALL, AGENT_CALL
    String content,             // 步骤内容
    String toolName,            // 工具名称（如果是工具调用）
    Map<String, Object> toolInput,  // 工具输入
    Object toolOutput,          // 工具输出
    boolean success,            // 是否成功
    String error,               // 错误信息
    long durationMs             // 耗时
) {}

/**
 * Agent 状态枚举
 */
public enum AgentStatus {
    RUNNING, SUCCESS, FAILED, CANCELLED, TIMEOUT, PENDING_CONFIRMATION
}

/**
 * Token 使用量
 */
public record TokenUsage(
    long promptTokens,          // 输入 Token 数
    long completionTokens,      // 输出 Token 数
    long totalTokens            // 总 Token 数
) {}

/**
 * 步骤类型枚举
 */
public enum StepType {
    THOUGHT,        // 思考过程
    TOOL_CALL,      // 工具调用
    TOOL_RESULT,    // 工具结果
    LLM_CALL,       // LLM 调用
    AGENT_CALL      // 委托其他 Agent
}
```

### 1.3 Agent 事件（用于 SSE）

```java
package com.jonychen.agent.core;

import java.time.Instant;

/**
 * Agent 事件基类
 * 
 * 所有事件都包含 traceId 和 timestamp，用于前端展示和追踪。
 */
public sealed interface AgentEvent permits 
    AgentEvent.StepStart,
    AgentEvent.StepEnd,
    AgentEvent.Thought,
    AgentEvent.ToolCall,
    AgentEvent.ToolResult,
    AgentEvent.AgentCall,
    AgentEvent.AgentResult,
    AgentEvent.ConfirmationRequired,
    AgentEvent.AgentDone,
    AgentEvent.AgentError,
    AgentEvent.Heartbeat {
    
    String traceId();
    int sequenceNumber();    // 事件序号（全局递增，前端据此检测丢事件）
    Instant timestamp();
    String eventType();
    
    // ===== 事件实现 =====
    
    record StepStart(
        String traceId,
        int sequenceNumber,
        Instant timestamp,
        int stepIndex,
        StepType type,
        String agentName
    ) implements AgentEvent {
        @Override public String eventType() { return "step_start"; }
    }
    
    record StepEnd(
        String traceId,
        int sequenceNumber,
        Instant timestamp,
        int stepIndex,
        boolean success,
        String summary
    ) implements AgentEvent {
        @Override public String eventType() { return "step_end"; }
    }
    
    record Thought(
        String traceId,
        int sequenceNumber,
        Instant timestamp,
        int stepIndex,
        String content
    ) implements AgentEvent {
        @Override public String eventType() { return "thought"; }
    }
    
    record ToolCall(
        String traceId,
        int sequenceNumber,
        Instant timestamp,
        int stepIndex,
        String toolName,
        Map<String, Object> params
    ) implements AgentEvent {
        @Override public String eventType() { return "tool_call"; }
    }
    
    record ToolResult(
        String traceId,
        int sequenceNumber,
        Instant timestamp,
        int stepIndex,
        String toolName,
        Object result,
        boolean success,
        String error
    ) implements AgentEvent {
        @Override public String eventType() { return "tool_result"; }
    }
    
    record AgentCall(
        String traceId,
        int sequenceNumber,
        Instant timestamp,
        int stepIndex,
        String targetAgent,
        String input
    ) implements AgentEvent {
        @Override public String eventType() { return "agent_call"; }
    }
    
    record AgentResult(
        String traceId,
        int sequenceNumber,
        Instant timestamp,
        int stepIndex,
        String agentName,
        String output,
        boolean success
    ) implements AgentEvent {
        @Override public String eventType() { return "agent_result"; }
    }
    
    record ConfirmationRequired(
        String traceId,
        int sequenceNumber,
        Instant timestamp,
        int stepIndex,
        String confirmationId,
        String operation,
        String description,
        RiskLevel riskLevel
    ) implements AgentEvent {
        @Override public String eventType() { return "confirmation_required"; }
    }
    
    record AgentDone(
        String traceId,
        int sequenceNumber,
        Instant timestamp,
        String agentName,
        String output,
        int totalSteps,
        TokenUsage tokenUsage,
        long durationMs
    ) implements AgentEvent {
        @Override public String eventType() { return "agent_done"; }
    }
    
    record AgentError(
        String traceId,
        int sequenceNumber,
        Instant timestamp,
        String errorCode,
        String message,
        String details,
        boolean recoverable
    ) implements AgentEvent {
        @Override public String eventType() { return "agent_error"; }
    }
    
    record Heartbeat(
        String traceId,
        int sequenceNumber,
        Instant timestamp
    ) implements AgentEvent {
        @Override public String eventType() { return "heartbeat"; }
    }
    
    // ===== 工厂方法 =====
    
    static StepStart stepStart(String traceId, int sequenceNumber, int stepIndex, StepType type, String agentName) {
        return new StepStart(traceId, sequenceNumber, Instant.now(), stepIndex, type, agentName);
    }
    
    static Thought thought(String traceId, int sequenceNumber, int stepIndex, String content) {
        return new Thought(traceId, sequenceNumber, Instant.now(), stepIndex, content);
    }
    
    static ToolCall toolCall(String traceId, int sequenceNumber, int stepIndex, String toolName, Map<String, Object> params) {
        return new ToolCall(traceId, sequenceNumber, Instant.now(), stepIndex, toolName, params);
    }
    
    static ToolResult toolResult(String traceId, int sequenceNumber, int stepIndex, String toolName, Object result, boolean success, String error) {
        return new ToolResult(traceId, sequenceNumber, Instant.now(), stepIndex, toolName, result, success, error);
    }
    
    static AgentDone done(String traceId, int sequenceNumber, String agentName, String output, int totalSteps, TokenUsage tokenUsage, long durationMs) {
        return new AgentDone(traceId, sequenceNumber, Instant.now(), agentName, output, totalSteps, tokenUsage, durationMs);
    }
    
    static Heartbeat heartbeat(String traceId, int sequenceNumber) {
        return new Heartbeat(traceId, sequenceNumber, Instant.now());
    }
}
```

---

## 2. 抽象基类

### 2.1 AbstractAgent

```java
package com.jonychen.agent.core;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agent 抽象基类
 * 
 * 提供通用的执行框架，子类只需实现：
 * 1. getMetadata() - 返回 Agent 元信息
 * 2. buildSystemPrompt() - 构建系统提示词
 * 3. buildExecutor() - 构建执行器（注册工具）
 */
public abstract class AbstractAgent implements Agent {
    
    protected final ChatLanguageModel chatModel;
    protected final ToolRegistry toolRegistry;
    protected final AgentTraceService traceService;
    
    protected AbstractAgent(
            ChatLanguageModel chatModel,
            ToolRegistry toolRegistry,
            AgentTraceService traceService) {
        this.chatModel = chatModel;
        this.toolRegistry = toolRegistry;
        this.traceService = traceService;
    }
    
    @Override
    public Flux<AgentEvent> executeStream(AgentRequest request, AgentContext context) {
        return Flux.create(sink -> {
            try {
                executeWithSink(request, context, sink);
                sink.complete();
            } catch (Exception e) {
                sink.error(e);
            }
        }, FluxSink.OverflowStrategy.BUFFER);
    }
    
    /**
     * 核心执行逻辑（模板方法）
     */
    protected void executeWithSink(
            AgentRequest request, 
            AgentContext context,
            FluxSink<AgentEvent> sink) {
        
        String traceId = context.getTraceId();
        AtomicInteger stepCounter = new AtomicInteger(0);
        AtomicInteger sequenceCounter = context.getSequenceCounter();
        long startTime = System.currentTimeMillis();
        long timeoutMs = request.options().timeout().toMillis();
        
        // 1. 开始追踪
        traceService.startTrace(
            context.getSessionId(),
            context.getUserId(),
            getMetadata().name(),
            request.userInput()
        );
        
        try {
            // 2. 构建执行器
            AgentExecutor executor = buildExecutor(context);
            
            // 3. 执行循环
            String currentInput = request.userInput();
            int iterations = 0;
            int maxIterations = request.options().maxIterations();
            
            while (iterations < maxIterations) {
                // 全局超时检查
                if (System.currentTimeMillis() - startTime > timeoutMs) {
                    emit(sink, new AgentEvent.AgentError(
                        traceId, sequenceCounter.getAndIncrement(), Instant.now(), "TIMEOUT", "执行超过最大允许时间 " + timeoutMs + "ms", null, false));
                    traceService.endTraceFailed(traceId, "执行超时");
                    return;
                }
                
                if (context.isCancelled()) {
                    emit(sink, new AgentEvent.AgentError(
                        traceId, sequenceCounter.getAndIncrement(), Instant.now(), "CANCELLED", "执行已取消", null, false));
                    traceService.endTraceFailed(traceId, "执行已取消");
                    return;
                }
                
                int stepIndex = stepCounter.getAndIncrement();
                int seqNum = sequenceCounter.getAndIncrement();
                
                // 发送步骤开始事件
                emit(sink, AgentEvent.stepStart(traceId, seqNum, stepIndex, StepType.LLM_CALL, getMetadata().name()));
                
                // 调用 LLM
                AgentStepResult stepResult = executeStep(executor, currentInput, context, stepIndex, sink);
                
                // 发送步骤结束事件
                emit(sink, new AgentEvent.StepEnd(
                    traceId, sequenceCounter.getAndIncrement(), Instant.now(), stepIndex, stepResult.success(), stepResult.summary()));
                
                if (stepResult.isDone()) {
                    // 执行完成
                    long durationMs = System.currentTimeMillis() - startTime;
                    emit(sink, AgentEvent.done(
                        traceId,
                        sequenceCounter.getAndIncrement(),
                        getMetadata().name(),
                        stepResult.output(), 
                        stepCounter.get(),
                        calculateTokenUsage(context),
                        durationMs
                    ));
                    
                    traceService.endTraceSuccess(traceId, stepResult.output(), null);
                    return;
                }
                
                // 处理待确认状态（核心修复：确认恢复机制）
                if (stepResult.confirmationId() != null) {
                    try {
                        long remainingMs = timeoutMs - (System.currentTimeMillis() - startTime);
                        boolean approved = context.awaitConfirmation(stepResult.confirmationId())
                            .get(Math.max(remainingMs, 1000), TimeUnit.MILLISECONDS);
                        
                        if (!approved) {
                            emit(sink, new AgentEvent.AgentError(
                                traceId, sequenceCounter.getAndIncrement(), Instant.now(), "REJECTED", "用户拒绝执行敏感操作", null, false));
                            traceService.endTraceFailed(traceId, "用户拒绝执行敏感操作");
                            return;
                        }
                        
                        // 用户已确认，让 LLM 再次决策（不消耗迭代次数）
                        currentInput = "用户已确认执行该操作，请继续。";
                        iterations++;
                        continue;
                    } catch (TimeoutException e) {
                        emit(sink, new AgentEvent.AgentError(
                                traceId, sequenceCounter.getAndIncrement(), Instant.now(), "CONFIRMATION_TIMEOUT", "等待用户确认超时", null, true));
                        traceService.endTraceFailed(traceId, "等待用户确认超时");
                        return;
                    }
                }
                
                // 继续执行
                currentInput = stepResult.nextInput();
                iterations++;
            }
            
            // 达到最大迭代次数
            emit(sink, new AgentEvent.AgentError(
                traceId, sequenceCounter.getAndIncrement(), Instant.now(), "MAX_ITERATIONS", 
                "已达到最大迭代次数 " + maxIterations, null, false));
            traceService.endTraceFailed(traceId, "达到最大迭代次数");
            
        } catch (Exception e) {
            traceService.endTraceFailed(traceId, e.getMessage());
            emit(sink, new AgentEvent.AgentError(
                traceId, sequenceCounter.getAndIncrement(), Instant.now(), "EXECUTION_ERROR", e.getMessage(), null, true));
        }
    }
    
    /**
     * 执行单步（子类可覆盖）
     */
    protected AgentStepResult executeStep(
            AgentExecutor executor,
            String input,
            AgentContext context,
            int stepIndex,
            FluxSink<AgentEvent> sink) {
        
        // 调用 LLM 获取下一步动作
        LLMResponse response = executor.invoke(input);
        
        // 记录思考过程（LangChain4j ToolCalling 模式下 thought 通常为空或包含在 text 中）
        if (response.thought() != null) {
            emit(sink, AgentEvent.thought(context.getTraceId(), context.getSequenceCounter().getAndIncrement(), stepIndex, response.thought()));
            traceService.recordThought(context.getTraceId(), response.thought());
        }
        
        // 检查是否需要调用工具
        if (response.toolCall() != null) {
            ToolCallRequest toolCall = response.toolCall();
            
            // 发送工具调用事件
            emit(sink, AgentEvent.toolCall(
                context.getTraceId(), context.getSequenceCounter().getAndIncrement(), stepIndex, toolCall.name(), toolCall.params()));
            
            // 执行工具（确认检查由 executeTool 处理，权限由 ToolRegistry 统一处理）
            ToolResult toolResult = executeTool(toolCall, context, sink);
            
            // 处理待确认：发送确认事件后返回 pending，由 executeWithSink 等待用户响应
            if (toolResult.pending()) {
                emit(sink, new AgentEvent.ConfirmationRequired(
                    context.getTraceId(), context.getSequenceCounter().getAndIncrement(), Instant.now(), stepIndex,
                    toolResult.confirmationId(), toolCall.name(),
                    toolResult.confirmationMessage(),
                    toolResult.riskLevel()
                ));
                return AgentStepResult.pendingConfirmation(toolResult.confirmationId());
            }
            
            // 发送工具结果事件
            emit(sink, AgentEvent.toolResult(
                context.getTraceId(), context.getSequenceCounter().getAndIncrement(), stepIndex, 
                toolCall.name(), toolResult.data(), 
                toolResult.success(), toolResult.error()));
            
            // 记录追踪
            traceService.recordToolCall(
                context.getTraceId(), toolCall.name(), 
                toolCall.params(), toolResult);
            
            // 继续执行，将工具结果作为下一次输入
            return AgentStepResult.continueExecution(
                "Observation: " + (toolResult.success() ? toolResult.data() : "Error: " + toolResult.error()));
        }
        
        // 无工具调用，返回最终结果
        return AgentStepResult.done(response.output());
    }
    
    /**
     * 执行工具（确认检查）
     * 
     * 注意：权限校验统一由 ToolRegistry 层负责（DefaultToolRegistry.execute()），
     * 此处不再重复校验，避免双重检查导致逻辑不一致。
     * ConfirmationRequired 事件由 executeStep 统一发送，以确保 stepIndex 正确。
     */
    protected ToolResult executeTool(
            ToolCallRequest toolCall,
            AgentContext context,
            FluxSink<AgentEvent> sink) {
        
        ToolDefinition tool = toolRegistry.getTool(toolCall.name())
            .orElseThrow(() -> new ToolNotFoundException(toolCall.name()));
        
        // 敏感操作需要确认（仅返回 pending 状态，不发送 SSE 事件）
        if (tool.requiresConfirmation() && context.getOptions().requireConfirmation()) {
            String confirmationId = generateConfirmationId();
            return ToolResult.pendingConfirmation(
                confirmationId, 
                "即将执行敏感操作: " + toolCall.name() + "，参数: " + toolCall.params(),
                tool.riskLevel()
            );
        }
        
        // 执行工具（权限校验由 ToolRegistry.execute() 统一处理）
        return toolRegistry.execute(toolCall.name(), toolCall.params(), new ToolExecutionContext(
            context.getTraceId(), context.getSessionId(), context.getUserId(),
            Set.of(), Map.of()  // roles 由 ToolRegistry 从 AgentPermissionService 获取
        ));
    }
    
    /**
     * 构建执行器（子类实现）
     */
    protected abstract AgentExecutor buildExecutor(AgentContext context);
    
    /**
     * 构建系统提示词（子类实现）
     */
    protected abstract String buildSystemPrompt();
    
    // ===== 辅助方法 =====
    
    private void emit(FluxSink<AgentEvent> sink, AgentEvent event) {
        sink.next(event);
    }
    
    private String generateConfirmationId() {
        return "confirm_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
```

### 2.2 AgentExecutor（LangChain4j 集成）

```java
package com.jonychen.agent.core;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.tool.ToolExecutor;

import java.util.List;
import java.util.Map;

/**
 * Agent 执行器
 * 
 * 封装 LangChain4j ChatModel 调用，支持工具调用。
 * 使用结构化输出替代正则解析。
 */
public class AgentExecutor {
    
    private final ChatLanguageModel chatModel;
    private final List<ToolSpecification> tools;
    private final Map<String, ToolExecutor> toolExecutors;
    private final String systemPrompt;
    private static final int MAX_HISTORY_MESSAGES = 20; // 滑动窗口：保留最近 20 条消息（约 10 轮对话）
    private final List<ChatMessage> chatHistory;

    public AgentExecutor(
            ChatLanguageModel chatModel,
            String systemPrompt,
            List<ToolSpecification> tools,
            Map<String, ToolExecutor> toolExecutors) {
        this.chatModel = chatModel;
        this.systemPrompt = systemPrompt;
        this.tools = tools;
        this.toolExecutors = toolExecutors;
        this.chatHistory = new CopyOnWriteArrayList<>();
    }

    /**
     * 调用 LLM
     *
     * 使用 JSON 结构化输出，确保解析可靠。
     * 聊天历史采用滑动窗口，防止长对话导致内存无限增长和 Token 爆炸。
     */
    public LLMResponse invoke(String userMessage) {
        // 构建消息列表
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.addAll(chatHistory);
        messages.add(new UserMessage(userMessage));

        // 调用 LLM（带工具）
        Response<AiMessage> response = chatModel.generate(messages, tools);

        AiMessage aiMessage = response.content();

        // 记录到历史（滑动窗口：超过上限时移除最老的消息）
        chatHistory.add(new UserMessage(userMessage));
        chatHistory.add(aiMessage);
        if (chatHistory.size() > MAX_HISTORY_MESSAGES) {
            chatHistory.subList(0, chatHistory.size() - MAX_HISTORY_MESSAGES).clear();
        }

        // 解析响应
        return parseResponse(aiMessage);
    }
    
    /**
     * 解析 LLM 响应
     * 
     * 完全依赖 LangChain4j 原生 ToolCalling 能力，不再手动正则解析 thought。
     * thought 直接透传 message.text()（模型原生支持 tool calling 时通常已包含 reasoning）。
     */
    private LLMResponse parseResponse(AiMessage message) {
        String text = message.text();
        
        // 检查是否有工具调用请求
        if (message.hasToolExecutionRequests()) {
            var toolRequest = message.toolExecutionRequests().get(0);
            Map<String, Object> params = parseJson(toolRequest.arguments());
            
            // text() 即为模型的思考过程，无需正则提取
            return new LLMResponse(
                text,
                new ToolCallRequest(toolRequest.name(), params),
                null
            );
        }
        
        // 无工具调用，text() 即为最终答案
        return new LLMResponse(
            null,
            null,
            text != null ? text.trim() : ""
        );
    }
    
    private static final ObjectMapper TOOL_OBJECT_MAPPER = new ObjectMapper();

    private Map<String, Object> parseJson(String json) {
        try {
            return TOOL_OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }
}

/**
 * LLM 响应
 */
record LLMResponse(
    String thought,             // 思考过程
    ToolCallRequest toolCall,   // 工具调用请求（可能为 null）
    String output               // 最终输出（无工具调用时有值）
) {}

/**
 * 工具调用请求
 */
record ToolCallRequest(
    String name,
    Map<String, Object> params
) {}

/**
 * 步骤执行结果
 */
record AgentStepResult(
    boolean done,               // 是否完成
    boolean success,            // 是否成功
    String output,              // 输出（完成时有值）
    String nextInput,           // 下一次输入（继续执行时有值）
    String confirmationId,      // 确认 ID（需要确认时有值）
    String summary              // 步骤摘要
) {
    static AgentStepResult done(String output) {
        return new AgentStepResult(true, true, output, null, null, "完成");
    }
    
    static AgentStepResult continueExecution(String nextInput) {
        return new AgentStepResult(false, true, null, nextInput, null, "继续执行");
    }
    
    static AgentStepResult pendingConfirmation(String confirmationId) {
        return new AgentStepResult(false, false, null, null, confirmationId, "等待确认");
    }
}
```

---

## 3. Router Agent

### 3.1 设计目标

替代现有 `AgentOrchestrator.determineTaskType()` 的关键词匹配，使用 LLM 智能判断用户意图并路由到合适的 Agent。

### 3.2 实现

```java
package com.jonychen.agent.impl;

import com.jonychen.agent.core.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

import java.util.List;
import java.util.Map;

/**
 * Router Agent
 * 
 * 智能路由，将用户请求分配到最合适的专业 Agent。
 * 使用 LangChain4j AiServices 结构化输出，彻底避免手动 JSON 解析。
 * 
 * 注意：RouterAgent 直接实现 Agent 接口，不继承 AbstractAgent，
 * 因为路由是同步单次调用，不需要 ReAct 循环（executeWithSink 模板方法）。
 */
public class RouterAgent implements Agent {
    
    private final Map<String, Agent> agents;
    private final RouterAiService routerAiService;
    private volatile String cachedSystemPrompt;  // 缓存系统提示词，避免每次请求重建
    
    public RouterAgent(
            ChatLanguageModel chatModel,
            Map<String, Agent> agents) {
        this.agents = agents;
        // 使用 AiServices 构建结构化输出服务
        this.routerAiService = AiServices.create(RouterAiService.class, chatModel);
    }
    
    @Override
    public AgentMetadata getMetadata() {
        return new AgentMetadata(
            "router", AgentType.ROUTER, "智能路由", "分析意图，选择合适的 Agent",
            "1.0.0",
            Set.of("intent-analysis"),
            Set.of(),
            3, Duration.ofMinutes(1), true
        );
    }
    
    @Override
    public AgentResult execute(AgentRequest request, AgentContext context) {
        RoutingResult routing = route(request.userInput());
        return AgentResult.success(context.getTraceId(), 
            "路由到 " + routing.selectedAgent() + ": " + routing.reason(), List.of());
    }
    
    @Override
    public Flux<AgentEvent> executeStream(AgentRequest request, AgentContext context) {
        // Router 不需要流式执行，直接包装为同步结果
        return Flux.just(AgentEvent.done(
            context.getTraceId(),
            0,  // sequenceNumber
            "router",
            "路由完成",
            1, null, 0
        ));
    }
    
    /**
     * 构建路由系统提示词（延迟初始化 + 缓存）
     */
    private String buildSystemPrompt() {
        if (cachedSystemPrompt != null) {
            return cachedSystemPrompt;
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("""
            你是一个智能路由 Agent，负责分析用户意图并选择最合适的 Agent 处理。
            
            可用的 Agent 列表：
            """);
        
        agents.forEach((name, agent) -> {
            AgentMetadata meta = agent.getMetadata();
            sb.append(String.format("- %s: %s。能力: %s%n",
                meta.name(), meta.description(), String.join(", ", meta.capabilities())));
        });
        
        sb.append("""
            
            请分析用户输入，判断应该由哪个 Agent 处理。
            如果没有合适的 Agent，选择 "chat" 作为默认。
            """);
        
        cachedSystemPrompt = sb.toString();
        return cachedSystemPrompt;
    }
    
    /**
     * 路由决策（使用结构化输出，零手动解析）
     * 
     * @param userInput 用户输入
     * @return 路由结果
     */
    public RoutingResult route(String userInput) {
        // 直接调用 AiServices，LangChain4j 自动保证返回符合 RoutingResult 结构
        RoutingResult result = routerAiService.route(userInput, buildSystemPrompt());
        
        // 兜底校验
        if (result == null || !agents.containsKey(result.selectedAgent())) {
            return new RoutingResult("chat", 0.5, "无匹配 Agent，降级到通用对话", false);
        }
        
        // 检查置信度
        if (result.confidence() < 0.7) {
            return new RoutingResult(
                "chat",
                result.confidence(),
                "置信度过低（" + result.confidence() + "），建议通用对话。" + result.reason(),
                true  // needsConfirmation
            );
        }
        
        return result;
    }
}

/**
 * Router 结构化输出接口（LangChain4j AiServices）
 */
interface RouterAiService {
    
    @SystemMessage("你是一个智能路由 Agent。请严格按 JSON 格式返回路由结果。")
    @UserMessage("根据以下系统提示和用户需求，返回路由决策。\n系统提示：{{systemPrompt}}\n用户输入：{{userInput}}")
    RoutingResult route(@V("userInput") String userInput, @V("systemPrompt") String systemPrompt);
}

/**
 * 路由结果
 *
 * 使用普通 class + @JsonCreator 替代 record，兼容 LangChain4j AiServices
 * 在某些版本中对 Java record 的 JSON 反序列化支持有限的问题。
 */
class RoutingResult {
    private String selectedAgent;
    private double confidence;
    private String reason;
    private boolean needsConfirmation;

    @JsonCreator
    public RoutingResult(
            @JsonProperty("selectedAgent") String selectedAgent,
            @JsonProperty("confidence") double confidence,
            @JsonProperty("reason") String reason,
            @JsonProperty("needsConfirmation") boolean needsConfirmation) {
        this.selectedAgent = selectedAgent;
        this.confidence = confidence;
        this.reason = reason;
        this.needsConfirmation = needsConfirmation;
    }

    public String selectedAgent() { return selectedAgent; }
    public double confidence() { return confidence; }
    public String reason() { return reason; }
    public boolean needsConfirmation() { return needsConfirmation; }
}
```

### 3.3 System Prompt 模板

```yaml
# src/main/resources/prompts/router-agent.yaml
template: |
  你是一个智能路由 Agent，负责分析用户意图并选择最合适的 Agent 处理。
  
  ## 可用的 Agent 列表
  {{#agents}}
  - **{{name}}**: {{description}}
    能力: {{capabilities}}
    适用场景: {{scenarios}}
  {{/agents}}
  
  ## 意图分类指南
  
  | 用户输入示例 | 推荐Agent | 判断依据 |
  |------------|----------|---------|
  | "检查模型健康" | ops | 涉及系统运维 |
  | "查询上周 token 消耗" | data | 需要查数据库 |
  | "优化这个 prompt" | prompt | Prompt 工程任务 |
  | "生成测试用例" | test | 测试相关 |
  | "你好" | chat | 普通对话 |
  
  ## 输出格式
  
  请严格按照以下 JSON 格式输出：
  ```json
  {
    "selectedAgent": "agent名称",
    "confidence": 0.0-1.0,
    "reason": "选择理由（一句话）"
  }
  ```
  
  ## 规则
  1. confidence 必须在 0-1 之间
  2. 如果不确定，confidence 设为 0.5 以下
  3. 没有 100% 匹配时，选择最接近的 Agent

variables:
  - name: agents
    description: 可用 Agent 列表
    required: true

metadata:
  modelRecommendation: qwen-plus
  maxTokens: 500
  temperature: 0.1
```

---

## 4. OpsAgent（运维助手）

### 4.1 设计

负责模型诊断、故障处置、健康监控。需要 ADMIN 权限。

### 4.2 实现

```java
package com.jonychen.agent.impl;

import com.jonychen.agent.core.*;
import com.jonychen.tool.*;
import java.util.HashMap;

/**
 * 运维助手 Agent
 * 
 * 能力：
 * - 模型健康检查
 * - 熔断器状态查询
 * - 动态调整模型权重
 * - 启用/禁用模型
 * - Token 用量分析
 */
public class OpsAgent extends AbstractAgent {
    
    private final ModelStateTools modelStateTools;
    private final CircuitBreakerTools circuitBreakerTools;
    private final TokenUsageTools tokenUsageTools;
    
    public OpsAgent(
            ChatLanguageModel chatModel,
            ToolRegistry toolRegistry,
            AgentTraceService traceService,
            ModelStateTools modelStateTools,
            CircuitBreakerTools circuitBreakerTools,
            TokenUsageTools tokenUsageTools) {
        super(chatModel, toolRegistry, traceService);
        this.modelStateTools = modelStateTools;
        this.circuitBreakerTools = circuitBreakerTools;
        this.tokenUsageTools = tokenUsageTools;
    }
    
    @Override
    public AgentMetadata getMetadata() {
        return AgentMetadata.ops(); // 使用工厂方法
    }
    
    @Override
    public double canHandle(AgentRequest request) {
        String input = request.userInput().toLowerCase();
        if (input.contains("模型") && (input.contains("健康") || input.contains("状态") || input.contains("权重")))
            return 0.9;
        if (input.contains("熔断") || input.contains("运维") || input.contains("ops"))
            return 0.85;
        if (input.contains("模型") || input.contains("token"))
            return 0.6;
        return 0.0;
    }
    
    @Override
    public List<ToolDefinition> getAvailableTools() {
        return toolRegistry.getToolsByCategory(ToolCategory.MODEL_STATE);
    }
    
    @Override
    protected String buildSystemPrompt() {
        return """
            你是一个智能运维助手，负责 AI 模型系统的诊断和维护。
            
            ## 可用工具
            - get_model_health: 获取所有模型的健康状态
            - get_circuit_breaker_status: 获取熔断器状态
            - adjust_model_weight: 调整模型负载均衡权重
            - toggle_model_enabled: 启用/禁用模型
            - get_token_usage: 获取 token 用量统计
            - get_rate_limit_status: 获取限流状态
            
            ## 工作流程
            1. 先获取当前状态（健康检查、熔断器状态）
            2. 分析问题原因
            3. 制定处置方案
            4. 执行处置（敏感操作需要用户确认）
            5. 验证处置效果
            
            ## 输出格式
            在每个步骤前，用 <thought> 标签说明你的思考过程。
            
            ## 安全约束
            - 调整权重和禁用模型是敏感操作，需要用户确认
            - 每次只能调整一个模型的权重
            - 权重调整幅度不能超过 50
            """;
    }
    
    @Override
    protected AgentExecutor buildExecutor(AgentContext context) {
        // 注册工具
        List<ToolSpecification> tools = List.of(
            ToolSpecificationConverter.convert(modelStateTools.getHealthTool()),
            ToolSpecificationConverter.convert(circuitBreakerTools.getStatusTool()),
            ToolSpecificationConverter.convert(modelStateTools.adjustWeightTool()),
            ToolSpecificationConverter.convert(modelStateTools.toggleEnabledTool()),
            ToolSpecificationConverter.convert(tokenUsageTools.getUsageTool())
        );
        
        Map<String, ToolExecutor> executors = new HashMap<>();
        executors.put("get_model_health", params -> modelStateTools.getHealth(
            params.get("include_disabled") != null ? Boolean.parseBoolean(params.get("include_disabled").toString()) : false));
        executors.put("get_circuit_breaker_status", params -> circuitBreakerTools.getStatus());
        executors.put("adjust_model_weight", params -> modelStateTools.adjustWeight(
            (String) params.get("modelName"),
            Integer.parseInt(params.get("weight").toString()),
            (String) params.get("reason")));
        executors.put("toggle_model_enabled", params -> modelStateTools.toggleEnabled(
            (String) params.get("modelName"),
            Boolean.parseBoolean(params.get("enabled").toString()),
            (String) params.get("reason")));
        executors.put("get_token_usage", params -> tokenUsageTools.getUsage(params));
        
        return new AgentExecutor(chatModel, buildSystemPrompt(), tools, executors);
    }
}
```

---

## 5. DataAgent（数据分析助手）

### 5.1 实现

```java
package com.jonychen.agent.impl;

/**
 * 数据分析助手 Agent
 * 
 * 能力：
 * - 自然语言转 SQL 查询
 * - 数据可视化（生成图表）
 * - 数据导出（CSV/Excel）
 * 
 * 安全约束：
 * - 只读查询（SELECT）
 * - 白名单表（users, agent_traces, token_usage...）
 * - 结果行数限制（默认 1000）
 */
public class DataAgent extends AbstractAgent {
    
    private final DatabaseTools databaseTools;
    private final ChartTools chartTools;
    private final ExportTools exportTools;

    public DataAgent(
            ChatLanguageModel chatModel,
            ToolRegistry toolRegistry,
            AgentTraceService traceService,
            DatabaseTools databaseTools,
            ChartTools chartTools,
            ExportTools exportTools) {
        super(chatModel, toolRegistry, traceService);
        this.databaseTools = databaseTools;
        this.chartTools = chartTools;
        this.exportTools = exportTools;
    }

    @Override
    public AgentMetadata getMetadata() {
        return new AgentMetadata(
            "data", AgentType.DATA, "数据分析助手", "自然语言查库、图表生成、数据导出",
            "1.0.0",
            Set.of("sql:read", "chart:generate", "export:create"),
            Set.of("USER"),  // 普通用户可用
            5, Duration.ofMinutes(3), true
        );
    }
    
    @Override
    public double canHandle(AgentRequest request) {
        String input = request.userInput().toLowerCase();
        if (input.contains("查询") || input.contains("数据") || input.contains("统计"))
            return 0.85;
        if (input.contains("sql") || input.contains("图表") || input.contains("导出"))
            return 0.9;
        if (input.contains("表") || input.contains("库"))
            return 0.6;
        return 0.0;
    }
    
    @Override
    public List<ToolDefinition> getAvailableTools() {
        return toolRegistry.getToolsByCategory(ToolCategory.DATABASE);
    }
    
    @Override
    protected String buildSystemPrompt() {
        return """
            你是一个数据分析助手，帮助用户查询和分析数据。
            
            ## 可用工具
            - list_tables: 列出可查询的数据库表
            - describe_table: 查看表结构
            - execute_query: 执行只读 SQL 查询
            - generate_chart: 根据数据生成图表配置
            - export_data: 导出查询结果为 CSV
            
            ## 数据库结构
            {{#tables}}
            表: {{name}}
            描述: {{description}}
            字段: {{columns}}
            {{/tables}}
            
            ## 工作流程
            1. 理解用户的数据需求
            2. 确认需要查询的表和字段
            3. 生成 SQL 查询语句
            4. 执行查询并展示结果
            5. 如需要，生成图表或导出数据
            
            ## 安全约束
            - 只能执行 SELECT 查询
            - 只能查询白名单表
            - 查询结果最多返回 1000 行
            
            ## SQL 生成规范
            - 使用标准 PostgreSQL 语法
            - 复杂查询先 explain 分析
            - 大表查询添加 LIMIT
            """;
    }
    
    @Override
    protected AgentExecutor buildExecutor(AgentContext context) {
        List<ToolSpecification> tools = List.of(
            ToolSpecificationConverter.convert(databaseTools.listTablesTool()),
            ToolSpecificationConverter.convert(databaseTools.describeTableTool()),
            ToolSpecificationConverter.convert(databaseTools.executeQueryTool()),
            ToolSpecificationConverter.convert(chartTools.generateChartTool()),
            ToolSpecificationConverter.convert(exportTools.exportDataTool())
        );
        
        Map<String, ToolExecutor> executors = new HashMap<>();
        executors.put("list_tables", params -> databaseTools.listTables());
        executors.put("describe_table", params -> databaseTools.describeTable((String) params.get("tableName")));
        executors.put("execute_readonly_query", params -> databaseTools.executeQuery(
            (String) params.get("sql"),
            params.get("limit") != null ? Integer.parseInt(params.get("limit").toString()) : 100));
        executors.put("generate_chart", params -> chartTools.generateChart(params));
        executors.put("export_data", params -> exportTools.exportData(params));
        
        return new AgentExecutor(chatModel, buildSystemPrompt(), tools, executors);
    }
}
```

---

## 6. PromptAgent（Prompt 工程助手）

### 6.1 实现

```java
package com.jonychen.agent.impl;

/**
 * Prompt 工程助手 Agent
 * 
 * 能力：
 * - Prompt 版本管理
 * - Prompt 优化建议
 * - A/B 测试配置
 * - 效果评测
 */
public class PromptAgent extends AbstractAgent {
    
    private final PromptTools promptTools;
    private final EvaluationTools evaluationTools;
    
    public PromptAgent(
            ChatLanguageModel chatModel,
            ToolRegistry toolRegistry,
            AgentTraceService traceService,
            PromptTools promptTools,
            EvaluationTools evaluationTools) {
        super(chatModel, toolRegistry, traceService);
        this.promptTools = promptTools;
        this.evaluationTools = evaluationTools;
    }
    
    @Override
    public AgentMetadata getMetadata() {
        return new AgentMetadata(
            "prompt", AgentType.PROMPT, "Prompt 工程助手", "Prompt 优化、评测、版本管理",
            "1.0.0",
            Set.of("prompt:read", "prompt:write", "evaluation:run"),
            Set.of("ADMIN"),
            8, Duration.ofMinutes(5), true
        );
    }
    
    @Override
    public double canHandle(AgentRequest request) {
        String input = request.userInput().toLowerCase();
        if (input.contains("prompt") || input.contains("提示词"))
            return 0.9;
        if (input.contains("优化") && input.contains("模板"))
            return 0.8;
        if (input.contains("ab测试") || input.contains("a/b测试") || input.contains("评测"))
            return 0.85;
        return 0.0;
    }
    
    @Override
    public List<ToolDefinition> getAvailableTools() {
        return toolRegistry.getToolsByCategory(ToolCategory.PROMPT);
    }
    
    @Override
    protected String buildSystemPrompt() {
        return """
            你是一个 Prompt 工程专家，帮助用户优化和管理 Prompt。
            
            ## 可用工具
            - get_prompt_versions: 获取 Prompt 版本历史
            - create_prompt_version: 创建新版本
            - evaluate_prompt: 评测 Prompt 效果
            - setup_ab_test: 配置 A/B 测试
            - compare_versions: 对比版本差异
            
            ## Prompt 优化原则
            1. 明确性：指令清晰，无歧义
            2. 结构化：使用分段和标记
            3. 示例驱动：提供 few-shot 示例
            4. 约束明确：说明输出格式和限制
            
            ## 工作流程
            1. 分析当前 Prompt 问题
            2. 提出优化建议
            3. 生成新版本
            4. 配置 A/B 测试验证
            5. 根据评测结果决定是否上线
            """;
    }
    
    @Override
    protected AgentExecutor buildExecutor(AgentContext context) {
        List<ToolSpecification> tools = List.of(
            ToolSpecificationConverter.convert(promptTools.getVersionsTool()),
            ToolSpecificationConverter.convert(promptTools.createVersionTool()),
            ToolSpecificationConverter.convert(promptTools.activateVersionTool()),
            ToolSpecificationConverter.convert(evaluationTools.evaluateTool()),
            ToolSpecificationConverter.convert(evaluationTools.compareVersionsTool())
        );
        
        Map<String, ToolExecutor> executors = new HashMap<>();
        executors.put("get_prompt_versions", params -> promptTools.getVersions(
            (String) params.get("name"),
            params.get("includeInactive") != null && Boolean.parseBoolean(params.get("includeInactive").toString())));
        executors.put("create_prompt_version", params -> promptTools.createVersion(
            (String) params.get("name"), (String) params.get("content"), (String) params.get("description")));
        executors.put("activate_prompt_version", params -> promptTools.activateVersion(
            (String) params.get("name"),
            Integer.parseInt(params.get("version").toString())));
        executors.put("evaluate_prompt", params -> evaluationTools.evaluate(params));
        executors.put("compare_versions", params -> evaluationTools.compareVersions(params));
        
        return new AgentExecutor(chatModel, buildSystemPrompt(), tools, executors);
    }
}
```

---

## 7. TestAgent（测试生成助手）

### 7.1 实现

```java
package com.jonychen.agent.impl;

/**
 * 测试生成助手 Agent
 * 
 * 能力：
 * - 读取源代码
 * - 生成单元测试
 * - 生成集成测试
 * - 生成 E2E 测试
 * - 执行测试并分析结果
 */
public class TestAgent extends AbstractAgent {
    
    private final SourceCodeTools sourceCodeTools;
    private final TestGeneratorTools testGeneratorTools;
    private final TestRunnerTools testRunnerTools;
    
    public TestAgent(
            ChatLanguageModel chatModel,
            ToolRegistry toolRegistry,
            AgentTraceService traceService,
            SourceCodeTools sourceCodeTools,
            TestGeneratorTools testGeneratorTools,
            TestRunnerTools testRunnerTools) {
        super(chatModel, toolRegistry, traceService);
        this.sourceCodeTools = sourceCodeTools;
        this.testGeneratorTools = testGeneratorTools;
        this.testRunnerTools = testRunnerTools;
    }
    
    @Override
    public AgentMetadata getMetadata() {
        return new AgentMetadata(
            "test", AgentType.TEST, "测试生成助手", "单元/集成/E2E 测试生成与执行",
            "1.0.0",
            Set.of("code:read", "test:create", "test:run"),
            Set.of("ADMIN"),
            10, Duration.ofMinutes(10), true
        );
    }
    
    @Override
    public double canHandle(AgentRequest request) {
        String input = request.userInput().toLowerCase();
        if (input.contains("测试") || input.contains("test"))
            return 0.9;
        if (input.contains("单测") || input.contains("单元测试") || input.contains("集成测试"))
            return 0.95;
        if (input.contains("覆盖率") || input.contains("e2e"))
            return 0.85;
        return 0.0;
    }
    
    @Override
    public List<ToolDefinition> getAvailableTools() {
        return toolRegistry.getToolsByCategory(ToolCategory.TEST);
    }
    
    @Override
    protected String buildSystemPrompt() {
        return """
            你是一个测试工程专家，帮助用户生成和执行测试。
            
            ## 可用工具
            - read_source_file: 读取源代码文件
            - list_source_files: 列出源代码文件
            - generate_unit_test: 生成单元测试
            - generate_integration_test: 生成集成测试
            - generate_e2e_test: 生成 E2E 测试
            - run_tests: 执行测试
            - analyze_coverage: 分析覆盖率
            
            ## 测试生成原则
            1. 覆盖正常路径
            2. 覆盖边界条件
            3. 覆盖异常情况
            4. 测试命名清晰
            5. 断言明确具体
            
            ## 测试类型选择
            - 单元测试：纯逻辑、无外部依赖
            - 集成测试：数据库、外部服务
            - E2E 测试：完整业务流程
            """;
    }
    
    @Override
    protected AgentExecutor buildExecutor(AgentContext context) {
        List<ToolSpecification> tools = List.of(
            ToolSpecificationConverter.convert(testGeneratorTools.listSourceFilesTool()),
            ToolSpecificationConverter.convert(testGeneratorTools.readSourceFileTool()),
            ToolSpecificationConverter.convert(testGeneratorTools.generateUnitTestTool()),
            ToolSpecificationConverter.convert(testRunnerTools.runTestTool())
        );
        
        Map<String, ToolExecutor> executors = new HashMap<>();
        executors.put("list_source_files", params -> testGeneratorTools.listSourceFiles(
            params.getOrDefault("path", "src/main/java").toString(),
            params.getOrDefault("pattern", "*.java").toString()));
        executors.put("read_source_file", params -> testGeneratorTools.readSourceFile(
            (String) params.get("path")));
        executors.put("generate_unit_test", params -> testGeneratorTools.generateUnitTest(
            (String) params.get("sourcePath"),
            (String) params.get("className"),
            params.getOrDefault("methods", "").toString()));
        executors.put("run_test", params -> testRunnerTools.runTest(
            (String) params.get("testClass"),
            params.getOrDefault("testMethod", "").toString()));
        
        return new AgentExecutor(chatModel, buildSystemPrompt(), tools, executors);
    }
}
```

---

## 8. ChatAgent（通用对话）

### 8.1 实现

```java
package com.jonychen.agent.impl;

/**
 * 通用对话 Agent
 * 
 * 能力：
 * - 普通问答、闲聊
 * - 不调用任何工具，直接使用 LLM 回答
 * - 作为 Router 的降级 Agent
 */
public class ChatAgent extends AbstractAgent {
    
    public ChatAgent(
            ChatLanguageModel chatModel,
            ToolRegistry toolRegistry,
            AgentTraceService traceService) {
        super(chatModel, toolRegistry, traceService);
    }
    
    @Override
    public AgentMetadata getMetadata() {
        return new AgentMetadata(
            "chat", AgentType.CHAT, "通用对话", "普通问答、闲聊",
            "1.0.0",
            Set.of("chat"),
            Set.of(),
            5, Duration.ofMinutes(2), true
        );
    }
    
    @Override
    public double canHandle(AgentRequest request) {
        // ChatAgent 的置信度始终为最低，作为兜底
        return 0.1;
    }
    
    @Override
    protected String buildSystemPrompt() {
        return """
            你是一个友好的 AI 助手，负责回答用户的通用问题。
            
            ## 工作方式
            - 直接回答用户的问题
            - 如果用户的问题涉及专业领域（运维、数据分析、Prompt 工程、测试），建议用户使用对应的专业 Agent
            
            ## 输出格式
            - 使用清晰、简洁的语言回答
            - 适当使用 Markdown 格式化
            """;
    }
    
    @Override
    protected AgentExecutor buildExecutor(AgentContext context) {
        // ChatAgent 不注册任何工具
        return new AgentExecutor(chatModel, buildSystemPrompt(), List.of(), Map.of());
    }
}
```

---

## 9. Agent 注册与发现

### 8.1 AgentRegistry

```java
package com.jonychen.agent.core;

import org.springframework.stereotype.Component;
import java.util.*;

/**
 * Agent 注册表
 * 
 * 管理所有 Agent 实例，支持按名称查找和能力查询。
 */
@Component
public class AgentRegistry {
    
    private final Map<String, Agent> agents = new ConcurrentHashMap<>();
    
    /**
     * 注册 Agent
     */
    public void register(Agent agent) {
        AgentMetadata metadata = agent.getMetadata();
        agents.put(metadata.name(), agent);
    }
    
    /**
     * 获取 Agent
     */
    public Optional<Agent> getAgent(String name) {
        return Optional.ofNullable(agents.get(name));
    }
    
    /**
     * 按能力查找 Agent
     */
    public List<Agent> findByCapability(String capability) {
        return agents.values().stream()
            .filter(agent -> agent.getMetadata().capabilities().contains(capability))
            .toList();
    }
    
    /**
     * 获取所有 Agent 元信息
     */
    public List<AgentMetadata> getAllMetadata() {
        return agents.values().stream()
            .map(Agent::getMetadata)
            .toList();
    }
}
```

### 8.2 Spring 自动注册

```java
package com.jonychen.agent.config;

@Configuration
public class AgentConfig {
    
    @Bean
    public OpsAgent opsAgent(
            LoadBalancedChatModel chatModel,
            ToolRegistry toolRegistry,
            AgentTraceService traceService,
            ModelStateTools modelStateTools,
            CircuitBreakerTools circuitBreakerTools,
            TokenUsageTools tokenUsageTools) {
        
        OpsAgent agent = new OpsAgent(
            chatModel, toolRegistry, traceService,
            modelStateTools, circuitBreakerTools, tokenUsageTools
        );
        
        return agent;
    }
    
    @Bean
    public DataAgent dataAgent(...) { ... }
    
    @Bean
    public PromptAgent promptAgent(...) { ... }
    
    @Bean
    public TestAgent testAgent(...) { ... }
    
    @Bean
    public ChatAgent chatAgent(
            LoadBalancedChatModel chatModel,
            ToolRegistry toolRegistry,
            AgentTraceService traceService) {
        return new ChatAgent(chatModel, toolRegistry, traceService);
    }
    
    @Bean
    @DependsOn("agentRegistry") // 确保 agentRegistry 先完成所有 Agent 注册
    public RouterAgent routerAgent(
            LoadBalancedChatModel chatModel,
            AgentRegistry agentRegistry) {

        Map<String, Agent> agentMap = agentRegistry.getAllMetadata().stream()
            .collect(Collectors.toMap(
                AgentMetadata::name,
                m -> agentRegistry.getAgent(m.name()).orElseThrow()
            ));

        return new RouterAgent(chatModel, agentMap);
    }
    
    @Bean
    public AgentRegistry agentRegistry(
            OpsAgent opsAgent,
            DataAgent dataAgent,
            PromptAgent promptAgent,
            TestAgent testAgent,
            ChatAgent chatAgent) {
        
        AgentRegistry registry = new AgentRegistry();
        registry.register(opsAgent);
        registry.register(dataAgent);
        registry.register(promptAgent);
        registry.register(testAgent);
        registry.register(chatAgent);
        return registry;
    }
}
```
