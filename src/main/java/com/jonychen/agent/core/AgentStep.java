package com.jonychen.agent.core;

import java.util.Map;

/**
 * Agent 执行步骤
 *
 * <p>记录 Agent 执行过程中的单个步骤信息，包括步骤类型、内容、工具调用详情、执行结果等。 用于构建执行追踪、调试分析和结果展示。
 *
 * @param stepIndex 步骤序号（从 0 开始）
 * @param type 步骤类型（THOUGHT、TOOL_CALL、TOOL_RESULT、LLM_CALL、AGENT_CALL）
 * @param content 步骤内容（如思考过程、LLM 响应文本等）
 * @param toolName 工具名称（仅 TOOL_CALL 和 TOOL_RESULT 类型有值）
 * @param toolInput 工具输入参数（仅 TOOL_CALL 类型有值）
 * @param toolOutput 工具输出结果（仅 TOOL_RESULT 类型有值）
 * @param success 是否成功执行
 * @param error 错误信息（执行失败时有值）
 * @param durationMs 执行耗时（毫秒）
 * @author jonychen
 */
public record AgentStep(
        int stepIndex,
        StepType type,
        String content,
        String toolName,
        Map<String, Object> toolInput,
        Object toolOutput,
        boolean success,
        String error,
        long durationMs) {

    /**
     * 创建思考步骤
     *
     * @param stepIndex 步骤序号
     * @param content 思考内容
     * @param durationMs 耗时
     * @return 思考步骤
     */
    public static AgentStep thought(int stepIndex, String content, long durationMs) {
        return new AgentStep(
                stepIndex, StepType.THOUGHT, content, null, null, null, true, null, durationMs);
    }

    /**
     * 创建工具调用步骤
     *
     * @param stepIndex 步骤序号
     * @param toolName 工具名称
     * @param toolInput 工具输入参数
     * @param durationMs 耗时
     * @return 工具调用步骤
     */
    public static AgentStep toolCall(
            int stepIndex, String toolName, Map<String, Object> toolInput, long durationMs) {
        return new AgentStep(
                stepIndex,
                StepType.TOOL_CALL,
                null,
                toolName,
                toolInput,
                null,
                true,
                null,
                durationMs);
    }

    /**
     * 创建工具结果步骤
     *
     * @param stepIndex 步骤序号
     * @param toolName 工具名称
     * @param toolOutput 工具输出
     * @param success 是否成功
     * @param error 错误信息
     * @param durationMs 耗时
     * @return 工具结果步骤
     */
    public static AgentStep toolResult(
            int stepIndex,
            String toolName,
            Object toolOutput,
            boolean success,
            String error,
            long durationMs) {
        return new AgentStep(
                stepIndex,
                StepType.TOOL_RESULT,
                null,
                toolName,
                null,
                toolOutput,
                success,
                error,
                durationMs);
    }

    /**
     * 创建 LLM 调用步骤
     *
     * @param stepIndex 步骤序号
     * @param content LLM 响应内容
     * @param success 是否成功
     * @param durationMs 耗时
     * @return LLM 调用步骤
     */
    public static AgentStep llmCall(
            int stepIndex, String content, boolean success, long durationMs) {
        return new AgentStep(
                stepIndex, StepType.LLM_CALL, content, null, null, null, success, null, durationMs);
    }

    /**
     * 创建 Agent 委托步骤
     *
     * @param stepIndex 步骤序号
     * @param targetAgent 目标 Agent 名称
     * @param input 委托输入
     * @param durationMs 耗时
     * @return Agent 委托步骤
     */
    public static AgentStep agentCall(
            int stepIndex, String targetAgent, String input, long durationMs) {
        return new AgentStep(
                stepIndex,
                StepType.AGENT_CALL,
                input,
                null,
                null,
                targetAgent,
                true,
                null,
                durationMs);
    }
}
