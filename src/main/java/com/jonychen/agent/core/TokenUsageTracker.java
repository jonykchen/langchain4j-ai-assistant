package com.jonychen.agent.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jonychen.admin.service.TokenUsageService;

import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;

/**
 * Token 使用追踪服务
 *
 * <p>封装 Token 使用量追踪逻辑，从 ChatResponse 提取并记录到数据库和 Prometheus 指标。
 *
 * @author jonychen
 */
@Service
@RequiredArgsConstructor
public class TokenUsageTracker {

    private static final Logger log = LoggerFactory.getLogger(TokenUsageTracker.class);

    private final TokenUsageService tokenUsageService;
    private final AgentMetricsService metricsService;

    /**
     * 从 ChatResponse 提取 Token 使用量并记录
     *
     * @param response LLM 响应
     * @param traceId 追踪 ID
     * @param userId 用户 ID
     * @param sessionId 会话 ID
     * @param modelName 模型名称
     * @return 提取的 TokenUsage，如果响应为空则返回 empty
     */
    @Transactional
    public TokenUsage extractAndRecord(
            ChatResponse response,
            String traceId,
            String userId,
            String sessionId,
            String modelName) {

        if (response == null) {
            log.warn("[TokenUsageTracker] 响应为空，跳过 Token 追踪");
            return TokenUsage.empty();
        }

        var usage = response.tokenUsage();
        if (usage == null) {
            log.debug("[TokenUsageTracker] 响应无 Token 使用信息");
            return TokenUsage.empty();
        }

        int promptTokens = usage.inputTokenCount();
        int completionTokens = usage.outputTokenCount();
        TokenUsage tokenUsage = TokenUsage.of(promptTokens, completionTokens);

        // 记录到数据库
        try {
            tokenUsageService.recordUsage(userId, sessionId, traceId, modelName, response);
            log.info(
                    "[TokenUsageTracker] Token 使用已记录: traceId={}, model={}, prompt={}, completion={}",
                    traceId,
                    modelName,
                    promptTokens,
                    completionTokens);
        } catch (Exception e) {
            log.error("[TokenUsageTracker] Token 使用记录失败: {}", e.getMessage(), e);
        }

        // 上报 Prometheus 指标
        try {
            metricsService.recordTokenUsage(modelName, promptTokens, completionTokens);
        } catch (Exception e) {
            log.warn("[TokenUsageTracker] Prometheus 指标上报失败: {}", e.getMessage());
        }

        return tokenUsage;
    }

    /**
     * 手动记录 Token 使用量（用于流式响应）
     *
     * @param traceId 追踪 ID
     * @param userId 用户 ID
     * @param sessionId 会话 ID
     * @param modelName 模型名称
     * @param promptTokens 输入 Token 数
     * @param completionTokens 输出 Token 数
     */
    @Transactional
    public void recordManual(
            String traceId,
            String userId,
            String sessionId,
            String modelName,
            int promptTokens,
            int completionTokens) {

        try {
            tokenUsageService.recordStreamingUsage(
                    userId, sessionId, modelName, promptTokens, completionTokens);
            metricsService.recordTokenUsage(modelName, promptTokens, completionTokens);
            log.info(
                    "[TokenUsageTracker] 手动记录 Token: traceId={}, model={}, prompt={}, completion={}",
                    traceId,
                    modelName,
                    promptTokens,
                    completionTokens);
        } catch (Exception e) {
            log.error("[TokenUsageTracker] 手动记录 Token 失败: {}", e.getMessage(), e);
        }
    }
}
