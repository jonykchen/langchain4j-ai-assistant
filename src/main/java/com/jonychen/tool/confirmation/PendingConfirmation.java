package com.jonychen.tool.confirmation;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 待确认请求
 *
 * @param confirmationId 确认ID
 * @param toolName       工具名称
 * @param params         参数
 * @param sessionId      会话ID
 * @param userId         用户ID
 * @param riskLevel      风险等级
 * @param status         确认状态
 * @param createdAt      创建时间
 * @param confirmedBy    确认人
 * @param confirmedAt    确认时间
 * @param message        确认消息
 * @author jonychen
 */
public record PendingConfirmation(
        String confirmationId,
        String toolName,
        Map<String, Object> params,
        String sessionId,
        String userId,
        ToolRiskLevel riskLevel,
        ConfirmationStatus status,
        LocalDateTime createdAt,
        String confirmedBy,
        LocalDateTime confirmedAt,
        String message
) {
    /**
     * 创建新的待确认请求
     */
    public static PendingConfirmation create(String toolName, Map<String, Object> params,
                                              String sessionId, String userId, ToolRiskLevel riskLevel) {
        return new PendingConfirmation(
                UUID.randomUUID().toString(),
                toolName,
                params,
                sessionId,
                userId,
                riskLevel,
                ConfirmationStatus.PENDING,
                LocalDateTime.now(),
                null,
                null,
                null
        );
    }

    /**
     * 更新确认状态
     */
    public PendingConfirmation withStatus(ConfirmationStatus status) {
        return new PendingConfirmation(
                confirmationId, toolName, params, sessionId, userId,
                riskLevel, status, createdAt, confirmedBy, confirmedAt, message
        );
    }

    /**
     * 更新确认人
     */
    public PendingConfirmation withConfirmedBy(String userId) {
        return new PendingConfirmation(
                confirmationId, toolName, params, sessionId, this.userId,
                riskLevel, status, createdAt, userId, LocalDateTime.now(), message
        );
    }

    /**
     * 更新消息
     */
    public PendingConfirmation withMessage(String message) {
        return new PendingConfirmation(
                confirmationId, toolName, params, sessionId, userId,
                riskLevel, status, createdAt, confirmedBy, confirmedAt, message
        );
    }

    /**
     * 是否已过期（默认5分钟）
     */
    public boolean isExpired() {
        return createdAt.plusMinutes(5).isBefore(LocalDateTime.now());
    }

    /**
     * 是否已处理
     */
    public boolean isProcessed() {
        return status != ConfirmationStatus.PENDING;
    }
}