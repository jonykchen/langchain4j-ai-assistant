package com.jonychen.admin.service;

import com.jonychen.admin.entity.TokenUsageLog;
import com.jonychen.admin.repository.TokenUsageRepository;
import com.jonychen.config.ModelProperties;
import com.jonychen.metrics.BusinessMetricsService;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Token 使用记录服务
 *
 * @author jonychen
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenUsageService {

    private final TokenUsageRepository tokenUsageRepository;
    private final ModelProperties modelProperties;
    private final BusinessMetricsService businessMetricsService;

    /**
     * 模型定价（每千 Token 价格，USD）
     * 参考：https://openai.com/pricing
     */
    private static final Map<String, Pricing> MODEL_PRICING = new HashMap<>();

    static {
        // OpenAI
        MODEL_PRICING.put("gpt-4o", new Pricing(0.005, 0.015));
        MODEL_PRICING.put("gpt-4o-mini", new Pricing(0.00015, 0.0006));
        MODEL_PRICING.put("gpt-4", new Pricing(0.03, 0.06));
        MODEL_PRICING.put("gpt-4-turbo", new Pricing(0.01, 0.03));
        MODEL_PRICING.put("gpt-3.5-turbo", new Pricing(0.0005, 0.0015));
        // DeepSeek
        MODEL_PRICING.put("deepseek-chat", new Pricing(0.00014, 0.00028));
        MODEL_PRICING.put("deepseek-coder", new Pricing(0.00014, 0.00028));
        // 阿里云 DashScope (qwen 系列)
        MODEL_PRICING.put("qwen-plus", new Pricing(0.0004, 0.0012));
        MODEL_PRICING.put("qwen-turbo", new Pricing(0.0002, 0.0006));
        MODEL_PRICING.put("qwen-max", new Pricing(0.0008, 0.002));
        // 智谱 GLM
        MODEL_PRICING.put("glm-4", new Pricing(0.001, 0.001));
        MODEL_PRICING.put("glm-4-flash", new Pricing(0.0001, 0.0001));
        // 硅基流动（免费模型）
        MODEL_PRICING.put("Qwen/Qwen2.5-7B-Instruct", new Pricing(0.0, 0.0));
        MODEL_PRICING.put("deepseek-ai/DeepSeek-V3", new Pricing(0.0, 0.0));
        // Ollama 本地模型（免费）
        MODEL_PRICING.put("llama3", new Pricing(0.0, 0.0));
        MODEL_PRICING.put("qwen2.5", new Pricing(0.0, 0.0));
    }

    /**
     * 记录 Token 使用
     *
     * @param userId      用户 ID
     * @param sessionId   会话 ID（可选）
     * @param modelName   模型名称
     * @param response    LangChain4j 响应对象
     */
    @Transactional
    public void recordUsage(String userId, String sessionId, String modelName, ChatResponse response) {
        recordUsage(userId, sessionId, null, modelName, response);
    }

    /**
     * 记录 Token 使用（带 Trace ID）
     *
     * @param userId      用户 ID
     * @param sessionId   会话 ID（可选）
     * @param traceId     Trace ID（可选）
     * @param modelName   模型名称
     * @param response    LangChain4j 响应对象
     */
    @Transactional
    public void recordUsage(String userId, String sessionId, String traceId, String modelName, ChatResponse response) {
        if (response == null || response.tokenUsage() == null) {
            log.warn("响应或 Token 使用信息为空，跳过记录");
            return;
        }

        var tokenUsage = response.tokenUsage();
        int promptTokens = tokenUsage.inputTokenCount();
        int completionTokens = tokenUsage.outputTokenCount();
        int totalTokens = tokenUsage.totalTokenCount();

        double cost = calculateCost(modelName, promptTokens, completionTokens);

        TokenUsageLog logEntry = TokenUsageLog.create(
                userId,
                sessionId,
                modelName,
                promptTokens,
                completionTokens,
                cost
        );
        logEntry.setTraceId(traceId);

        tokenUsageRepository.save(logEntry);

        // 记录 Micrometer 业务指标
        businessMetricsService.recordTokenUsage(modelName, promptTokens, completionTokens);
        businessMetricsService.recordUserTokenUsage(userId, totalTokens);
        businessMetricsService.recordCost(cost);

        log.info("Token 使用记录: userId={}, model={}, prompt={}, completion={}, total={}, cost=${}",
                userId, modelName, promptTokens, completionTokens, totalTokens, cost);
    }

    /**
     * 记录流式 Token 使用（流式响应完成后）
     *
     * @param userId           用户 ID
     * @param sessionId        会话 ID
     * @param modelName        模型名称
     * @param promptTokens     输入 Token 数
     * @param completionTokens 输出 Token 数
     */
    @Transactional
    public void recordStreamingUsage(String userId, String sessionId, String modelName,
                                      int promptTokens, int completionTokens) {
        double cost = calculateCost(modelName, promptTokens, completionTokens);

        TokenUsageLog logEntry = TokenUsageLog.create(
                userId,
                sessionId,
                modelName,
                promptTokens,
                completionTokens,
                cost
        );

        tokenUsageRepository.save(logEntry);

        // 记录 Micrometer 业务指标
        businessMetricsService.recordTokenUsage(modelName, promptTokens, completionTokens);
        businessMetricsService.recordUserTokenUsage(userId, promptTokens + completionTokens);
        businessMetricsService.recordCost(cost);

        log.info("流式 Token 使用记录: userId={}, model={}, prompt={}, completion={}, cost=${}",
                userId, modelName, promptTokens, completionTokens, cost);
    }

    /**
     * 计算费用
     */
    private double calculateCost(String modelName, int promptTokens, int completionTokens) {
        Pricing pricing = MODEL_PRICING.getOrDefault(modelName, new Pricing(0.001, 0.002));

        double promptCost = (promptTokens / 1000.0) * pricing.promptPricePer1k;
        double completionCost = (completionTokens / 1000.0) * pricing.completionPricePer1k;

        return promptCost + completionCost;
    }

    /**
     * 获取用户今日使用统计
     */
    public TokenUsageSummary getTodaySummary(String userId) {
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDateTime start = today.atStartOfDay();
        java.time.LocalDateTime end = start.plusDays(1);
        return getRangeSummary(userId, start, end);
    }

    /**
     * 获取用户指定日期范围统计
     */
    public TokenUsageSummary getRangeSummary(String userId, java.time.LocalDateTime start, java.time.LocalDateTime end) {
        List<Object[]> results;
        if (userId != null) {
            results = tokenUsageRepository.getUserUsage(userId, start, end);
        } else {
            results = tokenUsageRepository.getUsageSummary(start, end);
        }

        if (results.isEmpty() || results.get(0)[0] == null) {
            return new TokenUsageSummary(0, 0, 0, 0.0);
        }

        Object[] row = results.get(0);
        return new TokenUsageSummary(
                ((Number) row[0]).longValue(),
                ((Number) row[1]) != null ? ((Number) row[1]).longValue() : 0,
                ((Number) row[2]) != null ? ((Number) row[2]).longValue() : 0,
                ((Number) row[3]) != null ? ((Number) row[3]).doubleValue() : 0.0
        );
    }

    /**
     * 获取全局统计
     */
    public TokenUsageSummary getGlobalSummary(java.time.LocalDate start, java.time.LocalDate end) {
        return getRangeSummary(null, start.atStartOfDay(), end.plusDays(1).atStartOfDay());
    }

    /**
     * 检查用户预算
     */
    public BudgetCheckResult checkBudget(String userId, double monthlyLimit) {
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        java.time.LocalDateTime monthEnd = today.plusDays(1).atStartOfDay();

        TokenUsageSummary monthSummary = getRangeSummary(userId, monthStart, monthEnd);

        boolean withinBudget = monthSummary.totalCost() < monthlyLimit;
        double remaining = monthlyLimit - monthSummary.totalCost();

        return new BudgetCheckResult(withinBudget, monthSummary.totalCost(), monthlyLimit, remaining);
    }

    /**
     * 获取用户使用趋势
     *
     * @param userId 用户 ID
     * @param days 天数
     * @return 每日使用量列表
     */
    public java.util.List<DailyUsage> getUserTrend(String userId, int days) {
        java.time.LocalDate endDate = java.time.LocalDate.now();
        java.time.LocalDate startDate = endDate.minusDays(days);

        List<TokenUsageLog> records = tokenUsageRepository.findByUserIdOrderByCreatedAtDesc(userId);

        // 按日期分组
        Map<java.time.LocalDate, java.util.List<TokenUsageLog>> grouped = new java.util.TreeMap<>();
        for (TokenUsageLog record : records) {
            if (record.getCreatedAt() != null) {
                java.time.LocalDate date = record.getCreatedAt().toLocalDate();
                if (!date.isBefore(startDate) && !date.isAfter(endDate)) {
                    grouped.computeIfAbsent(date, k -> new java.util.ArrayList<>()).add(record);
                }
            }
        }

        return grouped.entrySet().stream()
                .map(e -> new DailyUsage(
                        e.getKey(),
                        e.getValue().stream().mapToLong(TokenUsageLog::getTotalTokens).sum(),
                        e.getValue().stream().mapToDouble(r -> r.getCost().doubleValue()).sum()
                ))
                .sorted(java.util.Comparator.comparing(DailyUsage::date))
                .toList();
    }

    /**
     * Token 使用统计摘要
     */
    public record TokenUsageSummary(long totalTokens, long promptTokens, long completionTokens, double totalCost) {}

    /**
     * 预算检查结果
     */
    public record BudgetCheckResult(boolean withinBudget, double usedAmount, double budgetLimit, double remaining) {}

    /**
     * 每日使用量
     */
    public record DailyUsage(java.time.LocalDate date, long totalTokens, double totalCost) {}

    /**
     * 模型定价
     */
    private record Pricing(double promptPricePer1k, double completionPricePer1k) {}
}
