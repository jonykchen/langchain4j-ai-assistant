package com.jonychen.agent.entity;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.jonychen.agent.core.AgentAuditService.AuditEventType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 审计日志实体
 *
 * @author jonychen
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "agent_audit_logs", schema = "audit")
public class AgentAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", unique = true, nullable = false, length = 36)
    private String eventId;

    @Column(name = "trace_id", length = 36)
    private String traceId;

    @Column(name = "user_id", length = 64)
    private String userId;

    @Column(name = "agent_name", length = 50)
    private String agentName;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private AuditEventType eventType;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "client_ip", length = 50)
    private String clientIp;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_data", columnDefinition = "jsonb")
    private Map<String, Object> eventData;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (eventId == null) {
            eventId = "evt_" + UUID.randomUUID().toString().substring(0, 8);
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public static AgentAuditLog create(
            String traceId,
            String userId,
            String agentName,
            AuditEventType eventType,
            String clientIp,
            String userAgent,
            Map<String, Object> details) {
        return AgentAuditLog.builder()
                .traceId(traceId)
                .userId(userId)
                .agentName(agentName)
                .eventType(eventType)
                .clientIp(clientIp)
                .userAgent(userAgent)
                .eventData(details)
                .build();
    }
}
