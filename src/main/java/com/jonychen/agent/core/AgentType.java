package com.jonychen.agent.core;

/**
 * Agent 类型枚举
 *
 * @author jonychen
 */
public enum AgentType {
    ROUTER("router", "路由 Agent", "分析意图，选择合适的 Agent"),
    OPS("ops", "运维助手", "模型诊断、故障处置、健康监控"),
    DATA("data", "数据分析助手", "自然语言查库、图表生成、数据导出"),
    PROMPT("prompt", "Prompt 工程", "Prompt 优化、评测、A/B 测试"),
    TEST("test", "测试生成助手", "单元/集成/E2E 测试生成与执行"),
    CHAT("chat", "通用对话", "普通问答、闲聊");

    private final String code;
    private final String displayName;
    private final String description;

    AgentType(String code, String displayName, String description) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
