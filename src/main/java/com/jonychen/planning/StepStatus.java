package com.jonychen.planning;

/**
 * 步骤状态枚举
 *
 * @author jonychen
 */
public enum StepStatus {
    /**
     * 待执行
     */
    PENDING("待执行"),

    /**
     * 运行中
     */
    RUNNING("运行中"),

    /**
     * 已完成
     */
    COMPLETED("已完成"),

    /**
     * 失败
     */
    FAILED("失败"),

    /**
     * 跳过
     */
    SKIPPED("跳过");

    private final String displayName;

    StepStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == SKIPPED;
    }
}