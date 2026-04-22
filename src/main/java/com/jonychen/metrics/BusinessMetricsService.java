package com.jonychen.metrics;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

/**
 * 业务指标监控服务
 *
 * <p>提供以下监控能力： 1. Token 用量实时监控（按模型、按用户） 2. 会话活跃度监控（在线用户、消息吞吐量） 3. 自定义业务指标（对话创建、对话长度）
 *
 * @author jonychen
 */
@Slf4j
@Service
public class BusinessMetricsService {

    private final MeterRegistry meterRegistry;

    // ==================== Token 指标 ====================

    /** Token 计数器（按模型分组） */
    private final Counter promptTokensCounter;

    private final Counter completionTokensCounter;
    private final Counter totalTokensCounter;

    /** Token 计数器（按模型细分） */
    private final Map<String, Counter> modelPromptTokensCounters = new ConcurrentHashMap<>();

    private final Map<String, Counter> modelCompletionTokensCounters = new ConcurrentHashMap<>();

    /** Token 速率计 */
    private final Counter tokenRateCounter;

    // ==================== 会话指标 ====================

    /** 活跃会话数 */
    private final AtomicLong activeSessions = new AtomicLong(0);

    private final AtomicLong activeUsers = new AtomicLong(0);

    /** 会话 Gauge */
    private final Gauge activeSessionsGauge;

    private final Gauge activeUsersGauge;

    /** 消息吞吐量 */
    private final Counter messagesSentCounter;

    private final Counter messagesReceivedCounter;

    // ==================== 对话指标 ====================

    /** 对话创建计数 */
    private final Counter conversationsCreatedCounter;

    /** 对话完成计数 */
    private final Counter conversationsCompletedCounter;

    /** 对话长度分布 */
    private final DistributionSummary conversationLengthSummary;

    /** 对话持续时间 */
    private final Timer conversationDurationTimer;

    // ==================== 流式响应指标 ====================

    /** 流式响应计数 */
    private final Counter streamingRequestsCounter;

    private final Counter streamingCompletedCounter;
    private final Counter streamingErrorsCounter;

    /** 流式响应延迟 */
    private final Timer streamingLatencyTimer;

    // ==================== 成本指标 ====================

    /** 累计成本（美元） */
    private final AtomicLong totalCostCents = new AtomicLong(0);

    private final Gauge totalCostGauge;

    /** 今日成本 */
    private final AtomicLong todayCostCents = new AtomicLong(0);

    private final Gauge todayCostGauge;

    // ==================== 用户行为指标 ====================

    /** 用户请求频率 */
    private final Map<String, Counter> userRequestCounters = new ConcurrentHashMap<>();

    /** 用户 Token 消耗 */
    private final Map<String, Counter> userTokensCounters = new ConcurrentHashMap<>();

    public BusinessMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // 初始化 Token 计数器
        this.promptTokensCounter =
                Counter.builder("langchain4j_tokens_total")
                        .description("Token 总使用量")
                        .tag("type", "prompt")
                        .register(meterRegistry);

        this.completionTokensCounter =
                Counter.builder("langchain4j_tokens_total")
                        .description("Token 总使用量")
                        .tag("type", "completion")
                        .register(meterRegistry);

        this.totalTokensCounter =
                Counter.builder("langchain4j_tokens_total")
                        .description("Token 总使用量")
                        .tag("type", "total")
                        .register(meterRegistry);

        this.tokenRateCounter =
                Counter.builder("langchain4j_token_rate")
                        .description("Token 处理速率")
                        .baseUnit("tokens")
                        .register(meterRegistry);

        // 初始化会话 Gauge
        this.activeSessionsGauge =
                Gauge.builder("langchain4j_active_sessions", activeSessions, AtomicLong::get)
                        .description("当前活跃会话数")
                        .register(meterRegistry);

        this.activeUsersGauge =
                Gauge.builder("langchain4j_active_users", activeUsers, AtomicLong::get)
                        .description("当前活跃用户数")
                        .register(meterRegistry);

        // 初始化消息计数器
        this.messagesSentCounter =
                Counter.builder("langchain4j_messages_total")
                        .description("消息总数")
                        .tag("direction", "sent")
                        .register(meterRegistry);

