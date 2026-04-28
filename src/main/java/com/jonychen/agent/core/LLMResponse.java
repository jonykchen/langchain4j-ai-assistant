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
 * @param promptTokens Prompt Token 使用量
 * @param completionTokens Completion Token 使用量
 * @param durationMs 执行时间（毫秒）
 * @param rawPrompt 原始 Prompt（用于追踪）
 * @param rawResponse 原始响应（用于追踪）
 * @author jonychen
 */
public record LLMResponse(
        String thought,
        ToolCallRequest toolCall,
        String output,
        Long promptTokens,
        Long completionTokens,
        Long durationMs,
        String rawPrompt,
        String rawResponse) {

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
     * 获取总 Token 使用量
     *
     * @return 总 Token 数
     */
    public long totalTokens() {
        return (promptTokens != null ? promptTokens : 0)
                + (completionTokens != null ? completionTokens : 0);
    }

    /**
     * 创建工具调用响应
     *
     * @param thought 思考过程
     * @param toolCall 工具调用请求
     * @return LLM 响应
     */
    public static LLMResponse toolCall(String thought, ToolCallRequest toolCall) {
        return new LLMResponse(thought, toolCall, null, null, null, null, null, null);
    }

    /**
     * 创建工具调用响应（带 Token 信息）
     *
     * @param thought 思考过程
     * @param toolCall 工具调用请求
     * @param promptTokens Prompt Token 数
     * @param completionTokens Completion Token 数
     * @param durationMs 执行时间
     * @param rawPrompt 原始 Prompt
     * @param rawResponse 原始响应
     * @return LLM 响应
     */
    public static LLMResponse toolCall(
            String thought,
            ToolCallRequest toolCall,
            Long promptTokens,
            Long completionTokens,
            Long durationMs,
            String rawPrompt,
            String rawResponse) {
        return new LLMResponse(
                thought,
                toolCall,
                null,
                promptTokens,
                completionTokens,
                durationMs,
                rawPrompt,
                rawResponse);
    }

    /**
     * 创建最终输出响应
     *
     * @param thought 思考过程
     * @param output 最终输出
     * @return LLM 响应
     */
    public static LLMResponse output(String thought, String output) {
        return new LLMResponse(thought, null, output, null, null, null, null, null);
    }

    /**
     * 创建最终输出响应（带 Token 信息）
     *
     * @param thought 思考过程
     * @param output 最终输出
     * @param promptTokens Prompt Token 数
     * @param completionTokens Completion Token 数
     * @param durationMs 执行时间
     * @param rawPrompt 原始 Prompt
     * @param rawResponse 原始响应
     * @return LLM 响应
     */
    public static LLMResponse output(
            String thought,
            String output,
            Long promptTokens,
            Long completionTokens,
            Long durationMs,
            String rawPrompt,
            String rawResponse) {
        return new LLMResponse(
                thought,
                null,
                output,
                promptTokens,
                completionTokens,
                durationMs,
                rawPrompt,
                rawResponse);
    }

    /** 简化构造（兼容旧代码） */
    public LLMResponse(String thought, ToolCallRequest toolCall, String output) {
        this(thought, toolCall, output, null, null, null, null, null);
    }
}
