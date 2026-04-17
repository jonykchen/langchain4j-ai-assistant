package com.jonychen.admin.dto;

import java.util.List;

/**
 * 成本统计
 *
 * @param modelName        模型名称
 * @param totalTokens      总 Token
 * @param promptTokens     提示 Token
 * @param completionTokens 完成 Token
 * @param totalCost        总费用
 * @param requestCount     请求数
 * @param avgTokensPerRequest 平均 Token/请求
 * @param costPercent      费用占比
 * @author jonychen
 */
public record CostStatistics(
        String modelName,
        long totalTokens,
        long promptTokens,
        long completionTokens,
        double totalCost,
        long requestCount,
        double avgTokensPerRequest,
        double costPercent
) {
    /**
     * 趋势数据
     */
    public record TrendData(
            List<String> dates,
            List<Long> tokens,
            List<Double> costs,
            List<Long> requests
    ) {}

    /**
     * 用户消费排行
     */
    public record UserCostRanking(
            String userId,
            String username,
            long tokens,
            double cost
    ) {}

    /**
     * 模型分布
     */
    public record ModelDistribution(
            String name,
            long value
    ) {}
}