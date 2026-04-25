package com.jonychen.agent.core;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Agent 执行指标监控服务
 *
 * <p>提供 Agent 系统的 Prometheus 指标:
 *
 * <h2>核心指标</h2>
 *
 * <ul>
 *   <li>执行计数：按 Agent、状态（成功/失败/取消）
 *   <li>执行时长：按 Agent 分布（P50/P90/P99）
 *   <li>活跃执行数：当前正在执行的 Agent 数量
 *   <li>工具调用：按工具名称、状态
 *   <li>路由决策：置信度分布、路由选择
 *   <li>委托调用：跨 Agent 委托次数
 * </ul>
 *
 * @author jonychen
 */
@Service
public class AgentMetricsService {

    private static final Logger log = LoggerFactory.getLogger(AgentMetricsService.class);

    private final MeterRegistry meterRegistry;

    // ==================== 执行计数指标 ====================

    /** Agent 执行总计数（按 Agent 和状态分组） */
    private final Counter executionsTotal;

    private final Counter executionsSuccess;
    private final Counter executionsFailure;
    private final Counter executionsCancelled;

    /** 按 Agent 细分的计数器 */
    private final Map<String, Counter> agentSuccessCounters = new ConcurrentHashMap<>();

    private final Map<String, Counter> agentFailureCounters = new ConcurrentHashMap<>();

    // ==================== 活跃执行指标 ====================

    /** 当前活跃执行数 */
    private final AtomicLong activeExecutions = new AtomicLong(0);

    /** 活跃执行 Gauge */
    private final Gauge activeExecutionsGauge;

    /** 按 Agent 细分的活跃执行数 */
    private final Map<String, AtomicLong> agentActiveCounts = new ConcurrentHashMap<>();

    // ==================== 执行时长指标 ====================

    /** 执行时长 Timer（全局） */
    private final Timer executionDurationTimer;

    /** 按 Agent 细分的 Timer */
    private final Map<String, Timer> agentDurationTimers = new ConcurrentHashMap<>();

    // ==================== 工具调用指标 ====================

    /** 工具调用总计数 */
    private final Counter toolCallsTotal;

    private final Counter toolCallsSuccess;
    private final Counter toolCallsFailure;

    /** 按工具细分的计数器 */
    private final Map<String, Counter> toolCallCounters = new ConcurrentHashMap<>();

    /** 工具执行时长 */
    private final Timer toolExecutionTimer;

    // ==================== 路由指标 ====================

    /** 路由决策计数 */
    private final Counter routingTotal;

    /** 低置信度路由计数 */
    private final Counter routingLowConfidence;

    /** 路由决策后的确认计数 */
    private final Counter routingConfirmed;

    private final Counter routingRejected;

    // ==================== 委托指标 ====================

    /** 委托调用计数 */
    private final Counter delegationsTotal;

    private final Counter delegationsSuccess;
    private final Counter delegationsFailure;

    /** 委托深度分布 */
    private final Map<Integer, Counter> delegationDepthCounters = new ConcurrentHashMap<>();

    // ==================== 限流指标 ====================

    /** 限流触发计数 */
    private final Counter rateLimitHitsTotal;

    /** 按限流类型细分 */
    private final Map<String, Counter> rateLimitCounters = new ConcurrentHashMap<>();

    // ==================== 步骤指标 ====================

    /** 步骤执行计数 */
    private final Counter stepsTotal;

    /** 按 Agent 细分的步骤分布 */
    private final Map<String, Counter> agentStepCounters = new ConcurrentHashMap<>();

    public AgentMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // 初始化执行计数器
        this.executionsTotal =
                Counter.builder("agent_executions_total")
                        .description("Agent 执行总数")
                        .tag("status", "total")
                        .register(meterRegistry);

        this.executionsSuccess =
                Counter.builder("agent_executions_total")
                        .description("Agent 执行成功数")
                        .tag("status", "success")
                        .register(meterRegistry);

        this.executionsFailure =
                Counter.builder("agent_executions_total")
                        .description("Agent 执行失败数")
                        .tag("status", "failure")
                        .register(meterRegistry);

        this.executionsCancelled =
                Counter.builder("agent_executions_total")
                        .description("Agent 执行取消数")
                        .tag("status", "cancelled")
                        .register(meterRegistry);

