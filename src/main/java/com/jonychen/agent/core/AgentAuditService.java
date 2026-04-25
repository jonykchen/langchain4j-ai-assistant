package com.jonychen.agent.core;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jonychen.agent.entity.AgentAuditLog;
import com.jonychen.agent.repository.AgentAuditLogRepository;

import lombok.RequiredArgsConstructor;

/**
 * Agent 审计服务
 *
 * <p>记录 Agent 执行的完整审计日志，包括用户操作、系统事件、安全事件等。
 *
 * <h2>审计事件类型</h2>
 *
 * <ul>
 *   <li>EXECUTION_START - 执行开始
 *   <li>EXECUTION_END - 执行结束
 *   <li>EXECUTION_CANCEL - 执行取消
 *   <li>TOOL_CALL - 工具调用
 *   <li>CONFIRMATION_REQUIRED - 需要确认
 *   <li>CONFIRMATION_APPROVED - 确认批准
 *   <li>CONFIRMATION_REJECTED - 确认拒绝
 *   <li>AGENT_DELEGATION - Agent 委托
 *   <li>RATE_LIMIT_HIT - 速率限制触发
 *   <li>PERMISSION_DENIED - 权限拒绝
 * </ul>
 *
 * <h2>审计记录</h2>
 *
 * <p>每条审计记录包含：
 *
 * <ul>
 *   <li>事件 ID（唯一标识）
 *   <li>追踪 ID（关联执行）
 *   <li>用户 ID
 *   <li>Agent 名称
 *   <li>事件类型
 *   <li>事件详情
 *   <li>时间戳
 *   <li>客户端 IP
 *   <li>User-Agent
 * </ul>
 *
 * <h2>存储策略</h2>
 *
 * <p>采用双写策略：内存缓存（实时查询）+ 异步数据库持久化（持久化存储）。
 *
 * @author jonychen
 */
@Service
@RequiredArgsConstructor
public class AgentAuditService {

    private static final Logger log = LoggerFactory.getLogger(AgentAuditService.class);
    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("AGENT_AUDIT");

    private final AgentAuditLogRepository auditLogRepository;

    /** 审计事件缓存（用于实时查询，数据库持久化后仍可保留短期缓存） */
    private final ConcurrentHashMap<String, AuditEvent> eventCache = new ConcurrentHashMap<>();

    /**
     * 记录执行开始
     *
     * @param traceId 追踪 ID
     * @param userId 用户 ID
     * @param agentName Agent 名称
     * @param userInput 用户输入
     * @param clientIp 客户端 IP
     * @param userAgent User-Agent
     */
    public void recordExecutionStart(
            String traceId,
            String userId,
            String agentName,
            String userInput,
            String clientIp,
            String userAgent) {

        AuditEvent event =
                AuditEvent.builder()
                        .eventId(generateEventId())
                        .traceId(traceId)
                        .userId(userId)
                        .agentName(agentName)
                        .eventType(AuditEventType.EXECUTION_START)
                        .timestamp(Instant.now())
                        .clientIp(clientIp)
                        .userAgent(userAgent)
                        .details(
                                Map.of(
                                        "userInput",
                                        truncate(userInput, 500),
                                        "inputLength",
                                        userInput != null ? userInput.length() : 0))
                        .build();

        recordEvent(event);
    }

    /**
     * 记录执行结束
     *
     * @param traceId 追踪 ID
     * @param userId 用户 ID
     * @param agentName Agent 名称
     * @param success 是否成功
     * @param outputLength 输出长度
     * @param durationMs 执行耗时
     * @param totalSteps 总步骤数
     */
    public void recordExecutionEnd(
            String traceId,
            String userId,
            String agentName,
            boolean success,
            int outputLength,
            long durationMs,
            int totalSteps) {

        AuditEvent event =
                AuditEvent.builder()
                        .eventId(generateEventId())
                        .traceId(traceId)
                        .userId(userId)
                        .agentName(agentName)
                        .eventType(
                                success
                                        ? AuditEventType.EXECUTION_END
                                        : AuditEventType.EXECUTION_ERROR)
                        .timestamp(Instant.now())
                        .details(
                                Map.of(
                                        "success",
                                        success,
                                        "outputLength",
                                        outputLength,
                                        "durationMs",
                                        durationMs,
                                        "totalSteps",
                                        totalSteps))
                        .build();

        recordEvent(event);
    }

