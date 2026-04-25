package com.jonychen.agent.core;

import java.time.Duration;
import java.util.Map;
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

import com.jonychen.agent.impl.RouterAgent;
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
 *   <li>使用 RouterAgent（LLM 智能路由）路由用户请求到合适的 Agent
 *   <li>管理执行生命周期（创建上下文、超时、取消）
 *   <li>心跳保活（每 30 秒发送 Heartbeat 事件）
 *   <li>全局并发限流（上限 20 个并发执行）
 *   <li>确认操作管理（低置信度路由、敏感操作）
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
    private final RouterAgent routerAgent;
    private final AgentAuditService auditService;
    private final AgentMetricsService metricsService;

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
            AgentInputValidator inputValidator,
            RouterAgent routerAgent,
            AgentAuditService auditService,
            AgentMetricsService metricsService) {
        this.agentRegistry = agentRegistry;
        this.permissionService = permissionService;
        this.inputValidator = inputValidator;
        this.routerAgent = routerAgent;
        this.auditService = auditService;
        this.metricsService = metricsService;

        // 启动清理任务：每 60 秒移除超时的执行记录
        cleanupScheduler.scheduleAtFixedRate(
                this::cleanupStaleExecutions, 60, 60, TimeUnit.SECONDS);

        log.info("[AgentOrchestrator] 初始化完成，使用 RouterAgent 进行智能路由");
    }

    /**
     * 执行 Agent 请求（流式）
     *
     * <p>流程：
     *
     * <ol>
     *   <li>输入校验
     *   <li>并发限流检查
     *   <li>使用 RouterAgent 路由到合适的 Agent
     *   <li>如果低置信度，发送确认请求事件
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
            // 审计：速率限制触发
            auditService.recordRateLimitHit(
                    request.userId(), "GLOBAL_CONCURRENT", MAX_CONCURRENT_EXECUTIONS);
            // 指标：限流触发
            metricsService.recordRateLimitHit("GLOBAL_CONCURRENT");
            return Flux.just(
                    AgentEvent.error(
                            traceId, 0, "CONCURRENCY_LIMIT", "当前执行请求过多，请稍后重试", null, true));
        }

        // 使用 RouterAgent 进行智能路由
        RoutingDecision routingDecision;
        try {
            routingDecision = routerAgent.route(request);
            // 指标：路由决策
            metricsService.recordRoutingDecision(
                    routingDecision.targetAgent(), routingDecision.confidence());
        } catch (Exception e) {
            log.error("[AgentOrchestrator] 路由失败: {}", e.getMessage(), e);
            return Flux.just(
                    AgentEvent.error(
                            traceId, 0, "ROUTING_ERROR", "路由失败: " + e.getMessage(), null, true));
        }

        // 检查是否需要用户确认路由选择
        if (routingDecision.needsConfirmation()) {
            log.info(
                    "[AgentOrchestrator] 低置信度路由，需要用户确认: agent={}, confidence={}%",
                    routingDecision.targetAgent(), routingDecision.getConfidencePercent());

            // 创建上下文（用于确认流程）
            AgentContext context =
                    AgentContext.of(
                            request.sessionId(),
                            request.userId(),
                            AgentType.ROUTER,
                            request.options());

            // 注册活跃执行（等待确认）
            ActiveExecution execution =
                    new ActiveExecution(traceId, request.userId(), "router", routingDecision);
            activeExecutions.put(traceId, execution);
            activeCount.incrementAndGet();

            return Flux.create(
                    (FluxSink<AgentEvent> sink) -> {
                        int seqNum = context.getSequenceCounter().getAndIncrement();

                        // 发送路由思考事件
                        String thought =
                                String.format(
                                        "分析用户意图，建议路由到 %s（置信度: %d%%）\n理由: %s",
                                        routingDecision.targetAgent(),
                                        routingDecision.getConfidencePercent(),
                                        routingDecision.reason());
                        sink.next(AgentEvent.thought(traceId, seqNum, 0, thought));
                        seqNum = context.getSequenceCounter().getAndIncrement();

                        // 发送确认请求事件
                        String confirmationMessage =
                                String.format(
                                        "我判断您的问题适合由 %s 处理（置信度: %d%%）\n理由：%s\n是否确认使用该 Agent？",
                                        routingDecision.targetAgent(),
                                        routingDecision.getConfidencePercent(),
                                        routingDecision.reason());

                        sink.next(
                                AgentEvent.confirmationRequired(
                                        traceId,
                                        seqNum,
                                        0,
                                        "routing_confirm",
                                        "路由选择",
                                        confirmationMessage,
                                        com.jonychen.tool.RiskLevel.LOW,
                                        Map.of("targetAgent", routingDecision.targetAgent())));

                        // 审计：确认请求
                        auditService.recordConfirmationRequired(
                                traceId,
                                request.userId(),
                                "routing_confirm",
                                "路由选择: " + routingDecision.targetAgent(),
                                com.jonychen.tool.RiskLevel.LOW.name());

                        // 保存 context 到 execution
                        execution.setContext(context);

                        sink.complete();
                    });
        }

        // 获取选中的 Agent
        Agent selectedAgent = agentRegistry.getAgent(routingDecision.targetAgent()).orElse(null);
        if (selectedAgent == null) {
            log.warn(
                    "[AgentOrchestrator] 路由目标 Agent 不存在: {}，降级到 ChatAgent",
                    routingDecision.targetAgent());
            selectedAgent = agentRegistry.getAgent("chat").orElse(null);
        }

        if (selectedAgent == null) {
            return Flux.just(
                    AgentEvent.error(traceId, 0, "NO_AGENT", "没有可用的 Agent 处理此请求", null, true));
        }

        log.info(
                "[AgentOrchestrator] 路由到 Agent: {} (置信度: {}%)",
                selectedAgent.getMetadata().name(), routingDecision.getConfidencePercent());

        // 权限校验（P0: 使用默认 ADMIN 角色）
        AgentRole role = AgentRole.ADMIN;
        if (!permissionService.canExecuteAgent(role, selectedAgent.getMetadata())) {
            // 审计：权限拒绝
            auditService.recordPermissionDenied(
                    traceId, request.userId(), selectedAgent.getMetadata().name(), role.name());
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

        // 审计：执行开始
        auditService.recordExecutionStart(
                traceId,
                request.userId(),
                selectedAgent.getMetadata().name(),
                request.userInput(),
                null,
                null);

        // 指标：执行开始
        metricsService.executionStarted(selectedAgent.getMetadata().name());

        // 执行并附加心跳
        return executeWithAgent(request, context, selectedAgent, traceId);
    }

    /**
     * 使用指定 Agent 执行请求（用户确认路由后使用）
     *
     * @param traceId 追踪 ID
     * @param agentName Agent 名称
     * @param request Agent 请求
     * @return 事件流
     */
    public Flux<AgentEvent> executeWithAgent(
            String traceId, String agentName, AgentRequest request) {
        Agent selectedAgent = agentRegistry.getAgent(agentName).orElse(null);
        if (selectedAgent == null) {
            return Flux.just(
                    AgentEvent.error(
                            traceId, 0, "NO_AGENT", "Agent 不存在: " + agentName, null, true));
        }

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

        return executeWithAgent(request, context, selectedAgent, traceId);
    }

    /** 执行 Agent 并附加心跳 */
    private Flux<AgentEvent> executeWithAgent(
            AgentRequest request, AgentContext context, Agent selectedAgent, String traceId) {
        return Flux.create(
                (FluxSink<AgentEvent> sink) -> {
                    // 发送路由结果事件
                    int seqNum = context.getSequenceCounter().getAndIncrement();
                    sink.next(
                            AgentEvent.agentResult(
                                    traceId,
                                    seqNum,
                                    0,
                                    "router",
                                    "路由到 " + selectedAgent.getMetadata().name(),
                                    true));

                    // 启动心跳
                    var heartbeatFuture =
                            heartbeatScheduler.scheduleAtFixedRate(
                                    () -> {
                                        if (!sink.isCancelled()) {
                                            try {
                                                int heartSeqNum =
                                                        context.getSequenceCounter()
                                                                .getAndIncrement();
                                                sink.next(
                                                        AgentEvent.heartbeat(traceId, heartSeqNum));
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
                                        // 审计：执行结束（成功）
                                        auditService.recordExecutionEnd(
                                                traceId,
                                                context.getUserId(),
                                                selectedAgent.getMetadata().name(),
                                                true,
                                                0,
                                                0,
                                                0);
                                        // 指标：执行成功
                                        metricsService.executionSuccess(
                                                selectedAgent.getMetadata().name(), 0, 0);
                                        log.info("[AgentOrchestrator] 执行完成: traceId={}", traceId);
                                    })
                            .doOnError(
                                    e -> {
                                        heartbeatFuture.cancel(false);
                                        activeExecutions.remove(traceId);
                                        activeCount.decrementAndGet();
                                        // 审计：执行结束（失败）
                                        auditService.recordExecutionEnd(
                                                traceId,
                                                context.getUserId(),
                                                selectedAgent.getMetadata().name(),
                                                false,
                                                0,
                                                0,
                                                0);
                                        // 指标：执行失败
                                        metricsService.executionFailure(
                                                selectedAgent.getMetadata().name(),
                                                0,
                                                "STREAM_ERROR");
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
     * <p>使用 RouterAgent（LLM 智能路由）分析用户意图，选择最合适的 Agent。
     *
     * <p>流程：
     *
     * <ol>
     *   <li>调用 RouterAgent.route() 获取 LLM 路由决策
     *   <li>如果置信度低于阈值，返回 null 表示需要用户确认
     *   <li>否则返回选中的 Agent
     * </ol>
     *
     * @param request 执行请求
     * @return 选中的 Agent，如果需要确认则返回 null
     */
    private Agent routeAgent(AgentRequest request) {
        try {
            // 调用 RouterAgent 获取路由决策
            RoutingDecision decision = routerAgent.route(request);

            log.info(
                    "[AgentOrchestrator] 路由决策: agent={}, confidence={}%, reason={}",
                    decision.targetAgent(), decision.getConfidencePercent(), decision.reason());

            // 检查置信度
            if (decision.needsConfirmation()) {
                log.info(
                        "[AgentOrchestrator] 低置信度路由，需要用户确认: agent={}, confidence={}%",
                        decision.targetAgent(), decision.getConfidencePercent());
                // 发送需要确认的事件，等待用户选择
                // 这里返回 null，由 executeStream 发送确认事件
                return null;
            }

            // 根据决策获取 Agent
            Agent selectedAgent = agentRegistry.getAgent(decision.targetAgent()).orElse(null);

            if (selectedAgent == null) {
                log.warn(
                        "[AgentOrchestrator] 路由目标 Agent 不存在: {}，降级到 ChatAgent",
                        decision.targetAgent());
                // 降级到 ChatAgent
                return agentRegistry.getAgent("chat").orElse(null);
            }

            return selectedAgent;

        } catch (Exception e) {
            log.error("[AgentOrchestrator] 路由失败: {}", e.getMessage(), e);
            // 降级：使用 ChatAgent
            return agentRegistry.getAgent("chat").orElse(null);
        }
    }

    /**
     * 路由到指定的 Agent（用户确认后使用）
     *
     * @param agentName Agent 名称
     * @return Agent 实例
     */
    public Agent routeToAgent(String agentName) {
        return agentRegistry.getAgent(agentName).orElse(null);
    }

    /**
     * 确认操作
     *
     * <p>支持两种确认场景：
     *
     * <ol>
     *   <li>路由确认（低置信度路由，用户确认选择）
     *   <li>敏感操作确认（Agent 执行过程中的敏感操作）
     * </ol>
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

        // 审计：确认结果
        auditService.recordConfirmationResult(traceId, operatorId, confirmationId, approved);

        // 指标：路由确认
        metricsService.recordRoutingConfirmation(approved);

        // 路由确认：确认后清理执行记录
        if (execution.routingDecision != null && "routing_confirm".equals(confirmationId)) {
            if (!approved) {
                log.info("[AgentOrchestrator] 用户拒绝路由选择: traceId={}", traceId);
                // 审计：执行取消
                auditService.recordExecutionCancel(traceId, operatorId, "router", "用户拒绝路由选择");
            }
            // 清理等待确认的执行记录
            activeExecutions.remove(traceId);
            activeCount.decrementAndGet();
            return true;
        }

        // 敏感操作确认：通过 context 传递给 Agent
        if (execution.context != null) {
            execution.context.resolveConfirmation(confirmationId, approved);
            return true;
        }

        log.warn("[AgentOrchestrator] 确认操作失败: context 为空 traceId={}", traceId);
        return false;
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

        if (execution.context != null) {
            execution.context.cancel();
        }
        // 审计：执行取消
        auditService.recordExecutionCancel(
                traceId, execution.userId, execution.agentName, "用户取消执行");
        // 指标：执行取消
        metricsService.executionCancelled(execution.agentName);
        activeExecutions.remove(traceId);
        activeCount.decrementAndGet();
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
                                if (entry.getValue().context != null) {
                                    entry.getValue().context.cancel();
                                }
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
        AgentContext context; // 非 final，会在 executeStream 中设置
        RoutingDecision routingDecision; // 低置信度路由时的决策信息

        ActiveExecution(String traceId, String userId, String agentName) {
            this.traceId = traceId;
            this.userId = userId;
            this.agentName = agentName;
            this.startTime = System.currentTimeMillis();
            this.context = null;
            this.routingDecision = null;
        }

        /** 创建带路由决策的执行记录（低置信度路由确认场景） */
        ActiveExecution(
                String traceId, String userId, String agentName, RoutingDecision routingDecision) {
            this.traceId = traceId;
            this.userId = userId;
            this.agentName = agentName;
            this.startTime = System.currentTimeMillis();
            this.context = null;
            this.routingDecision = routingDecision;
        }

        void setContext(AgentContext ctx) {
            this.context = ctx;
        }
    }
}
