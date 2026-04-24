package com.jonychen.tool;

/**
 * 工具风险等级
 *
 * <p>LOW - 只读操作，无副作用 MEDIUM - 有副作用，可恢复 HIGH - 不可逆操作，需谨慎 CRITICAL - 危险操作，必须 ADMIN 确认（无任何角色豁免）
 */
public enum RiskLevel {
    LOW("低风险", "只读操作，无副作用"),
    MEDIUM("中风险", "有副作用，可恢复"),
    HIGH("高风险", "不可逆操作，需谨慎"),
    CRITICAL("极高风险", "危险操作，需要 ADMIN 确认");

    private final String displayName;
    private final String description;

    RiskLevel(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
