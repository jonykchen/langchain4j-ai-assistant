package com.jonychen.tool;

/**
 * 工具统计信息
 *
 * <p>记录单个工具的调用统计数据，包括调用次数、成功率、平均耗时等。
 *
 * @param toolName 工具名称
 * @param totalCalls 总调用次数
 * @param successCount 成功次数
 * @param failureCount 失败次数
 * @param avgExecutionTimeMs 平均执行耗时（毫秒）
 * @param lastCallTime 最后调用时间戳（毫秒）
 * @param lastError 最后一次错误信息（可选）
 * @author jonychen
 */
public record ToolStatistics(
        String toolName,
        long totalCalls,
        long successCount,
        long failureCount,
        double avgExecutionTimeMs,
        long lastCallTime,
        String lastError) {

    /**
     * 计算成功率
     *
     * @return 成功率（0-100）
     */
    public double successRate() {
        if (totalCalls == 0) {
            return 0.0;
        }
        return (successCount * 100.0) / totalCalls;
    }

    /**
     * 创建空统计
     *
     * @param toolName 工具名称
     * @return 空统计记录
     */
    public static ToolStatistics empty(String toolName) {
        return new ToolStatistics(toolName, 0, 0, 0, 0.0, 0, null);
    }

    /**
     * 创建构建器
     *
     * @return 构建器实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /** 工具统计构建器 */
    public static class Builder {
        private String toolName;
        private long totalCalls;
        private long successCount;
        private long failureCount;
        private double avgExecutionTimeMs;
        private long lastCallTime;
        private String lastError;

        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        public Builder totalCalls(long totalCalls) {
            this.totalCalls = totalCalls;
            return this;
        }

        public Builder successCount(long successCount) {
            this.successCount = successCount;
            return this;
        }

        public Builder failureCount(long failureCount) {
            this.failureCount = failureCount;
            return this;
        }

        public Builder avgExecutionTimeMs(double avgExecutionTimeMs) {
            this.avgExecutionTimeMs = avgExecutionTimeMs;
            return this;
        }

        public Builder lastCallTime(long lastCallTime) {
            this.lastCallTime = lastCallTime;
            return this;
        }

        public Builder lastError(String lastError) {
            this.lastError = lastError;
            return this;
        }

        public ToolStatistics build() {
            return new ToolStatistics(
                    toolName,
                    totalCalls,
                    successCount,
                    failureCount,
                    avgExecutionTimeMs,
                    lastCallTime,
                    lastError);
        }
    }
}
