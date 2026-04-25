package com.jonychen.agent.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 执行详情 VO
 *
 * @author jonychen
 */
public record ExecutionDetailVO(
        String traceId,
        String userId,
        String agentName,
        String status,
        Instant startTime,
        Instant endTime,
        long durationMs,
        int totalSteps,
        List<StepDetail> steps,
        Map<String, Object> summary) {

    /** 步骤详情 */
    public record StepDetail(
            String eventType, Instant timestamp, String agentName, Map<String, Object> details) {}
}
