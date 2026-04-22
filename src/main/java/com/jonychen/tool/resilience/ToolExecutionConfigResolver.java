package com.jonychen.tool.resilience;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.jonychen.tool.ToolDefinition;

/**
 * 工具执行配置解析器
 *
 * <p>根据工具定义解析合适的执行配置
 *
 * @author jonychen
 */
@Component
public class ToolExecutionConfigResolver {

    /**
     * 根据工具定义解析执行配置
     *
     * @param tool 工具定义
     * @return 执行配置
     */
    public ToolExecutionConfig resolve(ToolDefinition tool) {
        // 优先使用工具自定义配置
        if (tool.timeout() != null && tool.maxRetries() > 0) {
            return ToolExecutionConfig.builder()
                    .timeout(tool.timeout())
                    .maxRetries(tool.maxRetries())
                    .retryDelay(Duration.ofSeconds(1))
                    .circuitBreakerThreshold(getCircuitBreakerThreshold(tool))
                    .circuitBreakerWait(Duration.ofSeconds(30))
                    .fallbackResult(getFallbackResult(tool))
                    .build();
        }

        // 根据分类选择预置配置
        return switch (tool.category()) {
            case SYSTEM -> ToolExecutionConfig.lowRisk();
            case DATABASE -> ToolExecutionConfig.highRisk();
            case FILE -> ToolExecutionConfig.highRisk();
            case EXTERNAL -> ToolExecutionConfig.readOnly();
            case SEARCH -> ToolExecutionConfig.readOnly();
            default -> ToolExecutionConfig.defaultConfig();
        };
    }

    /** 根据工具分类获取熔断阈值 */
    private double getCircuitBreakerThreshold(ToolDefinition tool) {
        return switch (tool.category()) {
            case DATABASE -> 0.3; // 数据库操作更敏感
            case FILE -> 0.3; // 文件操作更敏感
            case EXTERNAL -> 0.4; // 外部服务
            default -> 0.5; // 其他
        };
    }

    /** 根据工具分类获取降级结果 */
    private String getFallbackResult(ToolDefinition tool) {
        return switch (tool.category()) {
            case DATABASE -> "{\"error\": \"数据库服务暂时不可用\"}";
            case FILE -> "{\"error\": \"文件服务暂时不可用\"}";
            case EXTERNAL -> "{\"error\": \"外部服务暂时不可用\"}";
            default -> null;
        };
    }

    /**
     * 根据风险等级获取配置
     *
     * @param riskLevel 风险等级
     * @return 执行配置
     */
    public ToolExecutionConfig resolveByRiskLevel(String riskLevel) {
        return switch (riskLevel.toLowerCase()) {
            case "low" -> ToolExecutionConfig.lowRisk();
            case "medium" -> ToolExecutionConfig.readOnly();
            case "high", "critical" -> ToolExecutionConfig.highRisk();
            default -> ToolExecutionConfig.defaultConfig();
        };
    }
}
