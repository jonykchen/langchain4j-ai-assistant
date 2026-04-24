package com.jonychen.agent.security;

/**
 * Agent 权限枚举
 *
 * <p>定义 Agent 系统的所有权限，用于 RBAC 权限控制。 每个权限对应一个具体操作，AgentRole 通过映射获取其拥有的权限集合。
 *
 * @author jonychen
 */
public enum AgentPermission {

    /** 执行运维 Agent */
    AGENT_OPS_EXECUTE("agent:ops:execute", "执行运维 Agent"),
    /** 读取模型状态 */
    MODEL_READ("model:read", "读取模型状态"),
    /** 修改模型配置（权重、启禁用等） */
    MODEL_WRITE("model:write", "修改模型配置"),
    /** 读取熔断器状态 */
    CIRCUIT_BREAKER_READ("circuit-breaker:read", "读取熔断器状态"),
    /** 重置熔断器 */
    CIRCUIT_BREAKER_WRITE("circuit-breaker:write", "重置熔断器"),

    /** 执行数据分析 Agent */
    AGENT_DATA_EXECUTE("agent:data:execute", "执行数据分析 Agent"),
    /** 数据库只读查询 */
    DATABASE_READ("database:read", "数据库只读查询"),
    /** 生成图表 */
    CHART_GENERATE("chart:generate", "生成图表"),

    /** 执行 Prompt 工程 Agent */
    AGENT_PROMPT_EXECUTE("agent:prompt:execute", "执行 Prompt 工程 Agent"),
    /** 读取 Prompt 模板 */
    PROMPT_READ("prompt:read", "读取 Prompt 模板"),
    /** 修改 Prompt 模板 */
    PROMPT_WRITE("prompt:write", "修改 Prompt 模板"),
    /** 执行评测 */
    EVALUATION_RUN("evaluation:run", "执行评测"),

    /** 执行测试 Agent */
    AGENT_TEST_EXECUTE("agent:test:execute", "执行测试 Agent"),
    /** 读取源代码 */
    CODE_READ("code:read", "读取源代码"),
    /** 创建测试 */
    TEST_CREATE("test:create", "创建测试"),
    /** 执行测试 */
    TEST_RUN("test:run", "执行测试"),

    /** 通用对话（无需特殊权限） */
    CHAT("chat", "通用对话");

    private final String code;
    private final String description;

    AgentPermission(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
