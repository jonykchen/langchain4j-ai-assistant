package com.jonychen.agent.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jonychen.observability.trace.AgentTraceService;
import com.jonychen.observability.trace.TraceContext;
import com.jonychen.tool.ToolDefinition;
import com.jonychen.tool.ToolRegistry;
import com.jonychen.tool.ToolResult;

import dev.langchain4j.model.chat.ChatModel;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

/**
 * Agent 抽象基类
 *
 * <p>提供通用的执行框架，子类只需实现：
 *
 * <ul>
 *   <li>getMetadata() - 返回 Agent 元信息
 *   <li>buildSystemPrompt() - 构建系统提示词
 *   <li>buildExecutor() - 构建执行器（注册工具）
 * </ul>
 *
 * <p>执行流程：
 *
 * <ol>
 *   <li>启动追踪（Trace）
 *   <li>执行循环（LLM 调用 → 工具调用 → 工具结果）
 *   <li>委托处理（可选：委托给其他 Agent）
 *   <li>超时/取消检查
 *   <li>步骤事件推送
 *   <li>完成
 * </ol>
 *
 * @author jonychen
 */
public abstract class AbstractAgent implements Agent {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final ChatModel chatModel;
    protected final ToolRegistry toolRegistry;
    protected final AgentTraceService traceService;
    protected final TraceContext traceContext;

    /** 委托服务（可选，用于 Agent 间协作） */
    protected AgentDelegationService delegationService;

    /** 审计服务（可选，用于记录审计日志） */
    protected AgentAuditService auditService;

    /** 指标服务（可选，用于记录 Prometheus 指标） */
    protected AgentMetricsService metricsService;

    /** Token 追踪服务（可选，用于记录 Token 使用量） */
    protected TokenUsageTracker tokenUsageTracker;

    protected AbstractAgent(
            ChatModel chatModel,
            ToolRegistry toolRegistry,
            AgentTraceService traceService,
            TraceContext traceContext) {
        this.chatModel = chatModel;
        this.toolRegistry = toolRegistry;
        this.traceService = traceService;
        this.traceContext = traceContext;
    }

    /**
     * 设置委托服务
     *
     * @param delegationService 委托服务
     */
    public void setDelegationService(AgentDelegationService delegationService) {
        this.delegationService = delegationService;
    }

