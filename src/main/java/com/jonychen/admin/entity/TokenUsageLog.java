package com.jonychen.admin.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token 使用记录实体
 *
 * @author jonychen
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "token_usage_logs", schema = "audit")
public class TokenUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 用户ID */
    @Column(name = "user_id", nullable = false)
    private String userId;

    /** 会话ID */
    @Column(name = "session_id")
    private String sessionId;

    /** 会话ID（对话） */
    @Column(name = "conversation_id")
    private String conversationId;

    /** 消息ID */
    @Column(name = "message_id")
    private Long messageId;

    /** 模型名称 */
    @Column(name = "model_name", nullable = false)
    private String modelName;

    /** 模型提供者 */
    @Column(name = "model_provider")
    private String modelProvider;

    /** 提示 Token 数 */
    @Column(name = "prompt_tokens")
    private Integer promptTokens = 0;

    /** 完成 Token 数 */
    @Column(name = "completion_tokens")
    private Integer completionTokens = 0;

    /** 总 Token 数 */
    @Column(name = "total_tokens")
    private Integer totalTokens = 0;

    /** 费用 */
    @Column(name = "cost", precision = 10, scale = 6)
    private BigDecimal cost = BigDecimal.ZERO;

    /** 货币 */
    @Column(name = "currency", length = 10)
    private String currency = "USD";

    /** 请求类型 */
    @Column(name = "request_type", length = 20)
    private String requestType;

    /** 延迟（毫秒） */
    @Column(name = "latency_ms")
    private Long latencyMs;

    /** 状态 */
    @Column(name = "status", length = 20)
    private String status = "success";

    /** 错误消息 */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** 关联的 Trace ID */
    @Column(name = "trace_id", length = 36)
    private String traceId;

    /** 客户端IP */
    @Column(name = "client_ip", length = 50)
    private String clientIp;

    /** 请求时间 */
    @Column(name = "request_time")
    private LocalDateTime requestTime;

    /** 创建时间 */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (requestTime == null) {
            requestTime = LocalDateTime.now();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (totalTokens == null && promptTokens != null && completionTokens != null) {
            totalTokens = promptTokens + completionTokens;
        }
    }

    /** 创建使用记录 */
    public static TokenUsageLog create(
            String userId,
            String sessionId,
            String modelName,
            int promptTokens,
            int completionTokens,
            double cost) {
        TokenUsageLog log = new TokenUsageLog();
        log.setUserId(userId);
        log.setSessionId(sessionId);
        log.setModelName(modelName);
        log.setPromptTokens(promptTokens);
        log.setCompletionTokens(completionTokens);
        log.setTotalTokens(promptTokens + completionTokens);
        log.setCost(BigDecimal.valueOf(cost));
        log.setCreatedAt(LocalDateTime.now());
        return log;
    }
}
