package com.jonychen.planning;

/**
 * 任务类型枚举
 *
 * @author jonychen
 */
public enum TaskType {
    /** 简单任务 - 直接执行 */
    SIMPLE("简单任务", "无需规划，直接执行"),

    /** 多步骤任务 - 需要顺序执行多个步骤 */
    MULTI_STEP("多步骤任务", "需要顺序执行多个步骤"),

    /** 复杂任务 - 需要规划和动态调整 */
    COMPLEX("复杂任务", "需要规划、执行和动态调整");

    private final String displayName;
    private final String description;

    TaskType(String displayName, String description) {
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
