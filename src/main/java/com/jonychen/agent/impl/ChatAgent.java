package com.jonychen.agent.impl;

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.jonychen.agent.core.AbstractAgent;
import com.jonychen.agent.core.AgentContext;
import com.jonychen.agent.core.AgentEvent;
import com.jonychen.agent.core.AgentExecutor;
import com.jonychen.agent.core.AgentMetadata;
import com.jonychen.agent.core.AgentRequest;
import com.jonychen.agent.core.AgentResult;
import com.jonychen.agent.core.StepType;
import com.jonychen.agent.core.TokenUsage;
import com.jonychen.observability.trace.AgentTraceService;
import com.jonychen.tool.ToolRegistry;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import reactor.core.publisher.Flux;

/**
 * 通用对话助手 Agent
 *
 * <p>作为 RouterAgent 的兜底 Agent，处理普通对话请求。 不使用工具，仅进行纯对话交互。
 *
 * <h2>核心能力</h2>
 *
 * <ul>
 *   <li>回答用户问题
 *   <li>进行日常对话
 *   <li>提供一般性建议
 * </ul>
 *
 * <h2>路由特性</h2>
 *
 * <p>canHandle() 始终返回最低置信度 0.1，作为其他 Agent 不匹配时的兜底选择。
 *
 * @author jonychen
 */
@Component
public class ChatAgent extends AbstractAgent {

    private static final Logger log = LoggerFactory.getLogger(ChatAgent.class);

    private static final String SYSTEM_PROMPT =
            """
            你是一个友好的 AI 助手，具备以下能力：
            - 回答用户问题
            - 进行日常对话
            - 提供信息和建议

            当用户的问题需要特定工具（如数据库查询、测试生成、Prompt 优化）时，
            建议用户使用更专业的 Agent 来完成任务。

            请用简洁、准确的语言回答用户的问题。
            """;

    public ChatAgent(
            ChatModel chatModel, ToolRegistry toolRegistry, AgentTraceService traceService) {
        super(chatModel, toolRegistry, traceService);
        log.info("[ChatAgent] 初始化完成，作为通用对话 Agent");
    }

    @Override
    public AgentMetadata getMetadata() {
        return AgentMetadata.chat();
    }

    @Override
    public double canHandle(AgentRequest request) {
        // ChatAgent 作为兜底，置信度始终为最低
        // 这样当其他 Agent 都不匹配时，会路由到这里
        return 0.1;
    }

    @Override
    protected String buildSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    @Override
    protected AgentExecutor buildExecutor(AgentContext context) {
        // ChatAgent 不使用工具
        return new AgentExecutor(chatModel, SYSTEM_PROMPT, java.util.List.of(), java.util.Map.of());
    }

    @Override
    public AgentResult execute(AgentRequest request, AgentContext context) {
        log.info("[ChatAgent] 开始执行: traceId={}", context.getTraceId());
        long startTime = System.currentTimeMillis();

        try {
            // 构建消息
            var messages =
                    java.util.List.of(
                            new SystemMessage(SYSTEM_PROMPT), new UserMessage(request.userInput()));

            // 调用 LLM
            var llmRequest = ChatRequest.builder().messages(messages).build();

            ChatResponse response = chatModel.chat(llmRequest);
            String output = response.aiMessage().text();

            // 提取 Token 使用量
            TokenUsage tokenUsage = extractTokenUsage(response);
            long durationMs = System.currentTimeMillis() - startTime;

            log.info(
                    "[ChatAgent] 执行完成: traceId={}, durationMs={}, tokens={}",
                    context.getTraceId(),
                    durationMs,
                    tokenUsage.totalTokens());

            return AgentResult.success(
                    context.getTraceId(), output, java.util.List.of(), tokenUsage, durationMs);

        } catch (Exception e) {
            log.error("[ChatAgent] 执行失败: traceId={}", context.getTraceId(), e);
            return AgentResult.failure(
                    context.getTraceId(),
                    e.getMessage(),
                    java.util.List.of(),
                    System.currentTimeMillis() - startTime);
        }
    }

    @Override
    public Flux<AgentEvent> executeStream(AgentRequest request, AgentContext context) {
        log.info("[ChatAgent] 开始流式执行: traceId={}", context.getTraceId());

        return Flux.create(
                sink -> {
                    long startTime = System.currentTimeMillis();
                    AtomicInteger seqCounter = context.getSequenceCounter();
                    String traceId = context.getTraceId();

                    try {
                        // 发送步骤开始事件
                        sink.next(
                                AgentEvent.stepStart(
                                        traceId,
                                        seqCounter.getAndIncrement(),
                                        0,
                                        StepType.LLM_CALL,
                                        "chat"));

                        // 构建消息
                        var messages =
                                java.util.List.of(
                                        new SystemMessage(SYSTEM_PROMPT),
                                        new UserMessage(request.userInput()));

                        // 调用 LLM
                        var llmRequest = ChatRequest.builder().messages(messages).build();

                        ChatResponse response = chatModel.chat(llmRequest);
                        String output = response.aiMessage().text();

                        // 发送思考过程
                        sink.next(
                                AgentEvent.thought(
                                        traceId, seqCounter.getAndIncrement(), 0, output));

                        // 提取 Token 使用量
                        TokenUsage tokenUsage = extractTokenUsage(response);
                        long durationMs = System.currentTimeMillis() - startTime;

                        // 发送步骤结束事件
                        sink.next(
                                AgentEvent.stepEnd(
                                        traceId,
                                        seqCounter.getAndIncrement(),
                                        0,
                                        true,
                                        "对话完成",
                                        durationMs));

                        // 发送完成事件
                        sink.next(
                                AgentEvent.done(
                                        traceId,
                                        seqCounter.getAndIncrement(),
                                        "chat",
                                        output,
                                        1,
                                        tokenUsage,
                                        durationMs));

                        log.info(
                                "[ChatAgent] 流式执行完成: traceId={}, durationMs={}",
                                traceId,
                                durationMs);
                        sink.complete();

                    } catch (Exception e) {
                        log.error("[ChatAgent] 流式执行失败: traceId={}", traceId, e);
                        sink.next(
                                AgentEvent.error(
                                        traceId,
                                        seqCounter.getAndIncrement(),
                                        "CHAT_ERROR",
                                        e.getMessage(),
                                        null,
                                        false));
                        sink.error(e);
                    }
                });
    }

    private TokenUsage extractTokenUsage(ChatResponse response) {
        var usage = response.tokenUsage();
        if (usage != null) {
            return TokenUsage.of(usage.inputTokenCount(), usage.outputTokenCount());
        }
        return TokenUsage.empty();
    }
}
