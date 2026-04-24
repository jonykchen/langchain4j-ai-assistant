package com.jonychen.agent.core;

import java.time.Duration;

/**
 * Agent 执行选项配置
 *
 * @param maxIterations 最大迭代次数
 * @param timeout 超时时间
 * @param requireConfirmation 敏感操作是否需要确认
 * @param debugMode 调试模式（输出更多信息）
 * @author jonychen
 */
public record AgentRequestOptions(
        int maxIterations, Duration timeout, boolean requireConfirmation, boolean debugMode) {

    /** 默认执行选项 */
    public static AgentRequestOptions defaults() {
        return new AgentRequestOptions(10, Duration.ofMinutes(5), true, false);
    }

    /** 调试模式执行选项 */
    public static AgentRequestOptions debug() {
        return new AgentRequestOptions(10, Duration.ofMinutes(10), true, true);
    }

    /** 无确认执行选项（仅限可信场景） */
    public static AgentRequestOptions noConfirmation() {
        return new AgentRequestOptions(10, Duration.ofMinutes(5), false, false);
    }
}
