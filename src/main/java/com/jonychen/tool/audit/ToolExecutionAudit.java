package com.jonychen.tool.audit;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import com.jonychen.tool.confirmation.ToolRiskLevel;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具执行审计实体
 *
 * @author jonychen
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "tool_execution_audits", schema = "audit")
public class ToolExecutionAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 执行ID */
    @Column(name = "execution_id", unique = true, nullable = false)
    private String executionId;

    /** 工具名称 */
    @Column(name = "tool_name", nullable = false)
    private String toolName;

    /** 工具类别 */
    @Column(name = "tool_category")
    private String toolCategory;

    /** 用户ID */
    @Column(name = "user_id")
    private String userId;

    /** 会话ID */
    @Column(name = "session_id")
    private String sessionId;

    /** 对话ID */
    @Column(name = "conversation_id")
    private String conversationId;

    /** 消息ID */
    @Column(name = "message_id")
    private Long messageId;

    /** 参数（JSON格式，已脱敏） */
    @Column(name = "params", columnDefinition = "TEXT")
    private String params;

    /** 参数哈希 */
    @Column(name = "params_hash")
    private String paramsHash;

    /** 结果（脱敏后） */
    @Column(name = "result", columnDefinition = "TEXT")
    private String result;

    /** 结果哈希 */
    @Column(name = "result_hash")
    private String resultHash;

    /** 是否成功 */
    @Column(name = "success")
    private boolean success;

    /** 错误消息 */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    /** 执行时间（毫秒） */
    @Column(name = "execution_time_ms")
    private long executionTimeMs;

    /** 重试次数 */
    @Column(name = "retry_count")
    private int retryCount = 0;

    /** 风险等级 */
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level")
    private ToolRiskLevel riskLevel;

    /** 是否需要确认 */
    @Column(name = "confirmation_required")
    private boolean confirmationRequired;

    /** 是否已确认 */
    @Column(name = "confirmed")
    private boolean confirmed;

    /** 确认人 */
    @Column(name = "confirmed_by")
    private String confirmedBy;

    /** 客户端IP */
    @Column(name = "client_ip")
    private String clientIp;

    /** 执行时间 */
    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    /** 创建时间 */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (executedAt == null) {
            executedAt = LocalDateTime.now();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /** 创建审计记录 */
    public static ToolExecutionAudit create(
            String executionId, String toolName, String sessionId, String userId) {
        ToolExecutionAudit audit = new ToolExecutionAudit();
        audit.setExecutionId(executionId);
        audit.setToolName(toolName);
        audit.setSessionId(sessionId);
        audit.setUserId(userId);
        audit.setExecutedAt(LocalDateTime.now());
        return audit;
    }
}