        // 初始化活跃执行 Gauge
        this.activeExecutionsGauge =
                Gauge.builder("agent_active_executions", activeExecutions, AtomicLong::get)
                        .description("当前活跃的 Agent 执行数")
                        .register(meterRegistry);

        // 初始化执行时长 Timer
        this.executionDurationTimer =
                Timer.builder("agent_execution_duration")
                        .description("Agent 执行时长")
                        .publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
                        .minimumExpectedValue(Duration.ofMillis(10))
                        .maximumExpectedValue(Duration.ofMinutes(10))
                        .register(meterRegistry);

        // 初始化工具调用计数器
        this.toolCallsTotal =
                Counter.builder("agent_tool_calls_total")
                        .description("工具调用总数")
                        .tag("status", "total")
                        .register(meterRegistry);

        this.toolCallsSuccess =
                Counter.builder("agent_tool_calls_total")
                        .description("工具调用成功数")
                        .tag("status", "success")
                        .register(meterRegistry);

        this.toolCallsFailure =
                Counter.builder("agent_tool_calls_total")
                        .description("工具调用失败数")
                        .tag("status", "failure")
                        .register(meterRegistry);

        // 初始化工具执行时长 Timer
        this.toolExecutionTimer =
                Timer.builder("agent_tool_execution_duration")
                        .description("工具执行时长")
                        .publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
                        .minimumExpectedValue(Duration.ofMillis(1))
                        .maximumExpectedValue(Duration.ofSeconds(30))
                        .register(meterRegistry);

        // 初始化路由指标
        this.routingTotal =
                Counter.builder("agent_routing_total")
                        .description("路由决策总数")
                        .register(meterRegistry);

        this.routingLowConfidence =
                Counter.builder("agent_routing_low_confidence")
                        .description("低置信度路由次数")
                        .register(meterRegistry);

        this.routingConfirmed =
                Counter.builder("agent_routing_confirmation")
                        .description("路由确认总数")
                        .tag("result", "confirmed")
                        .register(meterRegistry);

        this.routingRejected =
                Counter.builder("agent_routing_confirmation")
                        .description("路由拒绝总数")
                        .tag("result", "rejected")
                        .register(meterRegistry);

        // 初始化委托指标
        this.delegationsTotal =
                Counter.builder("agent_delegations_total")
                        .description("委托调用总数")
                        .tag("status", "total")
                        .register(meterRegistry);

        this.delegationsSuccess =
                Counter.builder("agent_delegations_total")
                        .description("委托成功数")
                        .tag("status", "success")
                        .register(meterRegistry);

        this.delegationsFailure =
                Counter.builder("agent_delegations_total")
                        .description("委托失败数")
                        .tag("status", "failure")
                        .register(meterRegistry);

        // 初始化限流指标
        this.rateLimitHitsTotal =
                Counter.builder("agent_rate_limit_hits_total")
                        .description("限流触发总数")
                        .register(meterRegistry);

        // 初始化步骤指标
        this.stepsTotal =
                Counter.builder("agent_steps_total")
                        .description("Agent 执行步骤总数")
                        .register(meterRegistry);

