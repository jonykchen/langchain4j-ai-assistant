package com.jonychen.agent.dto;

import java.time.Instant;
import java.util.Map;

/**
 * 执行历史 VO
 *
 * @author jonychen
 */
public record ExecutionHistoryVO(
        String traceId,
        String userId,
        String agentName,
        String eventType,
        String status,
        Instant timestamp,
        String clientIp,
        Map<String, Object> details) {

    public static ExecutionHistoryVO from(com.jonychen.agent.entity.AgentAuditLog log) {
        String status =
                switch (log.getEventType().name()) {
                    case "EXECUTION_START" -> "RUNNING";
                    case "EXECUTION_END" -> "SUCCESS";
                    case "EXECUTION_ERROR" -> "FAILED";
                    case "EXECUTION_CANCEL" -> "CANCELLED";
                    default -> log.getEventType().name();
                };

        return new ExecutionHistoryVO(
                log.getTraceId(),
                log.getUserId(),
                log.getAgentName(),
                log.getEventType().name(),
                status,
                log.getTimestamp(),
                log.getClientIp(),
                log.getEventData());
    }
}
