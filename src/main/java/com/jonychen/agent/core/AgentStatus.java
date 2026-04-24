package com.jonychen.agent.core;

/**
 * Agent 执行状态枚举
 *
 * @author jonychen
 */
public enum AgentStatus {
    RUNNING("执行中"),
    SUCCESS("成功"),
    FAILED("失败"),
    CANCELLED("已取消"),
    TIMEOUT("超时"),
    PENDING_CONFIRMATION("等待确认");

    private final String displayName;

    AgentStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
