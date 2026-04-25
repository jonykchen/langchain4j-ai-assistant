package com.jonychen.agent.impl;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jonychen.agent.core.*;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

/**
 * 路由 Agent
 *
 * <p>基于 LLM 智能路由，分析用户意图并选择最合适的 Agent 处理请求。
 *
 * <h2>核心职责</h2>
 *
 * <ul>
 *   <li>分析用户输入的意图和语义
 *   <li>从注册的 Agent 中选择最合适的
 *   <li>返回置信度分数和选择理由
 *   <li>低置信度时请求用户确认
 * </ul>
 *
 * <h2>路由策略</h2>
 *
 * <ul>
 *   <li>置信度 >= 0.9：直接路由，无需确认
 *   <li>置信度 0.7-0.9：直接路由，但展示理由
 *   <li>置信度 < 0.7：发送确认请求，让用户选择
 * </ul>
 *
 * <h2>排除规则</h2>
 *
 * <ul>
 *   <li>RouterAgent 不路由到自己
 *   <li>ChatAgent 作为兜底选择
 * </ul>
 *
 * @author jonychen
 */
public class RouterAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(RouterAgent.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 路由置信度阈值：低于此值需要用户确认 */
    private static final double CONFIRMATION_THRESHOLD = 0.7;

    /** 路由超时时间 */
    private static final Duration ROUTING_TIMEOUT = Duration.ofSeconds(10);

    private final ChatModel chatModel;
    private final AgentRegistry agentRegistry;

    /** 缓存的 Agent 信息（用于构建路由提示词） */
    private volatile String agentInfoCache;

    private volatile long agentInfoCacheTime;
    private static final long CACHE_TTL_MS = 60000; // 1 分钟缓存

    /**
     * 构造函数
     *
     * @param chatModel 聊天模型
     * @param agentRegistry Agent 注册表
     */
    public RouterAgent(ChatModel chatModel, AgentRegistry agentRegistry) {
        this.chatModel = chatModel;
        this.agentRegistry = agentRegistry;
        log.info("[RouterAgent] 初始化完成");
    }

    @Override
    public AgentMetadata getMetadata() {
        return AgentMetadata.router();
    }

    /**
     * 路由 Agent 的 canHandle 始终返回 0
     *
     * <p>RouterAgent 不参与 canHandle 路由竞争，而是由 AgentOrchestrator 直接调用。
     */
    @Override
    public double canHandle(AgentRequest request) {
        return 0.0;
    }

    @Override
    public AgentResult execute(AgentRequest request, AgentContext context) {
        log.info("[RouterAgent] 开始路由: input={}", truncate(request.userInput(), 100));

        try {
            RoutingDecision decision = route(request);

            if (decision.needsConfirmation()) {
                // 需要用户确认，返回待确认状态
                return AgentResult.pendingConfirmation(
                        context.getTraceId(),
                        "请确认路由选择",
                        decision.targetAgent(),
                        decision.alternativeAgent(),
                        decision.getConfidencePercent());
            }

            return AgentResult.success(
                    context.getTraceId(), "路由到 " + decision.targetAgent(), List.of(), null, 0);

        } catch (Exception e) {
            log.error("[RouterAgent] 路由失败: {}", e.getMessage(), e);
            return AgentResult.failure(context.getTraceId(), "路由失败: " + e.getMessage());
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
                        log.error("[RouterAgent] 流式执行失败: {}", e.getMessage(), e);
                        sink.error(e);
                    }
                },
                FluxSink.OverflowStrategy.BUFFER);
    }

    /**
     * 流式执行核心逻辑
     *
     * <p>流程：
     *
     * <ol>
     *   <li>发送步骤开始事件
     *   <li>调用 LLM 获取路由决策
     *   <li>判断是否需要用户确认
     *   <li>发送路由结果事件
     * </ol>
     */
    private void executeWithSink(
            AgentRequest request, AgentContext context, FluxSink<AgentEvent> sink) {
        String traceId = context.getTraceId();
        int seqNum = context.getSequenceCounter().getAndIncrement();
        int stepIndex = 0;

        log.info("[RouterAgent] 开始路由分析: traceId={}", traceId);

        // 发送步骤开始事件
        sink.next(AgentEvent.stepStart(traceId, seqNum, stepIndex, StepType.LLM_CALL, "router"));
        seqNum = context.getSequenceCounter().getAndIncrement();

        try {
            // 执行路由
            RoutingDecision decision = route(request);

            // 发送思考过程事件
            String thought =
                    String.format(
                            "分析用户意图: \"%s\"\n选择 Agent: %s (置信度: %.0f%%)\n理由: %s",
                            truncate(request.userInput(), 50),
                            decision.targetAgent(),
                            decision.confidence() * 100,
                            decision.reason());
            sink.next(AgentEvent.thought(traceId, seqNum, stepIndex, thought));
            seqNum = context.getSequenceCounter().getAndIncrement();

            // 判断是否需要确认
            if (decision.needsConfirmation()) {
                log.info(
                        "[RouterAgent] 低置信度路由，需要确认: agent={}, confidence={}",
                        decision.targetAgent(),
                        decision.confidence());

                // 发送确认请求事件
                sink.next(
                        AgentEvent.confirmationRequired(
                                traceId,
                                seqNum,
                                stepIndex,
                                "routing_confirm",
                                "路由选择",
                                buildConfirmationMessage(decision),
                                com.jonychen.tool.RiskLevel.LOW,
                                java.util.Map.of("targetAgent", decision.targetAgent())));
                // 注意：这里不继续执行，等待 AgentOrchestrator 处理确认
            } else {
                // 发送路由结果事件
                sink.next(
                        AgentEvent.agentResult(
                                traceId,
                                seqNum,
                                stepIndex,
                                "router",
                                String.format(
                                        "路由到 %s (置信度: %d%%)",
                                        decision.targetAgent(), decision.getConfidencePercent()),
                                true));
                seqNum = context.getSequenceCounter().getAndIncrement();

                // 发送完成事件
                sink.next(
                        AgentEvent.done(
                                traceId,
                                seqNum,
                                "router",
                                decision.targetAgent(),
                                1,
                                TokenUsage.empty(),
                                0));
            }

            log.info(
                    "[RouterAgent] 路由完成: agent={}, confidence={}",
                    decision.targetAgent(),
                    decision.confidence());

        } catch (Exception e) {
            log.error("[RouterAgent] 路由失败: {}", e.getMessage(), e);
            sink.next(
                    AgentEvent.error(
                            traceId,
                            seqNum,
                            "ROUTING_ERROR",
                            "路由失败: " + e.getMessage(),
                            null,
                            true));
        }
    }

    /**
     * 执行路由决策
     *
     * <p>调用 LLM 分析用户意图，返回路由决策。
     *
     * @param request 执行请求
     * @return 路由决策
     */
    public RoutingDecision route(AgentRequest request) {
        long startTime = System.currentTimeMillis();

        // 构建路由提示词
        String systemPrompt = buildRoutingSystemPrompt();
        String userPrompt = buildRoutingUserPrompt(request);

        log.debug("[RouterAgent] 系统提示词长度: {}", systemPrompt.length());
        log.debug("[RouterAgent] 用户提示词: {}", userPrompt);

        try {
            // 构建 ChatRequest
            ChatRequest chatRequest =
                    ChatRequest.builder()
                            .messages(
                                    List.of(
                                            new SystemMessage(systemPrompt),
                                            new UserMessage(userPrompt)))
                            .build();

            // 调用 LLM
            ChatResponse response = chatModel.chat(chatRequest);
            AiMessage aiMessage = response.aiMessage();

            String responseText = aiMessage.text();
            log.debug("[RouterAgent] LLM 响应: {}", responseText);
            log.info("[RouterAgent] LLM 调用耗时: {}ms", System.currentTimeMillis() - startTime);

            // 解析路由决策
            return parseRoutingDecision(responseText);

        } catch (Exception e) {
            log.error("[RouterAgent] LLM 调用失败: {}", e.getMessage(), e);
            // 降级：返回 ChatAgent 作为兜底
            return RoutingDecision.of("chat", 0.5, "路由失败，使用通用对话处理: " + e.getMessage(), null);
        }
    }

    /**
     * 构建路由系统提示词
     *
     * <p>包含：
     *
     * <ul>
     *   <li>角色定义
     *   <li>可用 Agent 列表及其能力
     *   <li>输出格式要求（JSON Schema）
     * </ul>
     */
    private String buildRoutingSystemPrompt() {
        // 获取 Agent 信息（使用缓存）
        String agentInfo = getAgentInfo();

        return """
                你是一个智能路由器，负责分析用户意图并选择最合适的 Agent 处理请求。

                ## 可用 Agent 列表

                %s

                ## 你的任务

                1. 分析用户输入的意图和语义
                2. 从上述 Agent 中选择最合适的一个
                3. 返回 JSON 格式的路由决策

                ## 输出格式

                必须严格返回以下 JSON 格式，不要包含任何其他内容：

                ```json
                {
                    "targetAgent": "agent名称",
                    "confidence": 0.85,
                    "reason": "选择理由",
                    "alternativeAgent": "备选agent（可选）"
                }
                ```

                ## 置信度说明

                - 0.9-1.0：非常确定，用户意图明确匹配 Agent 能力
                - 0.7-0.9：比较确定，有一定匹配度
                - 0.5-0.7：不太确定，需要用户确认
                - <0.5：非常不确定，建议使用 ChatAgent

                ## 路由规则

                1. 如果用户意图不明确，选择 ChatAgent
                2. 如果涉及数据处理、SQL查询、图表，选择 DataAgent
                3. 如果涉及模型健康、熔断器、运维操作，选择 OpsAgent
                4. 不要选择 RouterAgent（你自己）
                5. 始终返回有效的 JSON 格式
                """
                .formatted(agentInfo);
    }

    /**
     * 构建路由用户提示词
     *
     * @param request 执行请求
     * @return 用户提示词
     */
    private String buildRoutingUserPrompt(AgentRequest request) {
        return "用户输入: " + request.userInput() + "\n\n请分析用户意图并返回路由决策 JSON。";
    }

    /**
     * 获取 Agent 信息（带缓存）
     *
     * <p>缓存 Agent 列表信息，避免每次路由都重新构建。
     */
    private String getAgentInfo() {
        long now = System.currentTimeMillis();
        if (agentInfoCache != null && (now - agentInfoCacheTime) < CACHE_TTL_MS) {
            return agentInfoCache;
        }

        // 构建 Agent 信息
        List<String> agentDescriptions =
                agentRegistry.getAllMetadata().stream()
                        .filter(meta -> !meta.agentType().equals(AgentType.ROUTER)) // 排除自己
                        .map(
                                meta ->
                                        String.format(
                                                "- %s (%s): %s",
                                                meta.name(),
                                                meta.displayName(),
                                                meta.description()))
                        .collect(Collectors.toList());

        agentInfoCache = String.join("\n", agentDescriptions);
        agentInfoCacheTime = now;

        log.debug("[RouterAgent] 刷新 Agent 信息缓存: {}", agentInfoCache);
        return agentInfoCache;
    }

    /**
     * 解析路由决策
     *
     * <p>从 LLM 响应中解析 JSON 格式的路由决策。
     *
     * @param responseText LLM 响应文本
     * @return 路由决策
     */
    private RoutingDecision parseRoutingDecision(String responseText) {
        // 尝试提取 JSON 块
        String json = extractJson(responseText);

        if (json == null) {
            log.warn("[RouterAgent] 无法从响应中提取 JSON: {}", responseText);
            return RoutingDecision.of("chat", 0.5, "无法解析路由决策，使用默认对话", null);
        }

        try {
            return MAPPER.readValue(json, RoutingDecision.class);
        } catch (JsonProcessingException e) {
            log.warn("[RouterAgent] JSON 解析失败: {} | 原文: {}", e.getMessage(), json);
            return RoutingDecision.of("chat", 0.5, "路由决策解析失败", null);
        }
    }

    /**
     * 从文本中提取 JSON
     *
     * <p>支持以下格式：
     *
     * <ul>
     *   <li>纯 JSON：{"targetAgent": ...}
     *   <li>Markdown 代码块：```json ... ```
     * </ul>
     *
     * @param text 文本
     * @return JSON 字符串，提取失败返回 null
     */
    private String extractJson(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        text = text.trim();

        // 尝试提取 Markdown 代码块中的 JSON
        if (text.contains("```json")) {
            int start = text.indexOf("```json") + 7;
            int end = text.indexOf("```", start);
            if (end > start) {
                return text.substring(start, end).trim();
            }
        }

        // 尝试提取 ``` 代码块
        if (text.contains("```")) {
            int start = text.indexOf("```") + 3;
            // 跳过可能的语言标识
            while (start < text.length() && text.charAt(start) != '{') {
                start++;
            }
            int end = text.indexOf("```", start);
            if (end > start) {
                return text.substring(start, end).trim();
            }
        }

        // 尝试直接解析（纯 JSON）
        if (text.startsWith("{") && text.endsWith("}")) {
            return text;
        }

        // 尝试提取第一个 JSON 对象
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }

        return null;
    }

    /**
     * 构建确认消息
     *
     * @param decision 路由决策
     * @return 确认消息
     */
    private String buildConfirmationMessage(RoutingDecision decision) {
        StringBuilder sb = new StringBuilder();
        sb.append("我判断您的问题应该由 ")
                .append(decision.targetAgent())
                .append(" 处理（置信度: ")
                .append(decision.getConfidencePercent())
                .append("%）\n");
        sb.append("理由：").append(decision.reason()).append("\n");

        if (decision.alternativeAgent() != null) {
            sb.append("备选 Agent：").append(decision.alternativeAgent());
        }

        return sb.toString();
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return null;
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}
