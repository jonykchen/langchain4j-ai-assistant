package com.jonychen.tool.confirmation;

import com.jonychen.tool.ToolCategory;
import com.jonychen.tool.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 工具风险评估器
 *
 * @author jonychen
 */
@Component
public class ToolRiskEvaluator {

    /**
     * 工具风险等级映射
     */
    private static final Map<String, ToolRiskLevel> RISK_MAPPING = Map.ofEntries(
            Map.entry("execute_sql", ToolRiskLevel.HIGH),
            Map.entry("write_file", ToolRiskLevel.HIGH),
            Map.entry("delete_file", ToolRiskLevel.CRITICAL),
            Map.entry("send_email", ToolRiskLevel.HIGH),
            Map.entry("http_request", ToolRiskLevel.MEDIUM),
            Map.entry("web_search", ToolRiskLevel.LOW),
            Map.entry("get_current_time", ToolRiskLevel.LOW),
            Map.entry("get_current_date", ToolRiskLevel.LOW),
            Map.entry("calculate", ToolRiskLevel.LOW),
            Map.entry("evaluate_expression", ToolRiskLevel.LOW),
            Map.entry("format_datetime", ToolRiskLevel.LOW),
            Map.entry("convert_units", ToolRiskLevel.LOW),
            Map.entry("calculate_percentage", ToolRiskLevel.LOW)
    );

    /**
     * 高风险操作关键词
     */
    private static final List<String> HIGH_RISK_KEYWORDS = List.of(
            "delete", "drop", "truncate", "update", "remove",
            "send", "publish", "submit", "confirm", "execute"
    );

    /**
     * 评估工具风险等级
     *
     * @param toolName 工具名称
     * @param params   参数
     * @return 风险等级
     */
    public ToolRiskLevel evaluateRisk(String toolName, Map<String, Object> params) {
        // 1. 检查预设风险等级
        ToolRiskLevel baseLevel = RISK_MAPPING.getOrDefault(toolName, ToolRiskLevel.MEDIUM);

        // 2. 根据参数动态调整
        if (containsHighRiskOperation(params)) {
            baseLevel = upgradeRiskLevel(baseLevel);
        }

        // 3. 批量操作提升风险等级
        if (isBatchOperation(params)) {
            baseLevel = upgradeRiskLevel(baseLevel);
        }

        // 4. 敏感数据操作提升风险等级
        if (containsSensitiveData(params)) {
            baseLevel = upgradeRiskLevel(baseLevel);
        }

        return baseLevel;
    }

    /**
     * 评估工具风险等级（基于工具定义）
     *
     * @param tool   工具定义
     * @param params 参数
     * @return 风险等级
     */
    public ToolRiskLevel evaluateRisk(ToolDefinition tool, Map<String, Object> params) {
        // 基础风险等级
        ToolRiskLevel baseLevel = getBaseRiskLevel(tool);

        // 根据参数动态调整
        return adjustByParams(baseLevel, params);
    }

    /**
     * 获取工具基础风险等级
     */
    private ToolRiskLevel getBaseRiskLevel(ToolDefinition tool) {
        // 先检查预设映射
        if (RISK_MAPPING.containsKey(tool.name())) {
            return RISK_MAPPING.get(tool.name());
        }

        // 根据分类判断
        return switch (tool.category()) {
            case SYSTEM -> ToolRiskLevel.LOW;
            case SEARCH -> ToolRiskLevel.LOW;
            case DATABASE -> ToolRiskLevel.HIGH;
            case FILE -> ToolRiskLevel.HIGH;
            case EXTERNAL -> ToolRiskLevel.MEDIUM;
            default -> ToolRiskLevel.MEDIUM;
        };
    }

    /**
     * 根据参数调整风险等级
     */
    private ToolRiskLevel adjustByParams(ToolRiskLevel baseLevel, Map<String, Object> params) {
        if (containsHighRiskOperation(params)) {
            baseLevel = upgradeRiskLevel(baseLevel);
        }

        if (isBatchOperation(params)) {
            baseLevel = upgradeRiskLevel(baseLevel);
        }

        if (containsSensitiveData(params)) {
            baseLevel = upgradeRiskLevel(baseLevel);
        }

        return baseLevel;
    }

    /**
     * 检查参数是否包含高风险操作
     */
    private boolean containsHighRiskOperation(Map<String, Object> params) {
        return params.values().stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(String::toLowerCase)
                .anyMatch(value -> HIGH_RISK_KEYWORDS.stream()
                        .anyMatch(keyword -> value.contains(keyword)));
    }

    /**
     * 检查是否是批量操作
     */
    private boolean isBatchOperation(Map<String, Object> params) {
        // 检查数量参数
        Object count = params.get("count");
        if (count instanceof Number && ((Number) count).intValue() > 10) {
            return true;
        }

        // 检查列表参数
        Object items = params.get("items");
        if (items instanceof List && ((List<?>) items).size() > 10) {
            return true;
        }

        // 检查批量标志
        Object batch = params.get("batch");
        return batch instanceof Boolean && (Boolean) batch;
    }

    /**
     * 检查参数是否包含敏感数据
     */
    private boolean containsSensitiveData(Map<String, Object> params) {
        // 检查参数名是否包含敏感关键词
        return params.keySet().stream()
                .map(String::toLowerCase)
                .anyMatch(key -> key.contains("password") ||
                        key.contains("token") ||
                        key.contains("secret") ||
                        key.contains("credit") ||
                        key.contains("ssn"));
    }

    /**
     * 提升风险等级
     */
    private ToolRiskLevel upgradeRiskLevel(ToolRiskLevel level) {
        return switch (level) {
            case LOW -> ToolRiskLevel.MEDIUM;
            case MEDIUM -> ToolRiskLevel.HIGH;
            case HIGH -> ToolRiskLevel.CRITICAL;
            case CRITICAL -> ToolRiskLevel.CRITICAL;
        };
    }
}