        log.info("[AgentMetricsService] 初始化完成，Prometheus 指标已注册");
    }

    // ==================== 执行相关方法 ====================

    /**
     * 记录执行开始
     *
     * @param agentName Agent 名称
     */
    public void executionStarted(String agentName) {
        activeExecutions.incrementAndGet();
        executionsTotal.increment();
        getOrCreateAgentActiveCount(agentName).incrementAndGet();
        log.debug("[AgentMetrics] 执行开始: agent={}, active={}", agentName, activeExecutions.get());
    }

    /**
     * 记录执行成功
     *
     * @param agentName Agent 名称
     * @param durationMs 执行时长（毫秒）
     * @param stepCount 步骤数
     */
    public void executionSuccess(String agentName, long durationMs, int stepCount) {
        activeExecutions.decrementAndGet();
        getOrCreateAgentActiveCount(agentName).decrementAndGet();

        executionsSuccess.increment();
        getOrCreateAgentSuccessCounter(agentName).increment();

        // 记录执行时长
        executionDurationTimer.record(Duration.ofMillis(durationMs));
        getOrCreateAgentDurationTimer(agentName).record(Duration.ofMillis(durationMs));

        // 记录步骤数
        stepsTotal.increment(stepCount);
        getOrCreateAgentStepCounter(agentName).increment(stepCount);

        log.debug(
                "[AgentMetrics] 执行成功: agent={}, duration={}ms, steps={}",
                agentName,
                durationMs,
                stepCount);
    }

    /**
     * 记录执行失败
     *
     * @param agentName Agent 名称
     * @param durationMs 执行时长（毫秒）
     * @param errorType 错误类型
     */
    public void executionFailure(String agentName, long durationMs, String errorType) {
        activeExecutions.decrementAndGet();
        getOrCreateAgentActiveCount(agentName).decrementAndGet();

        executionsFailure.increment();
        getOrCreateAgentFailureCounter(agentName).increment();

        // 记录失败执行时长
        if (durationMs > 0) {
            executionDurationTimer.record(Duration.ofMillis(durationMs));
        }

        // 记录错误类型
        meterRegistry
                .counter("agent_errors_total", "agent", agentName, "type", errorType)
                .increment();

        log.debug("[AgentMetrics] 执行失败: agent={}, error={}", agentName, errorType);
    }

    /**
     * 记录执行取消
     *
     * @param agentName Agent 名称
     */
    public void executionCancelled(String agentName) {
        activeExecutions.decrementAndGet();
        getOrCreateAgentActiveCount(agentName).decrementAndGet();

        executionsCancelled.increment();
        log.debug("[AgentMetrics] 执行取消: agent={}", agentName);
    }

    // ==================== 工具调用相关方法 ====================

    /**
     * 记录工具调用
     *
     * @param toolName 工具名称
     * @param success 是否成功
     * @param durationMs 执行时长（毫秒）
     */
    public void recordToolCall(String toolName, boolean success, long durationMs) {
        toolCallsTotal.increment();

        if (success) {
            toolCallsSuccess.increment();
        } else {
            toolCallsFailure.increment();
        }

        // 按 Tool 细分计数
        String status = success ? "success" : "failure";
        getOrCreateToolCallCounter(toolName, status).increment();

        // 记录执行时长
        toolExecutionTimer.record(Duration.ofMillis(durationMs));

        log.debug(
                "[AgentMetrics] 工具调用: tool={}, success={}, duration={}ms",
                toolName,
                success,
                durationMs);
    }

    // ==================== 路由相关方法 ====================

    /**
     * 记录路由决策
     *
     * @param targetAgent 目标 Agent
     * @param confidence 置信度（0-1）
     */
    public void recordRoutingDecision(String targetAgent, double confidence) {
        routingTotal.increment();

        // 记录目标 Agent 选择
        meterRegistry.counter("agent_routing_targets", "agent", targetAgent).increment();

        // 记录置信度分布
        String confidenceBucket = getConfidenceBucket(confidence);
        meterRegistry.counter("agent_routing_confidence", "bucket", confidenceBucket).increment();

        // 低置信度路由
        if (confidence < 0.7) {
            routingLowConfidence.increment();
        }

        log.debug("[AgentMetrics] 路由决策: target={}, confidence={}", targetAgent, confidence);
    }

    /**
     * 记录路由确认
     *
     * @param approved 是否批准
     */
    public void recordRoutingConfirmation(boolean approved) {
        if (approved) {
            routingConfirmed.increment();
        } else {
            routingRejected.increment();
        }
        log.debug("[AgentMetrics] 路由确认: approved={}", approved);
    }

    // ==================== 委托相关方法 ====================

    /**
     * 记录委托调用
     *
     * @param sourceAgent 源 Agent
     * @param targetAgent 目标 Agent
     * @param success 是否成功
     * @param depth 委托深度
     */
    public void recordDelegation(
            String sourceAgent, String targetAgent, boolean success, int depth) {
        delegationsTotal.increment();

        if (success) {
            delegationsSuccess.increment();
        } else {
            delegationsFailure.increment();
        }

        // 记录委托深度
        getOrCreateDelegationDepthCounter(depth).increment();

        // 记录委托关系
        meterRegistry
                .counter("agent_delegation_pairs", "source", sourceAgent, "target", targetAgent)
                .increment();

        log.debug(
                "[AgentMetrics] 委托调用: {} -> {}, success={}, depth={}",
                sourceAgent,
                targetAgent,
                success,
                depth);
    }

    // ==================== 限流相关方法 ====================

    /**
     * 记录限流触发
     *
     * @param limitType 限流类型
     */
    public void recordRateLimitHit(String limitType) {
        rateLimitHitsTotal.increment();
        getOrCreateRateLimitCounter(limitType).increment();
        log.debug("[AgentMetrics] 限流触发: type={}", limitType);
    }

    // ==================== 辅助方法 ====================

    private Counter getOrCreateAgentSuccessCounter(String agentName) {
        return agentSuccessCounters.computeIfAbsent(
                agentName,
                agent ->
                        Counter.builder("agent_executions_by_agent")
                                .description("按 Agent 细分的执行成功数")
                                .tag("agent", agent)
                                .tag("status", "success")
                                .register(meterRegistry));
    }

    private Counter getOrCreateAgentFailureCounter(String agentName) {
        return agentFailureCounters.computeIfAbsent(
                agentName,
                agent ->
                        Counter.builder("agent_executions_by_agent")
                                .description("按 Agent 细分的执行失败数")
                                .tag("agent", agent)
                                .tag("status", "failure")
                                .register(meterRegistry));
    }

    private Timer getOrCreateAgentDurationTimer(String agentName) {
        return agentDurationTimers.computeIfAbsent(
                agentName,
                agent ->
                        Timer.builder("agent_execution_duration_by_agent")
                                .description("按 Agent 细分的执行时长")
                                .tag("agent", agent)
                                .publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
                                .minimumExpectedValue(Duration.ofMillis(10))
                                .maximumExpectedValue(Duration.ofMinutes(10))
                                .register(meterRegistry));
    }

    private AtomicLong getOrCreateAgentActiveCount(String agentName) {
        return agentActiveCounts.computeIfAbsent(
                agentName,
                k -> {
                    AtomicLong count = new AtomicLong(0);
                    Gauge.builder("agent_active_executions_by_agent", count, AtomicLong::get)
                            .description("按 Agent 细分的活跃执行数")
                            .tag("agent", agentName)
                            .register(meterRegistry);
                    return count;
                });
    }

    private Counter getOrCreateToolCallCounter(String toolName, String status) {
        String key = toolName + "_" + status;
        return toolCallCounters.computeIfAbsent(
                key,
                k ->
                        Counter.builder("agent_tool_calls_by_tool")
                                .description("按工具细分的调用次数")
                                .tag("tool", toolName)
                                .tag("status", status)
                                .register(meterRegistry));
    }

    private Counter getOrCreateDelegationDepthCounter(int depth) {
        return delegationDepthCounters.computeIfAbsent(
                depth,
                d ->
                        Counter.builder("agent_delegation_depth")
                                .description("委托深度分布")
                                .tag("depth", String.valueOf(d))
                                .register(meterRegistry));
    }

    private Counter getOrCreateRateLimitCounter(String limitType) {
        return rateLimitCounters.computeIfAbsent(
                limitType,
                type ->
                        Counter.builder("agent_rate_limit_hits_by_type")
                                .description("按类型细分的限流次数")
                                .tag("type", type)
                                .register(meterRegistry));
    }

    private Counter getOrCreateAgentStepCounter(String agentName) {
        return agentStepCounters.computeIfAbsent(
                agentName,
                agent ->
                        Counter.builder("agent_steps_by_agent")
                                .description("按 Agent 细分的步骤数")
                                .tag("agent", agent)
                                .register(meterRegistry));
    }

    private String getConfidenceBucket(double confidence) {
        if (confidence >= 0.9) return "0.9-1.0";
        if (confidence >= 0.8) return "0.8-0.9";
        if (confidence >= 0.7) return "0.7-0.8";
        if (confidence >= 0.5) return "0.5-0.7";
        return "0.0-0.5";
    }

    // ==================== 获取当前值 ====================

    /** 获取当前活跃执行数 */
    public long getActiveExecutions() {
        return activeExecutions.get();
    }
}
