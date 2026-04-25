package com.jonychen.agent.core;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;

/**
 * Agent 委托服务
 *
 * <p>支持一个 Agent 在执行过程中委托给另一个 Agent 处理子任务。 实现多 Agent 协作的核心机制。
 *
 * <h2>委托流程</h2>
 *
 * <ol>
 *   <li>源 Agent 调用 delegate_to_agent 工具
 *   <li>服务创建新的 AgentRequest 给目标 Agent
 *   <li>执行目标 Agent 并收集结果
 *   <li>返回结果给源 Agent
 * </ol>
 *
 * <h2>安全控制</h2>
 *
 * <ul>
 *   <li>委托深度限制（最大 3 层，防止无限递归）
 *   <li>权限检查（委托者必须有执行目标 Agent 的权限）
 *   <li>防止自己委托给自己
 * </ul>
 *
 * @author jonychen
 */
@Service
public class AgentDelegationService {

    private static final Logger log = LoggerFactory.getLogger(AgentDelegationService.class);

    /** 最大委托深度（防止无限递归） */
    private static final int MAX_DELEGATION_DEPTH = 3;

    private AgentRegistry agentRegistry;
    private final AgentMetricsService metricsService;

    public AgentDelegationService(AgentMetricsService metricsService) {
        this.metricsService = metricsService;
        log.info("[AgentDelegationService] 初始化完成，最大委托深度: {}", MAX_DELEGATION_DEPTH);
    }

    /** 延迟注入 AgentRegistry（打破循环依赖） */
    @Autowired
    @Lazy
    public void setAgentRegistry(AgentRegistry agentRegistry) {
        this.agentRegistry = agentRegistry;
        log.debug("[AgentDelegationService] AgentRegistry 已注入");
    }

    /**
     * 委托给目标 Agent 执行
     *
     * <p>同步执行目标 Agent，返回结果。 用于工具调用场景（delegate_to_agent 工具）。
     *
     * @param targetAgentName 目标 Agent 名称
     * @param input 输入内容
     * @param sourceContext 源 Agent 上下文
     * @param sink 事件接收器（用于发送 AgentCall/AgentResult 事件）
     * @param sequenceCounter 序列号计数器
     * @param stepIndex 当前步骤索引
     * @return 委托结果
     */
    public DelegationResult delegate(
            String targetAgentName,
            String input,
            AgentContext sourceContext,
            reactor.core.publisher.FluxSink<AgentEvent> sink,
            AtomicInteger sequenceCounter,
            int stepIndex) {

        String traceId = sourceContext.getTraceId();
        long startTime = System.currentTimeMillis();

        log.info(
                "[AgentDelegationService] 开始委托: target={}, input={}",
                targetAgentName,
                truncate(input, 100));

        // 检查委托深度
        Integer currentDepth = (Integer) sourceContext.getVariable("delegationDepth");
        int depth = currentDepth != null ? currentDepth : 0;

        if (depth >= MAX_DELEGATION_DEPTH) {
            log.warn("[AgentDelegationService] 委托深度超限: depth={}", depth);
            return DelegationResult.failure("委托深度超限，最大允许 " + MAX_DELEGATION_DEPTH + " 层");
        }

        // 获取目标 Agent
        Agent targetAgent = agentRegistry.getAgent(targetAgentName).orElse(null);
        if (targetAgent == null) {
            log.warn("[AgentDelegationService] 目标 Agent 不存在: {}", targetAgentName);
            return DelegationResult.failure("目标 Agent 不存在: " + targetAgentName);
        }

        // 检查是否委托给自己
        String sourceAgentName = (String) sourceContext.getVariable("sourceAgentName");
        if (targetAgentName.equals(sourceAgentName)) {
            log.warn("[AgentDelegationService] 不能委托给自己: {}", targetAgentName);
            return DelegationResult.failure("不能委托给自己");
        }

        // 发送 AgentCall 事件
        if (sink != null) {
            sink.next(
                    AgentEvent.agentCall(
                            traceId,
                            sequenceCounter.getAndIncrement(),
                            stepIndex,
                            targetAgentName,
                            input));
        }

        try {
            // 创建委托请求上下文
            AgentContext delegateContext = createDelegateContext(sourceContext, depth + 1);

            // 创建委托请求
            AgentRequest delegateRequest =
                    new AgentRequest(
                            sourceContext.getSessionId(),
                            sourceContext.getUserId(),
                            input,
                            java.util.Collections.emptyMap(),
                            AgentRequestOptions.defaults(),
                            null,
                            null);

            // 执行目标 Agent（同步）
            AgentResult result = targetAgent.execute(delegateRequest, delegateContext);

            long durationMs = System.currentTimeMillis() - startTime;

            // 发送 AgentResult 事件
            if (sink != null) {
                sink.next(
                        AgentEvent.agentResult(
                                traceId,
                                sequenceCounter.getAndIncrement(),
                                stepIndex,
                                targetAgentName,
                                result.output(),
                                result.isSuccess()));
            }

            if (result.isSuccess()) {
                log.info(
                        "[AgentDelegationService] 委托成功: target={}, duration={}ms",
                        targetAgentName,
                        durationMs);
                // 指标：委托成功
                metricsService.recordDelegation(sourceAgentName, targetAgentName, true, depth);
                return DelegationResult.success(result.output(), targetAgentName, durationMs);
            } else {
                log.warn(
                        "[AgentDelegationService] 委托失败: target={}, error={}",
                        targetAgentName,
                        result.errorMessage());
                // 指标：委托失败
                metricsService.recordDelegation(sourceAgentName, targetAgentName, false, depth);
                return DelegationResult.failure(result.errorMessage(), targetAgentName);
            }

        } catch (Exception e) {
            log.error(
                    "[AgentDelegationService] 委托异常: target={}, error={}",
                    targetAgentName,
                    e.getMessage(),
                    e);

            // 发送 AgentResult 事件（失败）
            if (sink != null) {
                sink.next(
                        AgentEvent.agentResult(
                                traceId,
                                sequenceCounter.getAndIncrement(),
                                stepIndex,
                                targetAgentName,
                                e.getMessage(),
                                false));
            }

            // 指标：委托异常
            metricsService.recordDelegation(sourceAgentName, targetAgentName, false, depth);
            return DelegationResult.failure("委托执行异常: " + e.getMessage(), targetAgentName);
        }
    }

