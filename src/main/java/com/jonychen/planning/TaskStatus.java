package com.jonychen.planning;

/**
 * 任务状态枚举
 *
 * @author jonychen
 */
public enum TaskStatus {
    /**
     * 待执行
     */
    PENDING("待执行"),

    /**
     * 规划中
     */
    PLANNING("规划中"),

    /**
     * 执行中
     */
    EXECUTING("执行中"),

    /**
     * 已完成
     */
    COMPLETED("已完成"),

    /**
     * 失败
     */
    FAILED("失败"),

    /**
     * 已取消
     */
    CANCELLED("已取消");

    private final String displayName;

    TaskStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }

    public boolean isActive() {
        return this == PLANNING || this == EXECUTING;
    }
}