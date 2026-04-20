package com.jonychen.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Agent 追踪 VO
 *
 * @author jonychen
 */
@Data
@Builder
public class AgentTraceVO {

    private Long id;
    private String traceId;
    private String sessionId;
    private String userId;
    private String agentType;
    private String goal;
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    private Long executionTimeMs;
    private Integer iterations;
    private String finalOutput;
    private String errorMessage;

    private TokenUsageVO tokenUsage;
    private List<Map<String, Object>> spans;
    private Map<String, Object> metadata;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @Data
    @Builder
    public static class TokenUsageVO {
        private Long promptTokens;
        private Long completionTokens;
        private Long totalTokens;
    }

    /**
     * 从实体转换为 VO
     */
    public static AgentTraceVO from(com.jonychen.observability.trace.AgentTrace trace) {
        if (trace == null) return null;

        return AgentTraceVO.builder()
                .id(trace.getId())
                .traceId(trace.getTraceId())
                .sessionId(trace.getSessionId())
                .userId(trace.getUserId())
                .agentType(trace.getAgentType())
                .goal(trace.getGoal())
                .status(trace.getStatus())
                .startTime(trace.getStartTime())
                .endTime(trace.getEndTime())
                .executionTimeMs(trace.getExecutionTimeMs())
                .iterations(trace.getIterations())
                .finalOutput(trace.getFinalOutput())
                .errorMessage(trace.getErrorMessage())
                .tokenUsage(trace.getTokenUsage() != null
                        ? TokenUsageVO.builder()
                                .promptTokens(trace.getTokenUsage().getPromptTokens())
                                .completionTokens(trace.getTokenUsage().getCompletionTokens())
                                .totalTokens(trace.getTokenUsage().getTotalTokens())
                                .build()
                        : null)
                .spans(trace.getSpans())
                .metadata(trace.getMetadata())
                .createdAt(trace.getCreatedAt())
                .build();
    }
}
