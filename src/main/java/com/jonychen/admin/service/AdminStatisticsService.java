package com.jonychen.admin.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.jonychen.admin.dto.CostStatistics;
import com.jonychen.admin.dto.DashboardMetrics;
import com.jonychen.admin.repository.TokenUsageRepository;
import com.jonychen.auth.UserRepository;
import com.jonychen.config.ModelProperties;
import com.jonychen.rag.VectorStore;
import com.jonychen.tool.resilience.ResilientToolExecutor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 管理员统计服务
 *
 * @author jonychen
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminStatisticsService {

    private final UserRepository userRepository;
    private final TokenUsageRepository tokenUsageRepository;
    private final VectorStore vectorStore;
    private final ResilientToolExecutor resilientToolExecutor;
    private final ModelProperties modelProperties;

    /** 获取仪表盘统计数据 */
    public DashboardMetrics getDashboardMetrics() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        // 用户统计
        long totalUsers = userRepository.count();
        long activeUsers = countActiveUsers(todayStart, now);

        // Token 使用统计
        TokenUsageSummary todayUsage = getTokenUsageSummary(todayStart, now);

        // 模型健康状态
        List<DashboardMetrics.ModelHealthInfo> modelHealth = getModelHealthStatus();

        // 预算信息
        DashboardMetrics.BudgetInfo budget = getBudgetInfo(todayUsage.cost());

        return new DashboardMetrics(
                totalUsers,
                activeUsers,
                todayUsage.totalTokens(),
                todayUsage.cost(),
                todayUsage.requestCount(),
                modelHealth,
                budget);
    }

    /** 获取使用趋势数据 */
    public CostStatistics.TrendData getUsageTrend(int days) {
        List<String> dates = new ArrayList<>();
        List<Long> tokens = new ArrayList<>();
        List<Double> costs = new ArrayList<>();
        List<Long> requests = new ArrayList<>();

        LocalDate today = LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.plusDays(1).atStartOfDay();

            TokenUsageSummary summary = getTokenUsageSummary(start, end);

            dates.add(date.toString());
            tokens.add(summary.totalTokens());
            costs.add(summary.cost());
            requests.add(summary.requestCount());
        }

        return new CostStatistics.TrendData(dates, tokens, costs, requests);
    }

    /** 获取模型分布数据 */
    public List<CostStatistics.ModelDistribution> getModelDistribution() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        List<Object[]> results = tokenUsageRepository.getModelDistribution(todayStart, now);
        List<CostStatistics.ModelDistribution> distributions = new ArrayList<>();

        for (Object[] row : results) {
            distributions.add(
                    new CostStatistics.ModelDistribution(
                            (String) row[0], ((Number) row[1]).longValue()));
        }

        return distributions;
    }

    /** 获取用户消费排行 */
    public List<CostStatistics.UserCostRanking> getTopUsers(int limit) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        List<Object[]> results = tokenUsageRepository.getTopUsersByCost(todayStart, now, limit);
        List<CostStatistics.UserCostRanking> rankings = new ArrayList<>();

        for (Object[] row : results) {
            rankings.add(
                    new CostStatistics.UserCostRanking(
                            (String) row[0],
                            (String) row[1],
                            ((Number) row[2]).longValue(),
                            ((Number) row[3]).doubleValue()));
        }

        return rankings;
    }

    /** 获取模型成本统计 */
    public List<CostStatistics> getModelCostStatistics() {
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        List<Object[]> results = tokenUsageRepository.getModelCostStatistics(monthStart, now);

        // 计算总费用
        double totalCost = results.stream().mapToDouble(r -> ((Number) r[4]).doubleValue()).sum();

        List<CostStatistics> statistics = new ArrayList<>();
        for (Object[] row : results) {
            String modelName = (String) row[0];
            long totalTokens = ((Number) row[1]).longValue();
            long promptTokens = ((Number) row[2]).longValue();
            long completionTokens = ((Number) row[3]).longValue();
            double modelCost = ((Number) row[4]).doubleValue();
            long requestCount = ((Number) row[5]).longValue();

            double avgTokens = requestCount > 0 ? (double) totalTokens / requestCount : 0;
            double costPercent = totalCost > 0 ? (modelCost / totalCost) * 100 : 0;

            statistics.add(
                    new CostStatistics(
                            modelName,
                            totalTokens,
                            promptTokens,
                            completionTokens,
                            modelCost,
                            requestCount,
                            avgTokens,
                            costPercent));
        }

        return statistics;
    }

    /** 获取系统资源统计 */
    public SystemResourceInfo getSystemResources() {
        Runtime runtime = Runtime.getRuntime();

        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        return new SystemResourceInfo(
                usedMemory,
                maxMemory,
                (double) usedMemory / maxMemory * 100,
                vectorStore.count(),
                Thread.activeCount());
    }

    // ========== 私有方法 ==========

    private long countActiveUsers(LocalDateTime start, LocalDateTime end) {
        return tokenUsageRepository.countDistinctUsersByCreatedAtBetween(start, end);
    }

    private TokenUsageSummary getTokenUsageSummary(LocalDateTime start, LocalDateTime end) {
        List<Object[]> result = tokenUsageRepository.getUsageSummary(start, end);

        if (result.isEmpty() || result.get(0)[0] == null) {
            return new TokenUsageSummary(0, 0, 0, 0, 0.0);
        }

        Object[] row = result.get(0);
        return new TokenUsageSummary(
                ((Number) row[0]).longValue(), // totalTokens
                ((Number) row[1]).longValue(), // promptTokens
                ((Number) row[2]).longValue(), // completionTokens
                ((Number) row[3]).longValue(), // requestCount
                ((Number) row[4]).doubleValue() // cost
                );
    }

    private List<DashboardMetrics.ModelHealthInfo> getModelHealthStatus() {
        List<DashboardMetrics.ModelHealthInfo> healthList = new ArrayList<>();

        for (var provider : modelProperties.getEnabledProviders()) {
            if (!provider.enabled()) {
                continue;
            }

            var status =
                    resilientToolExecutor.getCircuitBreakerStatus(provider.name().toLowerCase());

            healthList.add(
                    new DashboardMetrics.ModelHealthInfo(
                            provider.name(),
                            status.state().equals("CLOSED") || status.state().equals("HALF_OPEN")
                                    ? "UP"
                                    : "DOWN",
                            status.state(),
                            (long) (Math.random() * 500 + 100), // 模拟延迟
                            status.state().equals("CLOSED") ? 95.0 : 50.0));
        }

        return healthList;
    }

    private DashboardMetrics.BudgetInfo getBudgetInfo(double todayCost) {
        // 默认预算配置（可从配置中心读取）
        double dailyTotal = 100.0;
        double monthlyTotal = 2000.0;

        // 计算月度使用
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime now = LocalDateTime.now();
        TokenUsageSummary monthUsage = getTokenUsageSummary(monthStart, now);

        double dailyPercent = (todayCost / dailyTotal) * 100;
        double monthlyPercent = (monthUsage.cost() / monthlyTotal) * 100;

        return new DashboardMetrics.BudgetInfo(
                todayCost,
                dailyTotal,
                Math.min(dailyPercent, 100),
                monthUsage.cost(),
                monthlyTotal,
                Math.min(monthlyPercent, 100));
    }

    /** Token 使用汇总 */
    private record TokenUsageSummary(
            long totalTokens,
            long promptTokens,
            long completionTokens,
            long requestCount,
            double cost) {}

    /** 系统资源信息 */
    public record SystemResourceInfo(
            long usedMemory,
            long maxMemory,
            double memoryUsagePercent,
            long documentCount,
            int activeThreads) {}
}
