package com.jonychen.agent.core;

import java.time.Instant;
import java.util.Map;

import com.jonychen.tool.RiskLevel;

/**
 * Agent 事件基类（Sealed Interface）
 *
 * <p>所有 Agent 执行事件都实现此 sealed interface，用于 SSE 流式推送执行进度。 每个事件包含 traceId、sequenceNumber 和
 * timestamp，支持前端展示和追踪。
 *
 * <p>事件类型：
 *
 * <ul>
 *   <li>StepStart - 步骤开始
 *   <li>StepEnd - 步骤结束
 *   <li>Thought - 思考过程
 *   <li>ToolCall - 工具调用
 *   <li>ToolResult - 工具结果
 *   <li>AgentCall - Agent 委托
 *   <li>AgentResult - Agent 返回结果
 *   <li>ConfirmationRequired - 需要用户确认
 *   <li>AgentDone - 执行完成
 *   <li>AgentError - 执行错误
 *   <li>Heartbeat - 心跳保活
 * </ul>
 *
 * @author jonychen
 */
public sealed interface AgentEvent
        permits AgentEvent.StepStart,
                AgentEvent.StepEnd,
                AgentEvent.Thought,
                AgentEvent.ToolCall,
                AgentEvent.ToolResult,
                AgentEvent.AgentCall,
                AgentEvent.AgentResult,
                AgentEvent.ConfirmationRequired,
                AgentEvent.AgentDone,
                AgentEvent.AgentError,
                AgentEvent.Heartbeat {

    /** 追踪 ID，用于关联同一执行的所有事件 */
    String traceId();

    /** 事件序号，全局递增，前端据此检测事件丢失 */
    int sequenceNumber();

    /** 事件时间戳 */
    Instant timestamp();

    /** 事件类型标识，用于前端区分事件类型 */
    String eventType();

    // ===== 事件实现 =====

    /**
     * 步骤开始事件
     *
     * @param traceId 追踪 ID
     * @param sequenceNumber 事件序号
     * @param timestamp 时间戳
     * @param stepIndex 步骤序号
     * @param type 步骤类型
     * @param agentName Agent 名称
     */
    record StepStart(
            String traceId,
            int sequenceNumber,
            Instant timestamp,
            int stepIndex,
            StepType type,
            String agentName)
            implements AgentEvent {
        @Override
        public String eventType() {
            return "step_start";
        }
    }

    /**
     * 步骤结束事件
     *
     * @param traceId 追踪 ID
     * @param sequenceNumber 事件序号
     * @param timestamp 时间戳
     * @param stepIndex 步骤序号
     * @param success 是否成功
     * @param summary 步骤摘要
     * @param durationMs 步骤耗时（毫秒）
     */
    record StepEnd(
            String traceId,
            int sequenceNumber,
            Instant timestamp,
            int stepIndex,
            boolean success,
            String summary,
            long durationMs)
            implements AgentEvent {
        @Override
        public String eventType() {
            return "step_end";
        }
    }

    /**
     * 思考过程事件
     *
     * @param traceId 追踪 ID
     * @param sequenceNumber 事件序号
     * @param timestamp 时间戳
     * @param stepIndex 步骤序号
     * @param content 思考内容
     */
    record Thought(
            String traceId, int sequenceNumber, Instant timestamp, int stepIndex, String content)
            implements AgentEvent {
        @Override
        public String eventType() {
            return "thought";
        }
    }

    /**
     * 工具调用事件
     *
     * @param traceId 追踪 ID
     * @param sequenceNumber 事件序号
     * @param timestamp 时间戳
     * @param stepIndex 步骤序号
     * @param toolName 工具名称
     * @param params 工具参数
     */
    record ToolCall(
            String traceId,
            int sequenceNumber,
            Instant timestamp,
            int stepIndex,
            String toolName,
            Map<String, Object> params)
            implements AgentEvent {
        @Override
        public String eventType() {
            return "tool_call";
        }
    }

    /**
     * 工具结果事件
     *
     * @param traceId 追踪 ID
     * @param sequenceNumber 事件序号
     * @param timestamp 时间戳
     * @param stepIndex 步骤序号
     * @param toolName 工具名称
     * @param result 工具返回结果
     * @param success 是否成功
     * @param error 错误信息
     * @param executionTimeMs 工具执行耗时（毫秒）
     */
    record ToolResult(
            String traceId,
            int sequenceNumber,
            Instant timestamp,
            int stepIndex,
            String toolName,
            Object result,
            boolean success,
            String error,
            long executionTimeMs)
            implements AgentEvent {
        @Override
        public String eventType() {
            return "tool_result";
        }
    }

    /**
     * Agent 委托事件
     *
     * @param traceId 追踪 ID
     * @param sequenceNumber 事件序号
     * @param timestamp 时间戳
     * @param stepIndex 步骤序号
     * @param targetAgent 目标 Agent 名称
     * @param input 委托输入
     */
    record AgentCall(
            String traceId,
            int sequenceNumber,
            Instant timestamp,
            int stepIndex,
            String targetAgent,
            String input)
            implements AgentEvent {
        @Override
        public String eventType() {
            return "agent_call";
        }
    }

    /**
     * Agent 返回结果事件
     *
     * @param traceId 追踪 ID
     * @param sequenceNumber 事件序号
     * @param timestamp 时间戳
     * @param stepIndex 步骤序号
     * @param agentName Agent 名称
     * @param output 输出内容
     * @param success 是否成功
     */
    record AgentResult(
            String traceId,
            int sequenceNumber,
            Instant timestamp,
            int stepIndex,
            String agentName,
            String output,
            boolean success)
            implements AgentEvent {
        @Override
        public String eventType() {
            return "agent_result";
        }
    }

    /**
     * 需要用户确认事件
     *
     * @param traceId 追踪 ID
     * @param sequenceNumber 事件序号
     * @param timestamp 时间戳
     * @param stepIndex 步骤序号
     * @param confirmationId 确认 ID（用于用户确认时回传）
     * @param operation 操作名称
     * @param description 操作描述
     * @param riskLevel 风险等级
     * @param params 操作参数
     */
    record ConfirmationRequired(
            String traceId,
            int sequenceNumber,
            Instant timestamp,
            int stepIndex,
            String confirmationId,
            String operation,
            String description,
            RiskLevel riskLevel,
            Map<String, Object> params)
            implements AgentEvent {
        @Override
        public String eventType() {
            return "confirmation_required";
        }
    }

    /**
     * 执行完成事件
     *
     * @param traceId 追踪 ID
     * @param sequenceNumber 事件序号
     * @param timestamp 时间戳
     * @param agentName Agent 名称
     * @param output 最终输出
     * @param totalSteps 总步骤数
     * @param tokenUsage Token 使用量
     * @param durationMs 执行耗时
     */
    record AgentDone(
            String traceId,
            int sequenceNumber,
            Instant timestamp,
            String agentName,
            String output,
            int totalSteps,
            TokenUsage tokenUsage,
            long durationMs)
            implements AgentEvent {
        @Override
        public String eventType() {
            return "agent_done";
        }
    }

    /**
     * 执行错误事件
     *
     * @param traceId 追踪 ID
     * @param sequenceNumber 事件序号
     * @param timestamp 时间戳
     * @param errorCode 错误码
     * @param message 错误消息
     * @param details 详细信息
     * @param recoverable 是否可恢复
     */
    record AgentError(
            String traceId,
            int sequenceNumber,
            Instant timestamp,
            String errorCode,
            String message,
            String details,
            boolean recoverable)
            implements AgentEvent {
        @Override
        public String eventType() {
            return "agent_error";
        }
    }

    /**
     * 心跳保活事件
     *
     * @param traceId 追踪 ID
     * @param sequenceNumber 事件序号
     * @param timestamp 时间戳
     */
    record Heartbeat(String traceId, int sequenceNumber, Instant timestamp) implements AgentEvent {
        @Override
        public String eventType() {
            return "heartbeat";
        }
    }

    // ===== 工厂方法 =====

    /** 创建步骤开始事件 */
    static StepStart stepStart(
            String traceId, int sequenceNumber, int stepIndex, StepType type, String agentName) {
        return new StepStart(traceId, sequenceNumber, Instant.now(), stepIndex, type, agentName);
    }

    /** 创建步骤结束事件 */
    static StepEnd stepEnd(
            String traceId,
            int sequenceNumber,
            int stepIndex,
            boolean success,
            String summary,
            long durationMs) {
        return new StepEnd(
                traceId, sequenceNumber, Instant.now(), stepIndex, success, summary, durationMs);
    }

    /** 创建思考过程事件 */
    static Thought thought(String traceId, int sequenceNumber, int stepIndex, String content) {
        return new Thought(traceId, sequenceNumber, Instant.now(), stepIndex, content);
    }

    /** 创建工具调用事件 */
    static ToolCall toolCall(
            String traceId,
            int sequenceNumber,
            int stepIndex,
            String toolName,
            Map<String, Object> params) {
        return new ToolCall(traceId, sequenceNumber, Instant.now(), stepIndex, toolName, params);
    }

    /** 创建工具结果事件 */
    static ToolResult toolResult(
            String traceId,
            int sequenceNumber,
            int stepIndex,
            String toolName,
            Object result,
            boolean success,
            String error,
            long executionTimeMs) {
        return new ToolResult(
                traceId,
                sequenceNumber,
                Instant.now(),
                stepIndex,
                toolName,
                result,
                success,
                error,
                executionTimeMs);
    }

    /** 创建 Agent 委托事件 */
    static AgentCall agentCall(
            String traceId, int sequenceNumber, int stepIndex, String targetAgent, String input) {
        return new AgentCall(traceId, sequenceNumber, Instant.now(), stepIndex, targetAgent, input);
    }

    /** 创建 Agent 返回结果事件 */
    static AgentResult agentResult(
            String traceId,
            int sequenceNumber,
            int stepIndex,
            String agentName,
            String output,
            boolean success) {
        return new AgentResult(
                traceId, sequenceNumber, Instant.now(), stepIndex, agentName, output, success);
    }

    /** 创建需要确认事件 */
    static ConfirmationRequired confirmationRequired(
            String traceId,
            int sequenceNumber,
            int stepIndex,
            String confirmationId,
            String operation,
            String description,
            RiskLevel riskLevel,
            Map<String, Object> params) {
        return new ConfirmationRequired(
                traceId,
                sequenceNumber,
                Instant.now(),
                stepIndex,
                confirmationId,
                operation,
                description,
                riskLevel,
                params);
    }

    /** 创建执行完成事件 */
    static AgentDone done(
            String traceId,
            int sequenceNumber,
            String agentName,
            String output,
            int totalSteps,
            TokenUsage tokenUsage,
            long durationMs) {
        return new AgentDone(
                traceId,
                sequenceNumber,
                Instant.now(),
                agentName,
                output,
                totalSteps,
                tokenUsage,
                durationMs);
    }

    /** 创建执行错误事件 */
    static AgentError error(
            String traceId,
            int sequenceNumber,
            String errorCode,
            String message,
            String details,
            boolean recoverable) {
        return new AgentError(
                traceId, sequenceNumber, Instant.now(), errorCode, message, details, recoverable);
    }

    /** 创建心跳事件 */
    static Heartbeat heartbeat(String traceId, int sequenceNumber) {
        return new Heartbeat(traceId, sequenceNumber, Instant.now());
    }
}