        this.messagesReceivedCounter =
                Counter.builder("langchain4j_messages_total")
                        .description("消息总数")
                        .tag("direction", "received")
                        .register(meterRegistry);

        // 初始化对话指标
        this.conversationsCreatedCounter =
                Counter.builder("langchain4j_conversations_total")
                        .description("对话创建总数")
                        .tag("status", "created")
                        .register(meterRegistry);

        this.conversationsCompletedCounter =
                Counter.builder("langchain4j_conversations_total")
                        .description("对话完成总数")
                        .tag("status", "completed")
                        .register(meterRegistry);

        this.conversationLengthSummary =
                DistributionSummary.builder("langchain4j_conversation_length")
                        .description("对话长度分布（消息数）")
                        .baseUnit("messages")
                        .minimumExpectedValue(1.0)
                        .maximumExpectedValue(1000.0)
                        .publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
                        .register(meterRegistry);

        this.conversationDurationTimer =
                Timer.builder("langchain4j_conversation_duration")
                        .description("对话持续时间")
                        .publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
                        .minimumExpectedValue(Duration.ofSeconds(1))
                        .maximumExpectedValue(Duration.ofHours(1))
                        .register(meterRegistry);

        // 初始化流式响应指标
        this.streamingRequestsCounter =
                Counter.builder("langchain4j_streaming_requests_total")
                        .description("流式请求总数")
                        .tag("status", "requested")
                        .register(meterRegistry);

        this.streamingCompletedCounter =
                Counter.builder("langchain4j_streaming_requests_total")
                        .description("流式请求总数")
                        .tag("status", "completed")
                        .register(meterRegistry);

        this.streamingErrorsCounter =
                Counter.builder("langchain4j_streaming_requests_total")
                        .description("流式请求总数")
                        .tag("status", "error")
                        .register(meterRegistry);

        this.streamingLatencyTimer =
                Timer.builder("langchain4j_streaming_latency")
                        .description("流式响应首字延迟")
                        .publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
                        .minimumExpectedValue(Duration.ofMillis(10))
                        .maximumExpectedValue(Duration.ofSeconds(30))
                        .register(meterRegistry);

        // 初始化成本指标（使用 Gauge 包装）
        this.totalCostGauge =
                Gauge.builder("langchain4j_cost_total", totalCostCents, AtomicLong::get)
                        .description("累计成本（美分）")
                        .baseUnit("cents")
                        .register(meterRegistry);

        this.todayCostGauge =
                Gauge.builder("langchain4j_cost_today", todayCostCents, AtomicLong::get)
                        .description("今日成本（美分）")
                        .baseUnit("cents")
                        .register(meterRegistry);

