package com.jonychen.agent.core;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jonychen.observability.trace.AgentTraceService;
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

    protected AbstractAgent(
            ChatModel chatModel, ToolRegistry toolRegistry, AgentTraceService traceService) {
        this.chatModel = chatModel;
        this.toolRegistry = toolRegistry;
        this.traceService = traceService;
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
                                stepResult.summary()));

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
            ToolResult toolResult = executeTool(toolCall, context);

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
                                toolResult.riskLevel()));
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
                            toolResult.error()));
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
        try {
            return toolRegistry.execute(toolCall.name(), toolCall.params());
        } catch (Exception e) {
            log.error(
                    "[{}] 工具执行失败: {} - {}", getMetadata().name(), toolCall.name(), e.getMessage());
            return ToolResult.failure("工具执行失败: " + e.getMessage());
        }
    }

    /** 构建执行器（子类实现） */
    protected abstract AgentExecutor buildExecutor(AgentContext context);

    /** 构建系统提示词（子类实现） */
    protected abstract String buildSystemPrompt();

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
