package com.jonychen.agent.core;

import java.util.Collections;
import java.util.List;

/**
 * Agent 执行结果
 *
 * <p>封装 Agent 执行完成后的结果，包括执行状态、输出内容、执行步骤、Token 使用量和耗时等。 用于同步执行模式返回结果，或流式执行模式的最终汇总。
 *
 * @param traceId 追踪 ID，用于关联执行过程中的所有事件
 * @param status 执行状态（SUCCESS、FAILED、CANCELLED、TIMEOUT）
 * @param output 最终输出内容
 * @param steps 执行步骤列表
 * @param tokenUsage Token 使用量统计
 * @param durationMs 执行总耗时（毫秒）
 * @param errorMessage 错误信息（失败时有值）
 * @author jonychen
 */
public record AgentResult(
        String traceId,
        AgentStatus status,
        String output,
        List<AgentStep> steps,
        TokenUsage tokenUsage,
        long durationMs,
        String errorMessage) {

    /**
     * 创建成功结果
     *
     * @param traceId 追踪 ID
     * @param output 输出内容
     * @param steps 执行步骤
     * @return 成功结果
     */
    public static AgentResult success(String traceId, String output, List<AgentStep> steps) {
        return new AgentResult(
                traceId, AgentStatus.SUCCESS, output, steps, TokenUsage.empty(), 0, null);
    }

    /**
     * 创建成功结果（带详细信息）
     *
     * @param traceId 追踪 ID
     * @param output 输出内容
     * @param steps 执行步骤
     * @param tokenUsage Token 使用量
     * @param durationMs 执行耗时
     * @return 成功结果
     */
    public static AgentResult success(
            String traceId,
            String output,
            List<AgentStep> steps,
            TokenUsage tokenUsage,
            long durationMs) {
        return new AgentResult(
                traceId, AgentStatus.SUCCESS, output, steps, tokenUsage, durationMs, null);
    }

    /**
     * 创建失败结果
     *
     * @param traceId 追踪 ID
     * @param errorMessage 错误信息
     * @return 失败结果
     */
    public static AgentResult failure(String traceId, String errorMessage) {
        return new AgentResult(
                traceId,
                AgentStatus.FAILED,
                null,
                Collections.emptyList(),
                TokenUsage.empty(),
                0,
                errorMessage);
    }

    /**
     * 创建失败结果（带步骤）
     *
     * @param traceId 追踪 ID
     * @param errorMessage 错误信息
     * @param steps 已执行的步骤
     * @param durationMs 执行耗时
     * @return 失败结果
     */
    public static AgentResult failure(
            String traceId, String errorMessage, List<AgentStep> steps, long durationMs) {
        return new AgentResult(
                traceId,
                AgentStatus.FAILED,
                null,
                steps,
                TokenUsage.empty(),
                durationMs,
                errorMessage);
    }

    /**
     * 创建取消结果
     *
     * @param traceId 追踪 ID
     * @param steps 已执行的步骤
     * @param durationMs 执行耗时
     * @return 取消结果
     */
    public static AgentResult cancelled(String traceId, List<AgentStep> steps, long durationMs) {
        return new AgentResult(
                traceId,
                AgentStatus.CANCELLED,
                null,
                steps,
                TokenUsage.empty(),
                durationMs,
                "执行已取消");
    }

    /**
     * 创建超时结果
     *
     * @param traceId 追踪 ID
     * @param steps 已执行的步骤
     * @param durationMs 执行耗时
     * @return 超时结果
     */
    public static AgentResult timeout(String traceId, List<AgentStep> steps, long durationMs) {
        return new AgentResult(
                traceId, AgentStatus.TIMEOUT, null, steps, TokenUsage.empty(), durationMs, "执行超时");
    }

    /**
     * 判断是否执行成功
     *
     * @return 是否成功
     */
    public boolean isSuccess() {
        return AgentStatus.SUCCESS == status;
    }
}
