package com.jonychen.agent.controller;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jonychen.admin.dto.PageResponse;
import com.jonychen.agent.core.AgentAuditService.AuditEventType;
import com.jonychen.agent.dto.ExecutionHistoryVO;
import com.jonychen.agent.entity.AgentAuditLog;
import com.jonychen.agent.repository.AgentAuditLogRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Agent 审计日志管理控制器
 *
 * <p>提供管理员查询审计日志的 API：
 *
 * <ul>
 *   <li>GET /api/admin/agent/audit/logs - 分页查询审计日志
 *   <li>GET /api/admin/agent/audit/stats - 获取审计统计
 *   <li>DELETE /api/admin/agent/audit/cleanup - 清理过期日志
 * </ul>
 *
 * @author jonychen
 */
@Slf4j
@Tag(name = "Agent审计管理", description = "Agent 执行审计日志管理接口")
@RestController
@RequestMapping("/api/admin/agent/audit")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AgentAuditController {

    private final AgentAuditLogRepository auditLogRepository;

    /**
     * 分页查询审计日志
     *
     * @param page 页码（从 0 开始）
     * @param size 每页大小
     * @param userId 用户 ID（可选过滤）
     * @param agentName Agent 名称（可选过滤）
     * @param eventType 事件类型（可选过滤）
     */
    @GetMapping("/logs")
    @Operation(summary = "查询审计日志", description = "分页查询 Agent 执行审计日志")
    public ResponseEntity<PageResponse<ExecutionHistoryVO>> queryLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String agentName,
            @RequestParam(required = false) String eventType) {

        log.info(
                "[AgentAudit] 查询审计日志: page={}, size={}, userId={}, agentName={}, eventType={}",
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
     * 获取审计统计
     *
     * @param hours 统计时间范围（小时）
     */
    @GetMapping("/stats")
    @Operation(summary = "审计统计", description = "获取指定时间范围内的审计统计信息")
    public ResponseEntity<AuditStats> getStats(@RequestParam(defaultValue = "24") int hours) {

        Instant since = Instant.now().minusSeconds(hours * 3600L);

        long totalExecutions =
                auditLogRepository.countByEventTypeSince(AuditEventType.EXECUTION_START, since);
        long successCount =
                auditLogRepository.countByEventTypeSince(AuditEventType.EXECUTION_END, since);
        long errorCount =
                auditLogRepository.countByEventTypeSince(AuditEventType.EXECUTION_ERROR, since);
        long cancelCount =
                auditLogRepository.countByEventTypeSince(AuditEventType.EXECUTION_CANCEL, since);
        long toolCalls = auditLogRepository.countByEventTypeSince(AuditEventType.TOOL_CALL, since);
        long confirmations =
                auditLogRepository.countByEventTypeSince(
                        AuditEventType.CONFIRMATION_REQUIRED, since);

        return ResponseEntity.ok(
                new AuditStats(
                        totalExecutions,
                        successCount,
                        errorCount,
                        cancelCount,
                        toolCalls,
                        confirmations,
                        hours));
    }

    /**
     * 清理过期日志
     *
     * @param daysBefore 保留天数
     */
    @DeleteMapping("/cleanup")
    @Operation(summary = "清理过期日志", description = "清理指定天数之前的审计日志")
    public ResponseEntity<CleanupResult> cleanup(
            @RequestParam(defaultValue = "30") int daysBefore) {

        Instant before = Instant.now().minusSeconds(daysBefore * 86400L);
        log.info("[AgentAudit] 开始清理 {} 天前的审计日志", daysBefore);

        int deleted = auditLogRepository.deleteByTimestampBefore(before);

        log.info("[AgentAudit] 清理完成，删除 {} 条记录", deleted);
        return ResponseEntity.ok(new CleanupResult(deleted, daysBefore));
    }

    private AuditEventType parseEventType(String eventType) {
        if (eventType == null || eventType.isBlank()) return null;
        try {
            return AuditEventType.valueOf(eventType);
        } catch (IllegalArgumentException e) {
            log.warn("[AgentAudit] 未知事件类型: {}", eventType);
            return null;
        }
    }

    /** 审计统计 */
    public record AuditStats(
            long totalExecutions,
            long successCount,
            long errorCount,
            long cancelCount,
            long toolCalls,
            long confirmations,
            int hoursRange) {}

    /** 清理结果 */
    public record CleanupResult(int deletedCount, int daysBefore) {}
}
