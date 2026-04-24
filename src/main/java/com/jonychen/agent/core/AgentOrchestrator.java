package com.jonychen.agent.core;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.jonychen.agent.security.AgentInputValidator;
import com.jonychen.agent.security.AgentPermissionService;
import com.jonychen.agent.security.AgentRole;

import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

/**
 * Agent 编排器
 *
 * <p>核心职责：
 *
 * <ul>
 *   <li>路由用户请求到合适的 Agent
 *   <li>管理执行生命周期（创建上下文、超时、取消）
 *   <li>心跳保活（每 30 秒发送 Heartbeat 事件）
 *   <li>全局并发限流（上限 20 个并发执行）
 *   <li>确认操作管理
 * </ul>
 *
 * @author jonychen
 */
@Service
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

    /** 最大并发执行数 */
    private static final int MAX_CONCURRENT_EXECUTIONS = 20;

    /** 心跳间隔（秒） */
    private static final int HEARTBEAT_INTERVAL_SECONDS = 30;

    /** 活跃执行记录超时时间（分钟） */
    private static final int EXECUTION_TIMEOUT_MINUTES = 10;

    private final AgentRegistry agentRegistry;
    private final AgentPermissionService permissionService;
    private final AgentInputValidator inputValidator;

    /** 当前并发执行数 */
    private final AtomicInteger activeCount = new AtomicInteger(0);

    /** 活跃执行记录: traceId -> ActiveExecution */
    private final ConcurrentHashMap<String, ActiveExecution> activeExecutions =
            new ConcurrentHashMap<>();

    /** 心跳调度器 */
    private final ScheduledExecutorService heartbeatScheduler =
            Executors.newSingleThreadScheduledExecutor(
                    r -> {
                        Thread t = new Thread(r, "agent-heartbeat");
                        t.setDaemon(true);
                        return t;
                    });

    /** 清理调度器 */
    private final ScheduledExecutorService cleanupScheduler =
            Executors.newSingleThreadScheduledExecutor(
                    r -> {
                        Thread t = new Thread(r, "agent-cleanup");
                        t.setDaemon(true);
                        return t;
                    });

    public AgentOrchestrator(
            AgentRegistry agentRegistry,
            AgentPermissionService permissionService,
            AgentInputValidator inputValidator) {
        this.agentRegistry = agentRegistry;
        this.permissionService = permissionService;
        this.inputValidator = inputValidator;

        // 启动清理任务：每 60 秒移除超时的执行记录
        cleanupScheduler.scheduleAtFixedRate(
                this::cleanupStaleExecutions, 60, 60, TimeUnit.SECONDS);
    }

    /**
     * 执行 Agent 请求（流式）
     *
     * <p>流程：
     *
     * <ol>
     *   <li>输入校验
     *   <li>并发限流检查
     *   <li>路由到合适的 Agent
     *   <li>权限校验
     *   <li>创建执行上下文
     *   <li>执行并附加心跳
     * </ol>
     *
     * @param request Agent 请求
     * @return 事件流
     */
    public Flux<AgentEvent> executeStream(AgentRequest request) {
        String traceId = UUID.randomUUID().toString();
        log.info(
                "[AgentOrchestrator] 收到请求: traceId={}, userId={}, input={}",
                traceId,
                request.userId(),
                truncate(request.userInput(), 100));

        // 输入校验
        try {
            inputValidator.validate(request.userInput());
        } catch (IllegalArgumentException e) {
            log.warn("[AgentOrchestrator] 输入校验失败: {}", e.getMessage());
            return Flux.just(
                    AgentEvent.error(traceId, 0, "INVALID_INPUT", e.getMessage(), null, true));
        }

        // 并发限流
        if (activeCount.get() >= MAX_CONCURRENT_EXECUTIONS) {
            log.warn("[AgentOrchestrator] 并发执行数已达上限: {}", MAX_CONCURRENT_EXECUTIONS);
            return Flux.just(
                    AgentEvent.error(
                            traceId, 0, "CONCURRENCY_LIMIT", "当前执行请求过多，请稍后重试", null, true));
        }

        // 路由到合适的 Agent
        Agent selectedAgent = routeAgent(request);
        if (selectedAgent == null) {
            return Flux.just(
                    AgentEvent.error(traceId, 0, "NO_AGENT", "没有可用的 Agent 处理此请求", null, true));
        }

        log.info(
                "[AgentOrchestrator] 路由到 Agent: {} (置信度: {})",
                selectedAgent.getMetadata().name(),
                selectedAgent.canHandle(request));

        // 权限校验（P0: 使用默认 ADMIN 角色）
        AgentRole role = AgentRole.ADMIN;
        if (!permissionService.canExecuteAgent(role, selectedAgent.getMetadata())) {
            return Flux.just(
                    AgentEvent.error(
                            traceId,
                            0,
                            "PERMISSION_DENIED",
                            "权限不足，无法执行 " + selectedAgent.getMetadata().displayName(),
                            null,
                            false));
        }

        // 创建执行上下文
        AgentContext context =
                AgentContext.of(
                        request.sessionId(),
                        request.userId(),
                        selectedAgent.getMetadata().agentType(),
                        request.options());

        // 注册活跃执行
        ActiveExecution execution =
                new ActiveExecution(traceId, request.userId(), selectedAgent.getMetadata().name());
        activeExecutions.put(traceId, execution);
        activeCount.incrementAndGet();

        // 执行并附加心跳
        return Flux.create(
                (FluxSink<AgentEvent> sink) -> {
                    // 启动心跳
                    var heartbeatFuture =
                            heartbeatScheduler.scheduleAtFixedRate(
                                    () -> {
                                        if (!sink.isCancelled()) {
                                            try {
                                                int seqNum =
                                                        context.getSequenceCounter()
                                                                .getAndIncrement();
                                                sink.next(AgentEvent.heartbeat(traceId, seqNum));
                                            } catch (Exception e) {
                                                // sink 可能已关闭
                                            }
                                        }
                                    },
                                    HEARTBEAT_INTERVAL_SECONDS,
                                    HEARTBEAT_INTERVAL_SECONDS,
                                    TimeUnit.SECONDS);

                    // 订阅 Agent 事件流
                    selectedAgent
                            .executeStream(request, context)
                            .doOnComplete(
                                    () -> {
                                        heartbeatFuture.cancel(false);
                                        activeExecutions.remove(traceId);
                                        activeCount.decrementAndGet();
                                        log.info("[AgentOrchestrator] 执行完成: traceId={}", traceId);
                                    })
                            .doOnError(
                                    e -> {
                                        heartbeatFuture.cancel(false);
                                        activeExecutions.remove(traceId);
                                        activeCount.decrementAndGet();
                                        log.error(
                                                "[AgentOrchestrator] 执行失败: traceId={}, error={}",
                                                traceId,
                                                e.getMessage());
                                    })
                            .doOnCancel(
                                    () -> {
                                        heartbeatFuture.cancel(false);
                                        activeExecutions.remove(traceId);
                                        activeCount.decrementAndGet();
                                    })
                            .subscribe(sink::next, sink::error, sink::complete);
                });
    }

    /**
     * 路由到合适的 Agent
     *
     * <p>P0 阶段使用关键词匹配的 canHandle 置信度分数进行路由。 后续可替换为 RouterAgent（LLM 智能路由）。
     */
    private Agent routeAgent(AgentRequest request) {
        Agent bestAgent = null;
        double bestScore = 0.0;

        for (Agent agent :
                agentRegistry.getAllMetadata().stream()
                        .map(meta -> agentRegistry.getAgent(meta.name()).orElse(null))
                        .filter(a -> a != null)
                        .toList()) {
            double score = agent.canHandle(request);
            if (score > bestScore) {
                bestScore = score;
                bestAgent = agent;
            }
        }

        return bestAgent;
    }

    /**
     * 确认操作
     *
     * @param traceId 追踪 ID
     * @param confirmationId 确认 ID
     * @param approved 是否批准
     * @param operatorId 操作者 ID
     * @return 是否成功
     */
    public boolean confirmOperation(
            String traceId, String confirmationId, boolean approved, String operatorId) {
        ActiveExecution execution = activeExecutions.get(traceId);
        if (execution == null) {
            log.warn("[AgentOrchestrator] 确认操作失败: 未找到执行记录 traceId={}", traceId);
            return false;
        }

        // 校验操作者必须是执行发起者
        if (!execution.userId.equals(operatorId)) {
            log.warn(
                    "[AgentOrchestrator] 确认操作失败: 操作者不是执行发起者 traceId={}, operator={}, initiator={}",
                    traceId,
                    operatorId,
                    execution.userId);
            return false;
        }

        log.info(
                "[AgentOrchestrator] 确认操作: traceId={}, confirmationId={}, approved={}",
                traceId,
                confirmationId,
                approved);

        execution.context.resolveConfirmation(confirmationId, approved);
        return true;
    }

    /**
     * 取消执行
     *
     * @param traceId 追踪 ID
     * @return 是否成功
     */
    public boolean cancelExecution(String traceId) {
        ActiveExecution execution = activeExecutions.get(traceId);
        if (execution == null) {
            return false;
        }

        execution.context.cancel();
        log.info("[AgentOrchestrator] 取消执行: traceId={}", traceId);
        return true;
    }

    /**
     * 获取可用 Agent 列表
     *
     * @return Agent 元信息列表
     */
    public java.util.List<AgentMetadata> getAvailableAgents() {
        return agentRegistry.getAllMetadata();
    }

    /** 清理超时的执行记录 */
    private void cleanupStaleExecutions() {
        long now = System.currentTimeMillis();
        long timeoutMs = Duration.ofMinutes(EXECUTION_TIMEOUT_MINUTES).toMillis();

        activeExecutions
                .entrySet()
                .removeIf(
                        entry -> {
                            if (now - entry.getValue().startTime > timeoutMs) {
                                log.warn("[AgentOrchestrator] 清理超时执行: traceId={}", entry.getKey());
                                entry.getValue().context.cancel();
                                activeCount.decrementAndGet();
                                return true;
                            }
                            return false;
                        });
    }

    @PreDestroy
    public void shutdown() {
        log.info("[AgentOrchestrator] 关闭编排器...");
        heartbeatScheduler.shutdown();
        cleanupScheduler.shutdown();
        try {
            heartbeatScheduler.awaitTermination(5, TimeUnit.SECONDS);
            cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return null;
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    /** 活跃执行记录 */
    private static class ActiveExecution {
        final String traceId;
        final String userId;
        final String agentName;
        final long startTime;
        final AgentContext context;

        ActiveExecution(String traceId, String userId, String agentName) {
            this.traceId = traceId;
            this.userId = userId;
            this.agentName = agentName;
            this.startTime = System.currentTimeMillis();
            this.context = null; // 会在 executeStream 中设置
        }
    }
}