    /**
     * 委托给目标 Agent 执行（流式）
     *
     * <p>返回 Flux 流，用于需要实时反馈的场景。
     *
     * @param targetAgentName 目标 Agent 名称
     * @param input 输入内容
     * @param sourceContext 源 Agent 上下文
     * @return 事件流
     */
    public Flux<AgentEvent> delegateStream(
            String targetAgentName, String input, AgentContext sourceContext) {

        String traceId = sourceContext.getTraceId();
        long startTime = System.currentTimeMillis();

        log.info(
                "[AgentDelegationService] 开始流式委托: target={}, input={}",
                targetAgentName,
                truncate(input, 100));

        // 检查委托深度
        Integer currentDepth = (Integer) sourceContext.getVariable("delegationDepth");
        int depth = currentDepth != null ? currentDepth : 0;

        if (depth >= MAX_DELEGATION_DEPTH) {
            log.warn("[AgentDelegationService] 委托深度超限: depth={}", depth);
            return Flux.just(
                    AgentEvent.error(
                            traceId, 0, "DELEGATION_DEPTH_EXCEEDED", "委托深度超限", null, false));
        }

        // 获取目标 Agent
        Agent targetAgent = agentRegistry.getAgent(targetAgentName).orElse(null);
        if (targetAgent == null) {
            return Flux.just(
                    AgentEvent.error(
                            traceId,
                            0,
                            "AGENT_NOT_FOUND",
                            "Agent 不存在: " + targetAgentName,
                            null,
                            false));
        }

        // 创建委托上下文和请求
        AgentContext delegateContext = createDelegateContext(sourceContext, depth + 1);
        AgentRequest delegateRequest =
                new AgentRequest(
                        sourceContext.getSessionId(),
                        sourceContext.getUserId(),
                        input,
                        java.util.Collections.emptyMap(),
                        AgentRequestOptions.defaults(),
                        null,
                        null);

        // 执行目标 Agent（流式）
        return targetAgent
                .executeStream(delegateRequest, delegateContext)
                .doOnComplete(
                        () ->
                                log.info(
                                        "[AgentDelegationService] 流式委托完成: target={}, duration={}ms",
                                        targetAgentName,
                                        System.currentTimeMillis() - startTime))
                .doOnError(
                        e ->
                                log.error(
                                        "[AgentDelegationService] 流式委托失败: target={}, error={}",
                                        targetAgentName,
                                        e.getMessage()));
    }

    /**
     * 创建委托上下文
     *
     * <p>继承源上下文的部分信息，并设置委托深度。
     */
    private AgentContext createDelegateContext(AgentContext sourceContext, int delegationDepth) {
        AgentContext context =
                AgentContext.of(
                        sourceContext.getSessionId(),
                        sourceContext.getUserId(),
                        AgentType.ROUTER, // 委托时使用 ROUTER 类型
                        sourceContext.getOptions());
        context.setVariable("delegationDepth", delegationDepth);
        context.setVariable("sourceTraceId", sourceContext.getTraceId());
        return context;
    }

    /**
     * 获取可委托的 Agent 列表
     *
     * <p>返回除 RouterAgent 和自己之外的所有 Agent。
     *
     * @param excludeAgentName 要排除的 Agent 名称
     * @return 可委托的 Agent 列表
     */
    public List<AgentMetadata> getDelegatableAgents(String excludeAgentName) {
        return agentRegistry.getAllMetadata().stream()
                .filter(meta -> !meta.agentType().equals(AgentType.ROUTER))
                .filter(meta -> !meta.name().equals(excludeAgentName))
                .toList();
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return null;
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    /**
     * 委托结果
     *
     * @param success 是否成功
     * @param output 输出内容
     * @param targetAgent 目标 Agent 名称
     * @param durationMs 耗时
     * @param error 错误信息
     */
    public record DelegationResult(
            boolean success, String output, String targetAgent, long durationMs, String error) {

        public static DelegationResult success(String output, String targetAgent, long durationMs) {
            return new DelegationResult(true, output, targetAgent, durationMs, null);
        }

        public static DelegationResult failure(String error) {
            return new DelegationResult(false, null, null, 0, error);
        }

        public static DelegationResult failure(String error, String targetAgent) {
            return new DelegationResult(false, null, targetAgent, 0, error);
        }
    }
}
