package com.jonychen.agent.core;

/**
 * Token 使用量统计
 *
 * @param promptTokens 输入 Token 数
 * @param completionTokens 输出 Token 数
 * @param totalTokens 总 Token 数
 * @author jonychen
 */
public record TokenUsage(long promptTokens, long completionTokens, long totalTokens) {
    public static TokenUsage of(long promptTokens, long completionTokens) {
        return new TokenUsage(promptTokens, completionTokens, promptTokens + completionTokens);
    }

    public static TokenUsage empty() {
        return new TokenUsage(0, 0, 0);
    }
}