    /**
     * 设置审计服务
     *
     * @param auditService 审计服务
     */
    public void setAuditService(AgentAuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * 设置指标服务
     *
     * @param metricsService 指标服务
     */
    public void setMetricsService(AgentMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    /**
     * 设置 Token 追踪服务
     *
     * @param tokenUsageTracker Token 追踪服务
     */
    public void setTokenUsageTracker(TokenUsageTracker tokenUsageTracker) {
        this.tokenUsageTracker = tokenUsageTracker;
    }

    @Override
    public AgentResult execute(AgentRequest request, AgentContext context) {
        log.info("[{}] 开始同步执行: traceId={}", getMetadata().name(), context.getTraceId());

        long startTime = System.currentTimeMillis();
        List<AgentStep> steps = new ArrayList<>();

        try {
            AgentExecutor executor = buildExecutor(context);
            String currentInput = request.userInput();
            int iterations = 0;
            int maxIterations = context.getOptions().maxIterations();

            while (iterations < maxIterations) {
                if (context.isCancelled()) {
                    return AgentResult.cancelled(
                            context.getTraceId(), steps, System.currentTimeMillis() - startTime);
                }

                long stepStartTime = System.currentTimeMillis();
                int stepIndex = steps.size();

                // 调用 LLM
                LLMResponse response = executor.invoke(currentInput);

                // 记录思考过程
                if (response.hasThought()) {
                    steps.add(
                            AgentStep.thought(
                                    stepIndex,
                                    response.thought(),
                                    System.currentTimeMillis() - stepStartTime));
                }

                // 检查是否有工具调用
                if (response.hasToolCall()) {
                    ToolCallRequest toolCall = response.toolCall();

                    // 执行工具
                    long toolStartTime = System.currentTimeMillis();
                    ToolResult toolResult = executeTool(toolCall, context);

                    steps.add(AgentStep.toolCall(stepIndex, toolCall.name(), toolCall.params(), 0));
                    steps.add(
                            AgentStep.toolResult(
                                    stepIndex,
                                    toolCall.name(),
                                    toolResult.data(),
                                    toolResult.success(),
                                    toolResult.error(),
                                    System.currentTimeMillis() - toolStartTime));

                    if (toolResult.pending()) {
                        return AgentResult.failure(
                                context.getTraceId(),
                                "工具执行需要确认，但在同步模式下无法等待",
                                steps,
                                System.currentTimeMillis() - startTime);
                    }

                    currentInput =
                            "Observation: "
                                    + (toolResult.success()
                                            ? toolResult.data()
                                            : "Error: " + toolResult.error());
                    iterations++;
                } else {
                    // 无工具调用，返回最终结果
                    return AgentResult.success(
                            context.getTraceId(),
                            response.output(),
                            steps,
                            TokenUsage.empty(),
                            System.currentTimeMillis() - startTime);
                }
            }

            return AgentResult.failure(
                    context.getTraceId(),
                    "达到最大迭代次数: " + maxIterations,
                    steps,
                    System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            log.error("[{}] 同步执行失败: {}", getMetadata().name(), e.getMessage(), e);
            return AgentResult.failure(
                    context.getTraceId(),
                    e.getMessage(),
                    steps,
                    System.currentTimeMillis() - startTime);
        }
    }

    @Override
    public Flux<AgentEvent> executeStream(AgentRequest request, AgentContext context) {
        return Flux.create(
                sink -> {
                    try {
                        executeWithSink(request, context, sink);
                        sink.complete();
                    } catch (Exception e) {
                        sink.error(e);
                    }
                },
                FluxSink.OverflowStrategy.BUFFER);
    }

    /**
     * 核心执行逻辑（模板方法）
     *
     * <p>实现 ReAct 循环：
     *
     * <ol>
     *   <li>调用 LLM 获取下一步动作
     *   <li>如有工具调用，执行工具并观察结果
     *   <li>将观察结果作为下一次输入
     *   <li>循环直到得出最终答案或达到限制
     * </ol>
     */
    protected void executeWithSink(
            AgentRequest request, AgentContext context, FluxSink<AgentEvent> sink) {
        String traceId = context.getTraceId();
        String agentName = getMetadata().name();
        AtomicInteger stepCounter = new AtomicInteger(0);
        AtomicInteger sequenceCounter = context.getSequenceCounter();
        long startTime = System.currentTimeMillis();
        long timeoutMs = request.options().timeout().toMillis();

        log.info(
                "[{}] 开始流式执行: traceId={}, userInput={}",
                agentName,
                traceId,
                truncate(request.userInput(), 100));

        // 发送开始追踪
        traceService.startTrace(
                context.getSessionId(), context.getUserId(), agentName, request.userInput());

        // 设置追踪上下文，以便工具执行切面能够获取 Trace ID
        traceContext.setCurrentTraceId(traceId);

        try {
            AgentExecutor executor = buildExecutor(context);
            String currentInput = request.userInput();
            int iterations = 0;
            int maxIterations = request.options().maxIterations();

            while (iterations < maxIterations) {
                // 全局超时检查
                if (System.currentTimeMillis() - startTime > timeoutMs) {
                    emit(
                            sink,
                            AgentEvent.error(
                                    traceId,
                                    sequenceCounter.getAndIncrement(),
                                    "TIMEOUT",
                                    "执行超过最大允许时间 " + timeoutMs + "ms",
                                    null,
                                    false));
                    traceService.endTraceFailed(traceId, "执行超时");
                    return;
                }

                if (context.isCancelled()) {
                    emit(
                            sink,
                            AgentEvent.error(
                                    traceId,
                                    sequenceCounter.getAndIncrement(),
                                    "CANCELLED",
                                    "执行已取消",
                                    null,
                                    false));
                    traceService.endTraceFailed(traceId, "执行已取消");
                    return;
                }

                int stepIndex = stepCounter.getAndIncrement();

                // 发送步骤开始事件
                emit(
                        sink,
                        AgentEvent.stepStart(
                                traceId,
                                sequenceCounter.getAndIncrement(),
                                stepIndex,
                                StepType.LLM_CALL,
                                agentName));

                // 执行单步
                AgentStepResult stepResult =
                        executeStep(
                                executor, currentInput, context, stepIndex, sink, sequenceCounter);

                // 发送步骤结束事件
                emit(
                        sink,
                        AgentEvent.stepEnd(
                                traceId,
                                sequenceCounter.getAndIncrement(),
                                stepIndex,
                                stepResult.success(),
                                stepResult.summary(),
                                0L));

                if (stepResult.done()) {
                    // 执行完成
                    long durationMs = System.currentTimeMillis() - startTime;
                    emit(
                            sink,
                            AgentEvent.done(
                                    traceId,
                                    sequenceCounter.getAndIncrement(),
                                    agentName,
                                    stepResult.output(),
                                    stepCounter.get(),
                                    TokenUsage.empty(),
                                    durationMs));
                    traceService.endTraceSuccess(traceId, stepResult.output(), 0L, 0L);
                    log.info(
                            "[{}] 执行完成: traceId={}, steps={}, duration={}ms",
                            agentName,
                            traceId,
                            stepCounter.get(),
                            durationMs);
                    return;
                }

                // 处理待确认状态
                if (stepResult.needsConfirmation()) {
                    try {
                        long remainingMs = timeoutMs - (System.currentTimeMillis() - startTime);
                        Boolean approved =
                                context.awaitConfirmation(stepResult.confirmationId())
                                        .get(Math.max(remainingMs, 1000), TimeUnit.MILLISECONDS);

                        if (approved == null || !approved) {
                            emit(
                                    sink,
                                    AgentEvent.error(
                                            traceId,
                                            sequenceCounter.getAndIncrement(),
                                            "REJECTED",
                                            "用户拒绝执行敏感操作",
                                            null,
                                            false));
                            traceService.endTraceFailed(traceId, "用户拒绝执行敏感操作");
                            return;
                        }

                        // 用户已确认，继续执行
                        currentInput = "用户已确认执行该操作，请继续。";
                        iterations++;
                        continue;
                    } catch (Exception e) {
                        emit(
                                sink,
                                AgentEvent.error(
                                        traceId,
                                        sequenceCounter.getAndIncrement(),
                                        "CONFIRMATION_TIMEOUT",
                                        "等待用户确认超时",
                                        null,
                                        true));
                        traceService.endTraceFailed(traceId, "等待用户确认超时");
                        return;
                    }
                }

                currentInput = stepResult.nextInput();
                iterations++;
            }

            // 达到最大迭代次数
            emit(
                    sink,
                    AgentEvent.error(
                            traceId,
                            sequenceCounter.getAndIncrement(),
                            "MAX_ITERATIONS",
                            "已达到最大迭代次数 " + maxIterations,
                            null,
                            false));
            traceService.endTraceFailed(traceId, "达到最大迭代次数");

        } catch (Exception e) {
            log.error("[{}] 执行失败: {}", agentName, e.getMessage(), e);
            traceService.endTraceFailed(traceId, e.getMessage());
            emit(
                    sink,
                    AgentEvent.error(
                            traceId,
                            sequenceCounter.getAndIncrement(),
                            "EXECUTION_ERROR",
                            e.getMessage(),
                            null,
                            true));
        } finally {
            traceContext.clear();
        }
    }

    /** 执行单步 */
    protected AgentStepResult executeStep(
            AgentExecutor executor,
            String input,
            AgentContext context,
            int stepIndex,
            FluxSink<AgentEvent> sink,
            AtomicInteger sequenceCounter) {

        String traceId = context.getTraceId();
        long stepStartTime = System.currentTimeMillis();

        // 调用 LLM
        LLMResponse response = executor.invoke(input);

        // 记录思考过程
        if (response.hasThought()) {
            emit(
                    sink,
                    AgentEvent.thought(
                            traceId,
                            sequenceCounter.getAndIncrement(),
                            stepIndex,
                            response.thought()));
            log.debug(
                    "[{}] 步骤 {} 思考: {}",
                    getMetadata().name(),
                    stepIndex,
                    truncate(response.thought(), 200));
        }

        // 检查是否有工具调用
        if (response.hasToolCall()) {
            ToolCallRequest toolCall = response.toolCall();

            // 发送工具调用事件
            emit(
                    sink,
                    AgentEvent.toolCall(
                            traceId,
                            sequenceCounter.getAndIncrement(),
                            stepIndex,
                            toolCall.name(),
                            toolCall.params()));
            log.info(
                    "[{}] 步骤 {} 工具调用: {} params={}",
                    getMetadata().name(),
                    stepIndex,
                    toolCall.name(),
                    toolCall.params());

            // 执行工具
            ToolResult toolResult;

            // 检查是否是委托工具调用
            if ("delegate_to_agent".equals(toolCall.name()) && delegationService != null) {
                toolResult =
                        executeDelegationTool(toolCall, context, sink, sequenceCounter, stepIndex);
            } else {
                toolResult = executeTool(toolCall, context);
            }

            // 处理待确认
            if (toolResult.pending()) {
                emit(
                        sink,
                        AgentEvent.confirmationRequired(
                                traceId,
                                sequenceCounter.getAndIncrement(),
                                stepIndex,
                                toolResult.confirmationId(),
                                toolCall.name(),
                                toolResult.confirmationMessage(),
                                toolResult.riskLevel(),
                                toolCall.params() != null ? toolCall.params() : Map.of()));
                return AgentStepResult.pendingConfirmation(toolResult.confirmationId());
            }

            // 发送工具结果事件
            emit(
                    sink,
                    AgentEvent.toolResult(
                            traceId,
                            sequenceCounter.getAndIncrement(),
                            stepIndex,
                            toolCall.name(),
                            toolResult.data(),
                            toolResult.success(),
                            toolResult.error(),
                            toolResult.executionTimeMs()));
            log.info(
                    "[{}] 步骤 {} 工具结果: {} success={} duration={}ms",
                    getMetadata().name(),
                    stepIndex,
                    toolCall.name(),
                    toolResult.success(),
                    System.currentTimeMillis() - stepStartTime);

            // 继续执行
            String observation =
                    toolResult.success()
                            ? (toolResult.data() != null ? toolResult.data().toString() : "null")
                            : "Error: " + toolResult.error();
            return AgentStepResult.continueExecution("Observation: " + observation);
        }

        // 无工具调用，返回最终结果
        return AgentStepResult.done(response.output());
    }

    /** 执行工具 */
    protected ToolResult executeTool(ToolCallRequest toolCall, AgentContext context) {
        long startTime = System.currentTimeMillis();
        try {
            ToolResult result = toolRegistry.execute(toolCall.name(), toolCall.params());
            long durationMs = System.currentTimeMillis() - startTime;
            // 审计：工具调用
            if (auditService != null) {
                auditService.recordToolCall(
                        context.getTraceId(),
                        context.getUserId(),
                        getMetadata().name(),
                        toolCall.name(),
                        toolCall.params(),
                        result.success(),
                        durationMs);
            }
            // 指标：工具调用
            if (metricsService != null) {
                metricsService.recordToolCall(toolCall.name(), result.success(), durationMs);
            }
            return result;
        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            log.error(
                    "[{}] 工具执行失败: {} - {}", getMetadata().name(), toolCall.name(), e.getMessage());
            // 审计：工具调用失败
            if (auditService != null) {
                auditService.recordToolCall(
                        context.getTraceId(),
                        context.getUserId(),
                        getMetadata().name(),
                        toolCall.name(),
                        toolCall.params(),
                        false,
                        durationMs);
            }
            // 指标：工具调用失败
            if (metricsService != null) {
                metricsService.recordToolCall(toolCall.name(), false, durationMs);
            }
            return ToolResult.failure("工具执行失败: " + e.getMessage());
        }
    }

    /** 构建执行器（子类实现） */
    protected abstract AgentExecutor buildExecutor(AgentContext context);

    /** 构建系统提示词（子类实现） */
    protected abstract String buildSystemPrompt();

    /**
     * 执行委托工具
     *
     * <p>处理 delegate_to_agent 工具调用，发送 AgentCall/AgentResult 事件。 支持多 Agent 协作场景。
     *
     * @param toolCall 工具调用请求
     * @param context 执行上下文
     * @param sink 事件接收器
     * @param sequenceCounter 序列号计数器
     * @param stepIndex 步骤索引
     * @return 工具结果
     */
    protected ToolResult executeDelegationTool(
            ToolCallRequest toolCall,
            AgentContext context,
            FluxSink<AgentEvent> sink,
            AtomicInteger sequenceCounter,
            int stepIndex) {

        String targetAgent = toolCall.getString("targetAgent");
        String input = toolCall.getString("input");

        log.info(
                "[{}] 执行委托: targetAgent={}, input={}",
                getMetadata().name(),
                targetAgent,
                truncate(input, 100));

        if (targetAgent == null || targetAgent.isBlank()) {
            return ToolResult.failure("目标 Agent 名称不能为空");
        }

        if (input == null || input.isBlank()) {
            return ToolResult.failure("委托输入内容不能为空");
        }

        // 审计：Agent 委托
        if (auditService != null) {
            auditService.recordAgentDelegation(
                    context.getTraceId(),
                    context.getUserId(),
                    getMetadata().name(),
                    targetAgent,
                    input);
        }

        // 设置源 Agent 名称到上下文
        context.setVariable("sourceAgentName", getMetadata().name());

        // 调用委托服务
        AgentDelegationService.DelegationResult result =
                delegationService.delegate(
                        targetAgent, input, context, sink, sequenceCounter, stepIndex);

        if (result.success()) {
            // 委托成功，返回目标 Agent 的输出作为工具结果
            return ToolResult.success(result.output());
        } else {
            return ToolResult.failure("委托失败: " + result.error());
        }
    }

    @Override
    public List<ToolDefinition> getAvailableTools() {
        return toolRegistry.getAllTools();
    }

    // ===== 辅助方法 =====

    protected void emit(FluxSink<AgentEvent> sink, AgentEvent event) {
        sink.next(event);
    }

    protected String generateConfirmationId() {
        return "confirm_" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected String truncate(String text, int maxLength) {
        if (text == null) return null;
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}
