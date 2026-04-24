package com.jonychen.agent.core;

/**
 * 执行步骤类型枚举
 *
 * @author jonychen
 */
public enum StepType {
    THOUGHT("思考过程"),
    TOOL_CALL("工具调用"),
    TOOL_RESULT("工具结果"),
    LLM_CALL("LLM 调用"),
    AGENT_CALL("委托其他 Agent");

    private final String displayName;

    StepType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
