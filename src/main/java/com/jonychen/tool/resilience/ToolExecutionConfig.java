package com.jonychen.tool.resilience;

import java.time.Duration;

/**
 * 工具执行配置
 *
 * @param timeout               超时时间
 * @param maxRetries            最大重试次数
 * @param retryDelay            重试间隔
 * @param circuitBreakerThreshold 熔断阈值（失败率）
 * @param circuitBreakerWait    熔断等待时间
 * @param fallbackResult        降级结果
 * @author jonychen
 */
public record ToolExecutionConfig(
        Duration timeout,
        int maxRetries,
        Duration retryDelay,
        double circuitBreakerThreshold,
        Duration circuitBreakerWait,
        String fallbackResult
) {
    /**
     * 低风险工具配置（宽松限制）
     */
    public static ToolExecutionConfig lowRisk() {
        return new ToolExecutionConfig(
                Duration.ofSeconds(30), 3, Duration.ofSeconds(1),
                0.5, Duration.ofSeconds(30), null
        );
    }

    /**
     * 高风险工具配置（严格限制）
     */
    public static ToolExecutionConfig highRisk() {
        return new ToolExecutionConfig(
                Duration.ofSeconds(10), 1, Duration.ofSeconds(2),
                0.3, Duration.ofSeconds(60), "{\"error\": \"服务暂时不可用\"}"
        );
    }

    /**
     * 只读工具配置（中等限制）
     */
    public static ToolExecutionConfig readOnly() {
        return new ToolExecutionConfig(
                Duration.ofSeconds(60), 2, Duration.ofSeconds(1),
                0.5, Duration.ofSeconds(30), null
        );
    }

    /**
     * 默认配置
     */
    public static ToolExecutionConfig defaultConfig() {
        return lowRisk();
    }

    /**
     * 构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Duration timeout = Duration.ofSeconds(30);
        private int maxRetries = 2;
        private Duration retryDelay = Duration.ofSeconds(1);
        private double circuitBreakerThreshold = 0.5;
        private Duration circuitBreakerWait = Duration.ofSeconds(30);
        private String fallbackResult = null;

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder retryDelay(Duration retryDelay) {
            this.retryDelay = retryDelay;
            return this;
        }

        public Builder circuitBreakerThreshold(double threshold) {
            this.circuitBreakerThreshold = threshold;
            return this;
        }

        public Builder circuitBreakerWait(Duration wait) {
            this.circuitBreakerWait = wait;
            return this;
        }

        public Builder fallbackResult(String fallback) {
            this.fallbackResult = fallback;
            return this;
        }

        public ToolExecutionConfig build() {
            return new ToolExecutionConfig(timeout, maxRetries, retryDelay,
                    circuitBreakerThreshold, circuitBreakerWait, fallbackResult);
        }
    }
}