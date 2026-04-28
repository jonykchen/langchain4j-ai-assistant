package com.jonychen.observability.trace;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 执行步骤 Span
 *
 * @author jonychen
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "agent_trace_spans", schema = "app")
public class AgentTraceSpan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联的 Trace ID */
    @Column(name = "trace_id", nullable = false, length = 36)
    private String traceId;

    /** Span ID */
    @Column(name = "span_id", nullable = false, length = 36)
    private String spanId;

    /** 父 Span ID */
    @Column(name = "parent_span_id", length = 36)
    private String parentSpanId;

    /** Span 类型 */
    @Column(name = "type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private SpanType type;

    /** Span 名称 */
    @Column(name = "name", length = 200)
    private String name;

    /** 输入内容 */
    @Column(name = "input", columnDefinition = "TEXT")
    private String input;

    /** 输出内容 */
    @Column(name = "output", columnDefinition = "TEXT")
    private String output;

    /** 开始时间 */
    @Column(name = "start_time")
    private LocalDateTime startTime;

    /** 结束时间 */
    @Column(name = "end_time")
    private LocalDateTime endTime;

    /** 执行时间（毫秒） */
    @Column(name = "duration_ms")
    private Long durationMs;

    /** 是否成功 */
    @Column(name = "success")
    @Builder.Default
    private Boolean success = true;

    /** 错误信息 */
    @Column(name = "error", columnDefinition = "TEXT")
    private String error;

    /** Token 消耗 - Prompt */
    @Column(name = "prompt_tokens")
    @Builder.Default
    private Long promptTokens = 0L;

    /** Token 消耗 - Completion */
    @Column(name = "completion_tokens")
    @Builder.Default
    private Long completionTokens = 0L;

    /** 扩展属性 */
    @Column(name = "attributes", columnDefinition = "JSONB")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private Map<String, Object> attributes;

    /** 创建时间 */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (spanId == null) {
            spanId = UUID.randomUUID().toString();
        }
        if (startTime == null) {
            startTime = LocalDateTime.now();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (success == null) {
            success = true;
        }
        if (promptTokens == null) {
            promptTokens = 0L;
        }
        if (completionTokens == null) {
            completionTokens = 0L;
        }
    }

    /** Span 类型枚举 */
    public enum SpanType {
        THOUGHT, // 思考过程
        ACTION, // 工具调用
        OBSERVATION, // 观察结果
        LLM_CALL, // LLM 调用
        TOOL_EXECUTE, // 工具执行
        PLANNING, // 规划步骤
        AGENT_CALL, // 子 Agent 调用
        STATE_UPDATE // 状态更新
    }

    /** 创建思考 Span */
    public static AgentTraceSpan thought(String traceId, String thought) {
        LocalDateTime now = LocalDateTime.now();
        return AgentTraceSpan.builder()
                .traceId(traceId)
                .type(SpanType.THOUGHT)
                .name("Thought")
                .input(thought)
                .startTime(now)
                .endTime(now)
                .durationMs(0L)
                .success(true)
                .build();
    }

    /** 创建思考 Span（带时间信息） */
    public static AgentTraceSpan thought(
            String traceId,
            String thought,
            LocalDateTime startTime,
            LocalDateTime endTime,
            long durationMs) {
        return AgentTraceSpan.builder()
                .traceId(traceId)
                .type(SpanType.THOUGHT)
                .name("Thought")
                .input(thought)
                .startTime(startTime)
                .endTime(endTime)
                .durationMs(durationMs)
                .success(true)
                .build();
    }

    /** 创建工具执行 Span */
    public static AgentTraceSpan toolCall(
            String traceId,
            String toolName,
            String params,
            String result,
            long durationMs,
            boolean success,
            String error) {
        return AgentTraceSpan.builder()
                .traceId(traceId)
                .type(SpanType.TOOL_EXECUTE)
                .name("Tool: " + toolName)
                .input(params)
                .output(result)
                .durationMs(durationMs)
                .success(success)
                .error(error)
                .startTime(LocalDateTime.now().minusNanos(durationMs * 1_000_000))
                .endTime(LocalDateTime.now())
                .build();
    }

    /** 创建 LLM 调用 Span */
    public static AgentTraceSpan llmCall(
            String traceId,
            String prompt,
            String response,
            long promptTokens,
            long completionTokens,
            long durationMs) {
        return AgentTraceSpan.builder()
                .traceId(traceId)
                .type(SpanType.LLM_CALL)
                .name("LLM Call")
                .input(prompt)
                .output(response)
                .durationMs(durationMs)
                .success(true)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .startTime(LocalDateTime.now().minusNanos(durationMs * 1_000_000))
                .endTime(LocalDateTime.now())
                .build();
    }
}
