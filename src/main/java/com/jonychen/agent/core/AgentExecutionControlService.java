package com.jonychen.agent.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.jonychen.agent.quota.QuotaProvider;
import com.jonychen.agent.quota.QuotaProvider.QuotaCheckResult;

/**
 * Agent 执行控制服务
 *
 * <p>提供全局和 Agent 级别的并发限流、速率限制和配额管理。
 *
 * <h2>限流策略</h2>
 *
 * <ul>
 *   <li>全局并发限流：最大 20 个并发执行
 *   <li>Agent 级限流：每个 Agent 最大并发数可配置
 *   <li>用户级限流：可通过 QuotaProvider 实现分布式限流
 * </ul>
 *
 * <h2>配额管理</h2>
 *
 * <ul>
 *   <li>每日执行配额：每个用户每日最大执行次数
 *   <li>每分钟请求配额：每个用户每分钟最大请求数
 * </ul>
 *
 * @author jonychen
 */
@Service
public class AgentExecutionControlService {

    private static final Logger log = LoggerFactory.getLogger(AgentExecutionControlService.class);

    /** 默认全局最大并发数 */
    private static final int DEFAULT_GLOBAL_MAX_CONCURRENT = 20;

    /** 默认 Agent 最大并发数 */
    private static final int DEFAULT_AGENT_MAX_CONCURRENT = 10;

    /** 全局当前并发数 */
    private final AtomicInteger globalConcurrentCount = new AtomicInteger(0);

    /** Agent 级当前并发数 */
    private final ConcurrentHashMap<String, AtomicInteger> agentConcurrentCounts =
            new ConcurrentHashMap<>();

    /** Agent 级最大并发数配置 */
    private final ConcurrentHashMap<String, Integer> agentMaxConcurrentConfig =
            new ConcurrentHashMap<>();

    /** 全局最大并发数 */
    private volatile int globalMaxConcurrent = DEFAULT_GLOBAL_MAX_CONCURRENT;

    /** 配额提供者（可选，用于分布式限流） */
    private QuotaProvider quotaProvider;

    public AgentExecutionControlService() {
        log.info("[AgentExecutionControl] 初始化完成, 全局并发上限={}", globalMaxConcurrent);
    }

    @Autowired(required = false)
    public void setQuotaProvider(QuotaProvider quotaProvider) {
        this.quotaProvider = quotaProvider;
        log.info(
                "[AgentExecutionControl] 已注入 QuotaProvider: {}",
                quotaProvider.getClass().getSimpleName());
    }

    /**
     * 尝试获取执行许可
     *
     * <p>检查全局并发、Agent 并发限制。 如果配置了 QuotaProvider，还会检查用户配额。
     *
     * @param agentName Agent 名称
     * @param userId 用户 ID
     * @return 许可结果
     */
    public ExecutionPermit tryAcquire(String agentName, String userId) {
        log.debug("[AgentExecutionControl] 尝试获取执行许可: agent={}, user={}", agentName, userId);

        // 1. 检查全局并发限制
        int globalCurrent = globalConcurrentCount.get();
        if (globalCurrent >= globalMaxConcurrent) {
            log.warn(
                    "[AgentExecutionControl] 全局并发超限: current={}, max={}",
                    globalCurrent,
                    globalMaxConcurrent);
            return ExecutionPermit.rejected(
                    "CONCURRENCY_LIMIT", "全局并发数已达上限 " + globalMaxConcurrent);
        }

        // 2. 检查 Agent 级并发限制
        int agentMax =
                agentMaxConcurrentConfig.getOrDefault(agentName, DEFAULT_AGENT_MAX_CONCURRENT);
        AtomicInteger agentCounter =
                agentConcurrentCounts.computeIfAbsent(agentName, k -> new AtomicInteger(0));
        int agentCurrent = agentCounter.get();
        if (agentCurrent >= agentMax) {
            log.warn(
                    "[AgentExecutionControl] Agent 并发超限: agent={}, current={}, max={}",
                    agentName,
                    agentCurrent,
                    agentMax);
            return ExecutionPermit.rejected(
                    "AGENT_CONCURRENCY_LIMIT", "Agent " + agentName + " 并发数已达上限 " + agentMax);
        }

        // 3. 检查用户配额（如果配置了 QuotaProvider）
        if (quotaProvider != null) {
            QuotaCheckResult quotaResult =
                    quotaProvider.checkAndConsume(userId, "DAILY_EXECUTION", 1).block();
            if (quotaResult != null && !quotaResult.allowed()) {
                log.warn(
                        "[AgentExecutionControl] 用户配额耗尽: user={}, remaining={}",
                        userId,
                        quotaResult.remaining());
                return ExecutionPermit.rejected(
                        "USER_DAILY_QUOTA",
                        "今日执行次数已达上限，请等待 " + quotaResult.resetInSeconds() + " 秒后重试");
            }
        }

        // 获取许可：增加计数
        globalConcurrentCount.incrementAndGet();
        agentCounter.incrementAndGet();

        log.info(
                "[AgentExecutionControl] 执行许可已获取: agent={}, user={}, global={}/{}, agent={}/{}",
                agentName,
                userId,
                globalConcurrentCount.get(),
                globalMaxConcurrent,
                agentCounter.get(),
                agentMax);

        return ExecutionPermit.granted(agentName, userId);
    }

