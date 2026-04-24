package com.jonychen.agent.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jonychen.observability.trace.AgentTraceService;
import com.jonychen.tool.ToolRegistry;

import dev.langchain4j.memory.ChatMemory;

/**
 * Agent 执行上下文
 *
 * <p>贯穿 Agent 执行全生命周期的上下文对象，持有追踪信息、工具注册表、 聊天记忆、执行选项等。同时管理确认等待机制和事件收集。
 *
 * <p>核心职责：
 *
 * <ul>
 *   <li>追踪 ID 管理：每个执行上下文有唯一的 traceId
 *   <li>确认等待机制：敏感操作需要用户确认后才能继续执行
 *   <li>事件收集：记录执行过程中产生的所有事件
 *   <li>取消控制：支持外部取消执行
 *   <li>序列号生成：为事件生成全局递增序号
 * </ul>
 *
 * @author jonychen
 */
public class AgentContext {

    private static final Logger log = LoggerFactory.getLogger(AgentContext.class);

    private final String traceId;
    private final String sessionId;
    private final String userId;
    private final AgentType agentType;
    private final ToolRegistry toolRegistry;
    private final ChatMemory chatMemory;
    private final AgentTraceService traceService;
    private final AgentRequestOptions options;
    private final Map<String, Object> variables;
    private final List<AgentEvent> events;

    /** 是否已取消 */
    private volatile boolean cancelled;

    /** 全局事件序号（由 Orchestrator 初始化，确保跨组件序号连续） 前端据此检测是否有事件丢失 */
    private final AtomicInteger sequenceCounter;

    /** 确认等待机制（统一管理入口，ToolRegistry 不再自行管理确认生命周期） 生产环境建议外置到 Redis */
    private final ConcurrentHashMap<String, CompletableFuture<Boolean>> pendingConfirmations =
            new ConcurrentHashMap<>();

    /**
     * 创建 Agent 执行上下文
     *
     * @param traceId 追踪 ID
     * @param sessionId 会话 ID
     * @param userId 用户 ID
     * @param agentType Agent 类型
     * @param toolRegistry 工具注册表
     * @param chatMemory 聊天记忆
     * @param traceService 追踪服务
     * @param options 执行选项
     */
    public AgentContext(
            String traceId,
            String sessionId,
            String userId,
            AgentType agentType,
            ToolRegistry toolRegistry,
            ChatMemory chatMemory,
            AgentTraceService traceService,
            AgentRequestOptions options) {
        this.traceId = traceId;
        this.sessionId = sessionId;
        this.userId = userId;
        this.agentType = agentType;
        this.toolRegistry = toolRegistry;
        this.chatMemory = chatMemory;
        this.traceService = traceService;
        this.options = options;
        this.variables = new ConcurrentHashMap<>();
        this.events = Collections.synchronizedList(new ArrayList<>());
        this.sequenceCounter = new AtomicInteger(0);
    }

    /**
     * 创建简单上下文（自动生成 traceId）
     *
     * @param sessionId 会话 ID
     * @param userId 用户 ID
     * @param agentType Agent 类型
     * @param options 执行选项
     * @return Agent 执行上下文
     */
    public static AgentContext of(
            String sessionId, String userId, AgentType agentType, AgentRequestOptions options) {
        return new AgentContext(
                UUID.randomUUID().toString(),
                sessionId,
                userId,
                agentType,
                null,
                null,
                null,
                options);
    }

    /**
     * 记录事件（用于 SSE 推送和追踪）
     *
     * @param event Agent 事件
     */
    public void emitEvent(AgentEvent event) {
        events.add(event);
        log.debug(
                "[AgentContext] 事件已记录: traceId={}, seq={}, type={}",
                traceId,
                event.sequenceNumber(),
                event.eventType());
    }

    /**
     * 取消执行
     *
     * <p>设置取消标志，并取消所有等待中的确认。 正在执行的 Agent 会在下一个循环迭代中检测到取消状态并退出。
     */
    public void cancel() {
        this.cancelled = true;
        log.info("[AgentContext] 执行已取消: traceId={}", traceId);
        // 取消所有等待中的确认
        pendingConfirmations
                .values()
                .forEach(
                        f -> {
                            if (!f.isDone()) {
                                f.completeExceptionally(new CancellationException("执行已取消"));
                            }
                        });
        pendingConfirmations.clear();
    }

    /**
     * 检查是否已取消
     *
     * @return 是否已取消
     */
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * 注册等待中的确认
     *
     * <p>当敏感操作需要用户确认时，调用此方法注册一个确认等待。 Agent 执行线程会阻塞在返回的 CompletableFuture 上，直到用户确认或超时。
     *
     * @param confirmationId 确认 ID
     * @return 确认结果的 Future，true 表示用户批准，false 表示用户拒绝
     */
    public CompletableFuture<Boolean> awaitConfirmation(String confirmationId) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        pendingConfirmations.put(confirmationId, future);
        log.info("[AgentContext] 等待用户确认: traceId={}, confirmationId={}", traceId, confirmationId);
        return future;
    }

    /**
     * 完成确认（由 AgentOrchestrator 在用户确认后调用）
     *
     * @param confirmationId 确认 ID
     * @param approved 是否批准
     */
    public void resolveConfirmation(String confirmationId, boolean approved) {
        CompletableFuture<Boolean> future = pendingConfirmations.remove(confirmationId);
        if (future != null && !future.isDone()) {
            future.complete(approved);
            log.info(
                    "[AgentContext] 确认结果: traceId={}, confirmationId={}, approved={}",
                    traceId,
                    confirmationId,
                    approved);
        } else {
            log.warn(
                    "[AgentContext] 确认已过期或不存在: traceId={}, confirmationId={}",
                    traceId,
                    confirmationId);
        }
    }

    /**
     * 获取下一个事件序号
     *
     * @return 递增的事件序号
     */
    public int nextSequenceNumber() {
        return sequenceCounter.getAndIncrement();
    }

    /**
     * 设置上下文变量
     *
     * @param key 变量名
     * @param value 变量值
     */
    public void setVariable(String key, Object value) {
        variables.put(key, value);
    }

    /**
     * 获取上下文变量
     *
     * @param key 变量名
     * @return 变量值
     */
    public Object getVariable(String key) {
        return variables.get(key);
    }

    // ===== Getters =====

    public String getTraceId() {
        return traceId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public AgentType getAgentType() {
        return agentType;
    }

    public ToolRegistry getToolRegistry() {
        return toolRegistry;
    }

    public ChatMemory getChatMemory() {
        return chatMemory;
    }

    public AgentTraceService getTraceService() {
        return traceService;
    }

    public AgentRequestOptions getOptions() {
        return options;
    }

    public Map<String, Object> getVariables() {
        return Collections.unmodifiableMap(variables);
    }

    public List<AgentEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }

    public AtomicInteger getSequenceCounter() {
        return sequenceCounter;
    }
}
