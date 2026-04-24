package com.jonychen.agent.core;

/**
 * LLM 响应封装
 *
 * <p>封装 LLM 返回的结构化响应，包括思考过程、工具调用请求和最终输出。
 *
 * <p>三种情况：
 *
 * <ul>
 *   <li>有工具调用：toolCall 不为空，output 为 null
 *   <li>有思考+工具调用：thought 和 toolCall 不为空
 *   <li>直接回答：output 不为空，thought 可能为思考过程
 * </ul>
 *
 * @param thought 思考过程（模型原生支持 tool calling 时通常包含在 text 中）
 * @param toolCall 工具调用请求（可能为 null）
 * @param output 最终输出（无工具调用时有值）
 * @author jonychen
 */
public record LLMResponse(String thought, ToolCallRequest toolCall, String output) {

    /**
     * 判断是否有工具调用
     *
     * @return 是否有工具调用
     */
    public boolean hasToolCall() {
        return toolCall != null;
    }

    /**
     * 判断是否有思考过程
     *
     * @return 是否有思考过程
     */
    public boolean hasThought() {
        return thought != null && !thought.isBlank();
    }

    /**
     * 创建工具调用响应
     *
     * @param thought 思考过程
     * @param toolCall 工具调用请求
     * @return LLM 响应
     */
    public static LLMResponse toolCall(String thought, ToolCallRequest toolCall) {
        return new LLMResponse(thought, toolCall, null);
    }

    /**
     * 创建最终输出响应
     *
     * @param thought 思考过程
     * @param output 最终输出
     * @return LLM 响应
     */
    public static LLMResponse output(String thought, String output) {
        return new LLMResponse(thought, null, output);
    }
}
