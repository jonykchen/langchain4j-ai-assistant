package com.jonychen.tool.confirmation;

/**
 * 工具风险等级
 *
 * @author jonychen
 */
public enum ToolRiskLevel {
    /** 低风险 - 无需确认 */
    LOW("低风险", false),

    /** 中风险 - 可选确认 */
    MEDIUM("中风险", false),

    /** 高风险 - 必须确认 */
    HIGH("高风险", true),

    /** 关键操作 - 必须确认 + 多因素验证 */
    CRITICAL("关键操作", true);

    private final String displayName;
    private final boolean requiresConfirmation;

    ToolRiskLevel(String displayName, boolean requiresConfirmation) {
        this.displayName = displayName;
        this.requiresConfirmation = requiresConfirmation;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean requiresConfirmation() {
        return requiresConfirmation;
    }

    /** 从字符串解析风险等级 */
    public static ToolRiskLevel fromString(String value) {
        if (value == null) {
            return MEDIUM;
        }
        return switch (value.toUpperCase()) {
            case "LOW" -> LOW;
            case "MEDIUM" -> MEDIUM;
            case "HIGH" -> HIGH;
            case "CRITICAL" -> CRITICAL;
            default -> MEDIUM;
        };
    }
}
