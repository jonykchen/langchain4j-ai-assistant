package com.jonychen.agent.repository;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.QueryHint;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.jonychen.agent.core.AgentAuditService.AuditEventType;
import com.jonychen.agent.entity.AgentAuditLog;

/**
 * Agent 审计日志 Repository
 *
 * @author jonychen
 */
@Repository
public interface AgentAuditLogRepository extends JpaRepository<AgentAuditLog, Long> {

    List<AgentAuditLog> findByTraceIdOrderByTimestampAsc(String traceId);

    Page<AgentAuditLog> findByUserIdOrderByTimestampDesc(String userId, Pageable pageable);

    Page<AgentAuditLog> findByEventTypeAndTimestampAfter(
            AuditEventType eventType, LocalDateTime since, Pageable pageable);

    @Query(
            value =
                    "SELECT * FROM audit.agent_audit_logs a WHERE "
                            + "(:userId IS NULL OR a.user_id = :userId) "
                            + "AND (:agentName IS NULL OR a.agent_name = :agentName) "
                            + "AND (:eventType IS NULL OR a.event_type = :eventType) "
                            + "AND (:startTime IS NULL OR a.timestamp >= :startTime) "
                            + "AND (:endTime IS NULL OR a.timestamp <= :endTime) "
                            + "ORDER BY a.timestamp DESC",
            nativeQuery = true)
    @QueryHints(@QueryHint(name = org.hibernate.jpa.HibernateHints.HINT_FETCH_SIZE, value = "100"))
    Page<AgentAuditLog> findByConditions(
            @Param("userId") String userId,
            @Param("agentName") String agentName,
            @Param("eventType") String eventType,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable);

    @Query(
            value =
                    "SELECT COUNT(*) FROM audit.agent_audit_logs a WHERE a.event_type = :eventType AND a.timestamp >= :since",
            nativeQuery = true)
    long countByEventTypeSince(
            @Param("eventType") String eventType, @Param("since") LocalDateTime since);

    @Modifying
    @Query(
            value = "DELETE FROM audit.agent_audit_logs a WHERE a.timestamp < :before",
            nativeQuery = true)
    int deleteByTimestampBefore(@Param("before") LocalDateTime before);
}
