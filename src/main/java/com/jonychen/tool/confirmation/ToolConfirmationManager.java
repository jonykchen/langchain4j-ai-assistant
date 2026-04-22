package com.jonychen.tool.confirmation;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 工具确认管理器
 *
 * @author jonychen
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolConfirmationManager {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    /** 确认请求 TTL（默认 5 分钟） */
    private static final Duration CONFIRMATION_TTL = Duration.ofMinutes(5);

    private static final String KEY_PREFIX = "tool:confirmation:";

    /**
     * 创建待确认请求
     *
     * @param toolName 工具名称
     * @param params 参数
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @param riskLevel 风险等级
     * @return 确认ID
     */
    public String createConfirmationRequest(
            String toolName,
            java.util.Map<String, Object> params,
            String sessionId,
            String userId,
            ToolRiskLevel riskLevel) {
        PendingConfirmation confirmation =
                PendingConfirmation.create(toolName, params, sessionId, userId, riskLevel);

        // 生成确认消息
        String message = generateConfirmationMessage(toolName, params, riskLevel);
        confirmation = confirmation.withMessage(message);

        // 存储到 Redis
        storeConfirmation(confirmation);

        log.info(
                "Created confirmation request: {} for tool: {}",
                confirmation.confirmationId(),
                toolName);

        return confirmation.confirmationId();
    }

    /**
     * 用户确认
     *
     * @param confirmationId 确认ID
     * @param approved 是否批准
     * @param userId 确认人ID
     * @return 确认结果
     */
    public ConfirmationResult confirm(String confirmationId, boolean approved, String userId) {
        Optional<PendingConfirmation> optConfirmation = getConfirmation(confirmationId);

        if (optConfirmation.isEmpty()) {
            return ConfirmationResult.expired();
        }

        PendingConfirmation confirmation = optConfirmation.get();

        // 检查是否已处理
        if (confirmation.isProcessed()) {
            return ConfirmationResult.alreadyProcessed();
        }

        // 检查是否过期
        if (confirmation.isExpired()) {
            removeConfirmation(confirmationId);
            return ConfirmationResult.expired();
        }

        // 更新状态
        ConfirmationStatus newStatus =
                approved ? ConfirmationStatus.APPROVED : ConfirmationStatus.REJECTED;
        confirmation = confirmation.withStatus(newStatus).withConfirmedBy(userId);

        // 存储
        storeConfirmation(confirmation);

        log.info(
                "Confirmation {} {} by user: {}",
                confirmationId,
                approved ? "approved" : "rejected",
                userId);

        return approved
                ? ConfirmationResult.approved(confirmation)
                : ConfirmationResult.rejected(confirmation, "User rejected");
    }

    /**
     * 获取待确认请求
     *
     * @param confirmationId 确认ID
     * @return 待确认请求（可选）
     */
    public Optional<PendingConfirmation> getConfirmation(String confirmationId) {
        String key = KEY_PREFIX + confirmationId;
        String json = redisTemplate.opsForValue().get(key);

        if (json == null) {
            return Optional.empty();
        }

        try {
            PendingConfirmation confirmation =
                    objectMapper.readValue(json, PendingConfirmation.class);
            return Optional.of(confirmation);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize confirmation: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 取消确认请求
     *
     * @param confirmationId 确认ID
     */
    public void cancelConfirmation(String confirmationId) {
        Optional<PendingConfirmation> optConfirmation = getConfirmation(confirmationId);
        if (optConfirmation.isPresent()) {
            PendingConfirmation confirmation =
                    optConfirmation.get().withStatus(ConfirmationStatus.CANCELLED);
            storeConfirmation(confirmation);
            log.info("Confirmation {} cancelled", confirmationId);
        }
    }

    /** 存储确认请求 */
    private void storeConfirmation(PendingConfirmation confirmation) {
        String key = KEY_PREFIX + confirmation.confirmationId();
        try {
            String json = objectMapper.writeValueAsString(confirmation);
            redisTemplate.opsForValue().set(key, json, CONFIRMATION_TTL);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize confirmation: {}", e.getMessage());
        }
    }

    /** 移除确认请求 */
    private void removeConfirmation(String confirmationId) {
        String key = KEY_PREFIX + confirmationId;
        redisTemplate.delete(key);
    }

    /** 生成确认消息 */
    private String generateConfirmationMessage(
            String toolName, java.util.Map<String, Object> params, ToolRiskLevel riskLevel) {
        StringBuilder sb = new StringBuilder();
        sb.append("⚠️ 工具执行确认请求\n\n");
        sb.append("工具: ").append(toolName).append("\n");
        sb.append("风险等级: ").append(riskLevel.getDisplayName()).append("\n\n");

        if (params != null && !params.isEmpty()) {
            sb.append("参数:\n");
            params.forEach(
                    (k, v) -> {
                        String valueStr = String.valueOf(v);
                        if (valueStr.length() > 100) {
                            valueStr = valueStr.substring(0, 100) + "...";
                        }
                        sb.append("  - ").append(k).append(": ").append(valueStr).append("\n");
                    });
        }

        sb.append("\n请确认是否执行此操作？(确认/取消)");
        return sb.toString();
    }
}