    /**
     * 释放执行许可
     *
     * <p>执行完成后调用，减少全局和 Agent 级并发计数。
     *
     * @param permit 执行许可
     */
    public void release(ExecutionPermit permit) {
        if (permit == null || !permit.granted()) {
            return;
        }

        globalConcurrentCount.decrementAndGet();
        AtomicInteger agentCounter = agentConcurrentCounts.get(permit.agentName());
        if (agentCounter != null) {
            agentCounter.decrementAndGet();
        }

        log.info(
                "[AgentExecutionControl] 执行许可已释放: agent={}, user={}, global={}",
                permit.agentName(),
                permit.userId(),
                globalConcurrentCount.get());
    }

    /**
     * 获取当前并发状态
     *
     * @return 并发状态信息
     */
    public Map<String, Object> getConcurrencyStatus() {
        Map<String, Object> status = new java.util.LinkedHashMap<>();
        status.put("globalConcurrent", globalConcurrentCount.get());
        status.put("globalMaxConcurrent", globalMaxConcurrent);
        status.put("agentConcurrent", getAgentConcurrentMap());
        return status;
    }

    /**
     * 配置 Agent 最大并发数
     *
     * @param agentName Agent 名称
     * @param maxConcurrent 最大并发数
     */
    public void configureAgentMaxConcurrent(String agentName, int maxConcurrent) {
        agentMaxConcurrentConfig.put(agentName, maxConcurrent);
        log.info("[AgentExecutionControl] 配置 Agent 并发上限: {}={}", agentName, maxConcurrent);
    }

    /**
     * 更新全局最大并发数
     *
     * @param maxConcurrent 最大并发数
     */
    public void setGlobalMaxConcurrent(int maxConcurrent) {
        this.globalMaxConcurrent = maxConcurrent;
        log.info("[AgentExecutionControl] 更新全局并发上限: {}", maxConcurrent);
    }

    private Map<String, Integer> getAgentConcurrentMap() {
        Map<String, Integer> map = new java.util.LinkedHashMap<>();
        agentConcurrentCounts.forEach((name, counter) -> map.put(name, counter.get()));
        return map;
    }

    /**
     * 执行许可
     *
     * @param granted 是否获得许可
     * @param agentName Agent 名称
     * @param userId 用户 ID
     * @param rejectReason 拒绝原因
     * @param rejectCode 拒绝错误码
     */
    public record ExecutionPermit(
            boolean granted,
            String agentName,
            String userId,
            String rejectCode,
            String rejectReason) {

        public static ExecutionPermit granted(String agentName, String userId) {
            return new ExecutionPermit(true, agentName, userId, null, null);
        }

        public static ExecutionPermit rejected(String rejectCode, String rejectReason) {
            return new ExecutionPermit(false, null, null, rejectCode, rejectReason);
        }
    }
}
