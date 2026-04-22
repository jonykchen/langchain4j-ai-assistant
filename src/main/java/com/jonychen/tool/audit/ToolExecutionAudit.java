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
@Table(name = "tool_execution_audits")
public class ToolExecutionAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 执行ID */
    @Column(unique = true, nullable = false)
    private String executionId;

    /** 工具名称 */
    @Column(nullable = false)
    private String toolName;

    /** 会话ID */
    private String sessionId;

    /** 用户ID */
    private String userId;

    /** 参数（JSON格式，已脱敏） */
    @Column(columnDefinition = "TEXT")
    private String params;

    /** 是否成功 */
    private boolean success;

    /** 结果（脱敏后） */
    @Column(columnDefinition = "TEXT")
    private String result;

    /** 错误消息 */
    @Column(length = 1000)
    private String errorMessage;

    /** 执行时间（毫秒） */
    private long executionTimeMs;

    /** 风险等级 */
    @Enumerated(EnumType.STRING)
    private ToolRiskLevel riskLevel;

    /** 是否需要确认 */
    private boolean confirmationRequired;

    /** 是否已确认 */
    private boolean confirmed;

    /** 确认人 */
    private String confirmedBy;

    /** 执行时间 */
    private LocalDateTime executedAt;

    /** 客户端IP */
    private String clientIp;

    @PrePersist
    void prePersist() {
        if (executedAt == null) {
            executedAt = LocalDateTime.now();
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