    /**
     * 记录执行取消
     *
     * @param traceId 追踪 ID
     * @param userId 用户 ID
     * @param agentName Agent 名称
     * @param reason 取消原因
     */
    public void recordExecutionCancel(
            String traceId, String userId, String agentName, String reason) {
        AuditEvent event =
                AuditEvent.builder()
                        .eventId(generateEventId())
                        .traceId(traceId)
                        .userId(userId)
                        .agentName(agentName)
                        .eventType(AuditEventType.EXECUTION_CANCEL)
                        .timestamp(Instant.now())
                        .details(Map.of("reason", reason != null ? reason : "用户取消"))
                        .build();

        recordEvent(event);
    }

    /**
     * 记录工具调用
     *
     * @param traceId 追踪 ID
     * @param userId 用户 ID
     * @param agentName Agent 名称
     * @param toolName 工具名称
     * @param toolParams 工具参数
     * @param success 是否成功
     * @param durationMs 执行耗时
     */
    public void recordToolCall(
            String traceId,
            String userId,
            String agentName,
            String toolName,
            Map<String, Object> toolParams,
            boolean success,
            long durationMs) {

        AuditEvent event =
                AuditEvent.builder()
                        .eventId(generateEventId())
                        .traceId(traceId)
                        .userId(userId)
                        .agentName(agentName)
                        .eventType(AuditEventType.TOOL_CALL)
                        .timestamp(Instant.now())
                        .details(
                                Map.of(
                                        "toolName",
                                        toolName,
                                        "params",
                                        sanitizeParams(toolParams),
                                        "success",
                                        success,
                                        "durationMs",
                                        durationMs))
                        .build();

        recordEvent(event);
    }

    /**
     * 记录确认请求
     *
     * @param traceId 追踪 ID
     * @param userId 用户 ID
     * @param confirmationId 确认 ID
     * @param operation 操作名称
     * @param riskLevel 风险等级
     */
    public void recordConfirmationRequired(
            String traceId,
            String userId,
            String confirmationId,
            String operation,
            String riskLevel) {

        AuditEvent event =
                AuditEvent.builder()
                        .eventId(generateEventId())
                        .traceId(traceId)
                        .userId(userId)
                        .agentName(null)
                        .eventType(AuditEventType.CONFIRMATION_REQUIRED)
                        .timestamp(Instant.now())
                        .details(
                                Map.of(
                                        "confirmationId",
                                        confirmationId,
                                        "operation",
                                        operation,
                                        "riskLevel",
                                        riskLevel))
                        .build();

        recordEvent(event);
    }

    /**
     * 记录确认结果
     *
     * @param traceId 追踪 ID
     * @param userId 用户 ID
     * @param confirmationId 确认 ID
     * @param approved 是否批准
     */
    public void recordConfirmationResult(
            String traceId, String userId, String confirmationId, boolean approved) {
        AuditEvent event =
                AuditEvent.builder()
                        .eventId(generateEventId())
                        .traceId(traceId)
                        .userId(userId)
                        .agentName(null)
                        .eventType(
                                approved
                                        ? AuditEventType.CONFIRMATION_APPROVED
                                        : AuditEventType.CONFIRMATION_REJECTED)
                        .timestamp(Instant.now())
                        .details(Map.of("confirmationId", confirmationId, "approved", approved))
                        .build();

        recordEvent(event);
    }

    /**
     * 记录 Agent 委托
     *
     * @param traceId 追踪 ID
     * @param userId 用户 ID
     * @param sourceAgent 源 Agent
     * @param targetAgent 目标 Agent
     * @param input 委托输入
     */
    public void recordAgentDelegation(
            String traceId, String userId, String sourceAgent, String targetAgent, String input) {

        AuditEvent event =
                AuditEvent.builder()
                        .eventId(generateEventId())
                        .traceId(traceId)
                        .userId(userId)
                        .agentName(sourceAgent)
                        .eventType(AuditEventType.AGENT_DELEGATION)
                        .timestamp(Instant.now())
                        .details(
                                Map.of(
                                        "targetAgent",
                                        targetAgent,
                                        "inputLength",
                                        input != null ? input.length() : 0))
                        .build();

        recordEvent(event);
    }

    /**
     * 记录速率限制触发
     *
     * @param userId 用户 ID
     * @param limitType 限制类型
     * @param limitValue 限制值
     */
    public void recordRateLimitHit(String userId, String limitType, int limitValue) {

        AuditEvent event =
                AuditEvent.builder()
                        .eventId(generateEventId())
                        .traceId(null)
                        .userId(userId)
                        .agentName(null)
                        .eventType(AuditEventType.RATE_LIMIT_HIT)
                        .timestamp(Instant.now())
                        .details(Map.of("limitType", limitType, "limitValue", limitValue))
                        .build();

        recordEvent(event);
    }