        log.info("业务指标服务初始化完成");
    }

    // ==================== Token 相关方法 ====================

    /**
     * 记录 Token 使用
     *
     * @param modelName 模型名称
     * @param promptTokens 输入 Token 数
     * @param completionTokens 输出 Token 数
     */
    public void recordTokenUsage(String modelName, int promptTokens, int completionTokens) {
        int totalTokens = promptTokens + completionTokens;

        // 全局计数
        promptTokensCounter.increment(promptTokens);
        completionTokensCounter.increment(completionTokens);
        totalTokensCounter.increment(totalTokens);
        tokenRateCounter.increment(totalTokens);

        // 按模型计数
        getModelPromptCounter(modelName).increment(promptTokens);
        getModelCompletionCounter(modelName).increment(completionTokens);

        log.trace(
                "Token 使用记录: model={}, prompt={}, completion={}, total={}",
                modelName,
                promptTokens,
                completionTokens,
                totalTokens);
    }

    /** 记录用户 Token 消耗 */
    public void recordUserTokenUsage(String userId, int tokens) {
        getUserTokensCounter(userId).increment(tokens);
    }

    private Counter getModelPromptCounter(String modelName) {
        return modelPromptTokensCounters.computeIfAbsent(
                modelName,
                model ->
                        Counter.builder("langchain4j_model_tokens")
                                .description("模型 Token 使用量")
                                .tag("model", model)
                                .tag("type", "prompt")
                                .register(meterRegistry));
    }

    private Counter getModelCompletionCounter(String modelName) {
        return modelCompletionTokensCounters.computeIfAbsent(
                modelName,
                model ->
                        Counter.builder("langchain4j_model_tokens")
                                .description("模型 Token 使用量")
                                .tag("model", model)
                                .tag("type", "completion")
                                .register(meterRegistry));
    }

    private Counter getUserTokensCounter(String userId) {
        return userTokensCounters.computeIfAbsent(
                userId,
                user ->
                        Counter.builder("langchain4j_user_tokens")
                                .description("用户 Token 消耗")
                                .tag("user_id", user)
                                .register(meterRegistry));
    }

    // ==================== 会话相关方法 ====================

    /** 会话开始 */
    public void sessionStarted(String userId) {
        activeSessions.incrementAndGet();
        activeUsers.incrementAndGet();
        getUserRequestCounter(userId).increment();
        log.trace("会话开始: userId={}, activeSessions={}", userId, activeSessions.get());
    }

    /** 会话结束 */
    public void sessionEnded(String userId) {
        activeSessions.decrementAndGet();
        log.trace("会话结束: userId={}, activeSessions={}", userId, activeSessions.get());
    }

    /** 用户离线 */
    public void userOffline(String userId) {
        activeUsers.decrementAndGet();
        log.trace("用户离线: userId={}, activeUsers={}", userId, activeUsers.get());
    }

    private Counter getUserRequestCounter(String userId) {
        return userRequestCounters.computeIfAbsent(
                userId,
                user ->
                        Counter.builder("langchain4j_user_requests")
                                .description("用户请求次数")
                                .tag("user_id", user)
                                .register(meterRegistry));
    }

    // ==================== 消息相关方法 ====================

    /** 记录用户消息 */
    public void recordUserMessage(String userId) {
        messagesSentCounter.increment();
        getUserRequestCounter(userId).increment();
    }

    /** 记录 AI 响应 */
    public void recordAiResponse(String modelName) {
        messagesReceivedCounter.increment();
        meterRegistry.counter("langchain4j_model_messages", "model", modelName).increment();
    }

    // ==================== 对话相关方法 ====================

    /** 对话创建 */
    public void conversationCreated() {
        conversationsCreatedCounter.increment();
    }

    /**
     * 对话完成
     *
     * @param messageCount 消息数量
     * @param durationSeconds 持续时间（秒）
     */
    public void conversationCompleted(int messageCount, long durationSeconds) {
        conversationsCompletedCounter.increment();
        conversationLengthSummary.record(messageCount);
        conversationDurationTimer.record(Duration.ofSeconds(durationSeconds));
    }

    // ==================== 流式响应相关方法 ====================

    /** 流式请求开始 */
    public void streamingRequestStarted(String modelName) {
        streamingRequestsCounter.increment();
        meterRegistry.counter("langchain4j_streaming_by_model", "model", modelName).increment();
    }

    /** 流式请求完成 */
    public void streamingRequestCompleted() {
        streamingCompletedCounter.increment();
    }

    /** 流式请求错误 */
    public void streamingRequestError(String errorType) {
        streamingErrorsCounter.increment();
        meterRegistry.counter("langchain4j_streaming_errors", "error_type", errorType).increment();
    }

    /**
     * 记录流式首字延迟
     *
     * @param latencyMs 延迟毫秒数
     */
    public void recordStreamingLatency(long latencyMs) {
        streamingLatencyTimer.record(Duration.ofMillis(latencyMs));
    }

    // ==================== 成本相关方法 ====================

    /**
     * 记录成本
     *
     * @param costUsd 成本（美元）
     */
    public void recordCost(double costUsd) {
        long costCents = (long) (costUsd * 100);
        totalCostCents.addAndGet(costCents);
        todayCostCents.addAndGet(costCents);
    }

    /** 重置今日成本（每日定时任务调用） */
    public void resetTodayCost() {
        todayCostCents.set(0);
        log.info("今日成本已重置");
    }

    // ==================== 获取当前值 ====================

    /** 获取当前活跃会话数 */
    public long getActiveSessions() {
        return activeSessions.get();
    }

    /** 获取当前活跃用户数 */
    public long getActiveUsers() {
        return activeUsers.get();
    }

    /** 获取累计成本（美元） */
    public double getTotalCostUsd() {
        return totalCostCents.get() / 100.0;
    }

    /** 获取今日成本（美元） */
    public double getTodayCostUsd() {
        return todayCostCents.get() / 100.0;
    }
}
