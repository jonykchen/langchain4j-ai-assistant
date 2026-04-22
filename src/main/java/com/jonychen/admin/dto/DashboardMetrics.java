package com.jonychen.admin.dto;

import java.util.List;

/**
 * 仪表盘统计数据
 *
 * @param totalUsers 总用户数
 * @param activeUsers 今日活跃用户
 * @param todayTokens 今日 Token 使用量
 * @param todayCost 今日费用
 * @param todayRequests 今日请求数
 * @param modelHealth 模型健康状态
 * @param budget 预算使用情况
 * @author jonychen
 */
public record DashboardMetrics(
        long totalUsers,
        long activeUsers,
        long todayTokens,
        double todayCost,
        long todayRequests,
        List<ModelHealthInfo> modelHealth,
        BudgetInfo budget) {
    /** 模型健康信息 */
    public record ModelHealthInfo(
            String name,
            String status,
            String circuitBreaker,
            long avgLatency,
            double successRate) {}

    /** 预算信息 */
    public record BudgetInfo(
            double dailyUsed,
            double dailyTotal,
            double dailyPercent,
            double monthlyUsed,
            double monthlyTotal,
            double monthlyPercent) {}
}