    /**
     * 记录权限拒绝
     *
     * @param traceId 追踪 ID
     * @param userId 用户 ID
     * @param agentName Agent 名称
     * @param requiredPermission 需要的权限
     */
    public void recordPermissionDenied(
            String traceId, String userId, String agentName, String requiredPermission) {

        AuditEvent event =
                AuditEvent.builder()
                        .eventId(generateEventId())
                        .traceId(traceId)
                        .userId(userId)
                        .agentName(agentName)
                        .eventType(AuditEventType.PERMISSION_DENIED)
                        .timestamp(Instant.now())
                        .details(Map.of("requiredPermission", requiredPermission))
                        .build();

        recordEvent(event);
    }

    /**
     * 记录审计事件（双写：内存缓存 + 异步数据库持久化）
     *
     * @param event 审计事件
     */
    private void recordEvent(AuditEvent event) {
        // 1. 缓存事件（内存）
        eventCache.put(event.eventId(), event);

        // 2. 写入审计日志（SLF4J）
        AUDIT_LOG.info(
                "[AUDIT] event={} traceId={} user={} agent={} type={} details={}",
                event.eventId(),
                event.traceId(),
                event.userId(),
                event.agentName(),
                event.eventType(),
                event.details());

        // 3. 异步写入数据库
        persistAuditLogAsync(event);

        log.debug(
                "[AgentAuditService] 审计事件已记录: eventId={}, type={}",
                event.eventId(),
                event.eventType());
    }

    /** 异步持久化审计日志到数据库 */
    @Async
    @Transactional
    public void persistAuditLogAsync(AuditEvent event) {
        try {
            AgentAuditLog auditLog =
                    AgentAuditLog.create(
                            event.traceId(),
                            event.userId(),
                            event.agentName(),
                            event.eventType(),
                            event.clientIp(),
                            event.userAgent(),
                            event.details());
            auditLog.setEventId(event.eventId());
            auditLog.setTimestamp(event.timestamp());
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error(
                    "[AgentAuditService] 审计日志持久化失败: eventId={}, error={}",
                    event.eventId(),
                    e.getMessage(),
                    e);
        }
    }

    private String generateEventId() {
        return "evt_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return null;
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    /** 敏感参数脱敏 */
    private Map<String, Object> sanitizeParams(Map<String, Object> params) {
        if (params == null) return Map.of();

        Map<String, Object> sanitized = new java.util.HashMap<>(params);
        // 脱敏敏感字段
        for (String key : sanitized.keySet()) {
            if (key.toLowerCase().contains("password")
                    || key.toLowerCase().contains("secret")
                    || key.toLowerCase().contains("token")
                    || key.toLowerCase().contains("key")) {
                sanitized.put(key, "***");
            }
        }
        return sanitized;
    }

    // ==================== 审计事件数据结构 ====================

    /** 审计事件类型 */
    public enum AuditEventType {
        EXECUTION_START,
        EXECUTION_END,
        EXECUTION_ERROR,
        EXECUTION_CANCEL,
        TOOL_CALL,
        CONFIRMATION_REQUIRED,
        CONFIRMATION_APPROVED,
        CONFIRMATION_REJECTED,
        AGENT_DELEGATION,
        RATE_LIMIT_HIT,
        PERMISSION_DENIED
    }

    /** 审计事件 */
    public record AuditEvent(
            String eventId,
            String traceId,
            String userId,
            String agentName,
            AuditEventType eventType,
            Instant timestamp,
            String clientIp,
            String userAgent,
            Map<String, Object> details) {

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String eventId;
            private String traceId;
            private String userId;
            private String agentName;
            private AuditEventType eventType;
            private Instant timestamp;
            private String clientIp;
            private String userAgent;
            private Map<String, Object> details;

            public Builder eventId(String eventId) {
                this.eventId = eventId;
                return this;
            }

            public Builder traceId(String traceId) {
                this.traceId = traceId;
                return this;
            }

            public Builder userId(String userId) {
                this.userId = userId;
                return this;
            }

            public Builder agentName(String agentName) {
                this.agentName = agentName;
                return this;
            }

            public Builder eventType(AuditEventType eventType) {
                this.eventType = eventType;
                return this;
            }

            public Builder timestamp(Instant timestamp) {
                this.timestamp = timestamp;
                return this;
            }

            public Builder clientIp(String clientIp) {
                this.clientIp = clientIp;
                return this;
            }

            public Builder userAgent(String userAgent) {
                this.userAgent = userAgent;
                return this;
            }

            public Builder details(Map<String, Object> details) {
                this.details = details;
                return this;
            }

            public AuditEvent build() {
                return new AuditEvent(
                        eventId, traceId, userId, agentName, eventType, timestamp, clientIp,
                        userAgent, details);
            }
        }
    }
}
