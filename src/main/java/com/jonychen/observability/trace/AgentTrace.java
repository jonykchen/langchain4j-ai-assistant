package com.jonychen.observability.trace;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 执行追踪记录
 *
 * @author jonychen
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "agent_traces", schema = "app", indexes = {
    @Index(name = "idx_trace_session", columnList = "sessionId"),
    @Index(name = "idx_trace_status", columnList = "status"),
    @Index(name = "idx_trace_start_time", columnList = "startTime")
})
public class AgentTrace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 追踪 ID（全局唯一）
     */
    @Column(name = "trace_id", unique = true, nullable = false, length = 36)
    private String traceId;

    /**
     * 会话 ID
     */
    @Column(name = "session_id", length = 36)
    private String sessionId;

    /**
     * 用户 ID
     */
    @Column(name = "user_id", length = 50)
    private String userId;

    /**
     * Agent 类型：REACT, PLAN_EXECUTE, MULTI_AGENT
     */
    @Column(name = "agent_type", nullable = false, length = 50)
    private String agentType;

    /**
     * 任务目标/问题
     */
    @Column(name = "goal", columnDefinition = "TEXT")
    private String goal;

    /**
     * 执行状态：RUNNING, COMPLETED, FAILED, CANCELLED
     */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "RUNNING";

    /**
     * 开始时间
     */
    @Column(name = "start_time")
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    @Column(name = "end_time")
    private LocalDateTime endTime;

    /**
     * 总执行时间（毫秒）
     */
    @Column(name = "execution_time_ms")
    private Long executionTimeMs;

    /**
     * 迭代次数
     */
    @Column(name = "iterations")
    @Builder.Default
    private Integer iterations = 0;

    /**
     * 最终输出
     */
    @Column(name = "final_output", columnDefinition = "TEXT")
    private String finalOutput;

    /**
     * 错误信息
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Token 使用统计
     */
    @Embedded
    private TokenUsage tokenUsage;

    /**
     * 执行步骤详情（JSON）
     */
    @Column(name = "spans", columnDefinition = "JSONB")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Builder.Default
    private List<Map<String, Object>> spans = new ArrayList<>();

    /**
     * 元数据（标签、配置等）
     */
    @Column(name = "metadata", columnDefinition = "JSONB")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private Map<String, Object> metadata;

    /**
     * 创建时间
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
        }
        if (startTime == null) {
            startTime = LocalDateTime.now();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = "RUNNING";
        }
        if (iterations == null) {
            iterations = 0;
        }
    }

    /**
     * Token 使用统计嵌入对象
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Embeddable
    public static class TokenUsage {
        @Column(name = "prompt_tokens")
        private Long promptTokens;

        @Column(name = "completion_tokens")
        private Long completionTokens;

        @Column(name = "total_tokens")
        private Long totalTokens;

        public static TokenUsage of(long prompt, long completion) {
            return TokenUsage.builder()
                    .promptTokens(prompt)
                    .completionTokens(completion)
                    .totalTokens(prompt + completion)
                    .build();
        }
    }

    /**
     * 创建新的追踪记录
     */
    public static AgentTrace create(String sessionId, String userId, String agentType, String goal) {
        return AgentTrace.builder()
                .sessionId(sessionId)
                .userId(userId)
                .agentType(agentType)
                .goal(goal)
                .status("RUNNING")
                .startTime(LocalDateTime.now())
                .iterations(0)
                .build();
    }
}
