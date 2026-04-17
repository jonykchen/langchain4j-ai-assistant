package com.jonychen.admin.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Token 使用记录实体
 *
 * @author jonychen
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "token_usage_logs")
public class TokenUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 用户ID
     */
    @Column(name = "user_id", nullable = false)
    private String userId;

    /**
     * 会话ID
     */
    @Column(name = "session_id")
    private String sessionId;

    /**
     * 模型名称
     */
    @Column(name = "model_name", nullable = false)
    private String modelName;

    /**
     * 提示 Token 数
     */
    @Column(name = "prompt_tokens", nullable = false)
    private Integer promptTokens;

    /**
     * 完成 Token 数
     */
    @Column(name = "completion_tokens", nullable = false)
    private Integer completionTokens;

    /**
     * 总 Token 数
     */
    @Column(name = "total_tokens", nullable = false)
    private Integer totalTokens;

    /**
     * 费用
     */
    @Column(name = "cost", nullable = false, precision = 10, scale = 6)
    private BigDecimal cost;

    /**
     * 货币
     */
    @Column(name = "currency", length = 10)
    private String currency = "USD";

    /**
     * 请求类型
     */
    @Column(name = "request_type", length = 20)
    private String requestType;

    /**
     * 创建时间
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /**
     * 创建使用记录
     */
    public static TokenUsageLog create(String userId, String sessionId, String modelName,
                                        int promptTokens, int completionTokens, double cost) {
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