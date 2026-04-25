package com.jonychen.agent.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jonychen.admin.dto.PageResponse;
import com.jonychen.agent.core.AgentAuditService.AuditEventType;
import com.jonychen.agent.dto.ExecutionDetailVO;
import com.jonychen.agent.dto.ExecutionDetailVO.StepDetail;
import com.jonychen.agent.dto.ExecutionHistoryVO;
import com.jonychen.agent.entity.AgentAuditLog;
import com.jonychen.agent.repository.AgentAuditLogRepository;

import lombok.RequiredArgsConstructor;

/**
 * Agent 执行历史控制器
 *
 * <p>提供 Agent 执行历史的查询 API：
 *
 * <ul>
 *   <li>GET /api/agent/history - 分页查询执行历史
 *   <li>GET /api/agent/history/{traceId} - 获取执行详情
 * </ul>
 *
 * @author jonychen
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentHistoryController {

    private static final Logger log = LoggerFactory.getLogger(AgentHistoryController.class);

    private final AgentAuditLogRepository auditLogRepository;

    /**
     * 分页查询执行历史
     *
     * @param page 页码（从 0 开始）
     * @param size 每页大小
     * @param userId 用户 ID（可选过滤）
     * @param agentName Agent 名称（可选过滤）
     * @param eventType 事件类型（可选过滤）
     */
    @GetMapping("/history")
    public ResponseEntity<PageResponse<ExecutionHistoryVO>> getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String agentName,
            @RequestParam(required = false) String eventType) {

        log.debug(
                "[AgentHistory] 查询执行历史: page={}, size={}, userId={}, agentName={}, eventType={}",
                page,
                size,
                userId,
                agentName,
                eventType);

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("timestamp").descending());

        AuditEventType type = parseEventType(eventType);
        Page<AgentAuditLog> auditPage =
                auditLogRepository.findByConditions(
                        userId, agentName, type, null, null, pageRequest);

        List<ExecutionHistoryVO> vos =
                auditPage.getContent().stream().map(ExecutionHistoryVO::from).toList();

        return ResponseEntity.ok(PageResponse.of(vos, auditPage.getTotalElements(), page, size));
    }

    /**
     * 获取执行详情
     *
     * @param traceId 追踪 ID
     */
    @GetMapping("/history/{traceId}")
    public ResponseEntity<ExecutionDetailVO> getExecutionDetail(@PathVariable String traceId) {
        log.debug("[AgentHistory] 查询执行详情: traceId={}", traceId);

        List<AgentAuditLog> logs = auditLogRepository.findByTraceIdOrderByTimestampAsc(traceId);
        if (logs.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // 提取执行信息
        String userId = null;
        String agentName = null;
        String status = "UNKNOWN";
        Instant startTime = null;
        Instant endTime = null;
        long durationMs = 0;
        int totalSteps = 0;

        for (AgentAuditLog auditLog : logs) {
            if (auditLog.getEventType() == AuditEventType.EXECUTION_START) {
                userId = auditLog.getUserId();
                agentName = auditLog.getAgentName();
                startTime = auditLog.getTimestamp();
                status = "RUNNING";
            } else if (auditLog.getEventType() == AuditEventType.EXECUTION_END) {
                endTime = auditLog.getTimestamp();
                status = "SUCCESS";
                if (auditLog.getDetails() != null) {
                    Object dur = auditLog.getDetails().get("durationMs");
                    if (dur instanceof Number n) durationMs = n.longValue();
                    Object steps = auditLog.getDetails().get("totalSteps");
                    if (steps instanceof Number n) totalSteps = n.intValue();
                }
            } else if (auditLog.getEventType() == AuditEventType.EXECUTION_ERROR) {
                endTime = auditLog.getTimestamp();
                status = "FAILED";
            } else if (auditLog.getEventType() == AuditEventType.EXECUTION_CANCEL) {
                endTime = auditLog.getTimestamp();
                status = "CANCELLED";
            }
        }

        if (startTime != null && endTime != null && durationMs == 0) {
            durationMs = java.time.Duration.between(startTime, endTime).toMillis();
        }

        List<StepDetail> steps =
                logs.stream()
                        .map(
                                auditLog ->
                                        new StepDetail(
                                                auditLog.getEventType().name(),
                                                auditLog.getTimestamp(),
                                                auditLog.getAgentName(),
                                                auditLog.getDetails()))
                        .toList();

        Map<String, Object> summary =
                Map.of(
                        "totalEvents", logs.size(),
                        "status", status,
                        "durationMs", durationMs,
                        "totalSteps", totalSteps);

        return ResponseEntity.ok(
                new ExecutionDetailVO(
                        traceId,
                        userId,
                        agentName,
                        status,
                        startTime,
                        endTime,
                        durationMs,
                        totalSteps,
                        steps,
                        summary));
    }

    private AuditEventType parseEventType(String eventType) {
        if (eventType == null || eventType.isBlank()) return null;
        try {
            return AuditEventType.valueOf(eventType);
        } catch (IllegalArgumentException e) {
            log.warn("[AgentHistory] 未知事件类型: {}", eventType);
            return null;
        }
    }
}
