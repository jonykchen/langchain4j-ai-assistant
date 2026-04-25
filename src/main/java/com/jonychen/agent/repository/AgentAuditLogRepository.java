package com.jonychen.agent.repository;

import java.time.Instant;
import java.util.List;

import jakarta.persistence.QueryHint;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
            AuditEventType eventType, Instant since, Pageable pageable);

    @Query(
            "SELECT a FROM AgentAuditLog a WHERE "
                    + "(:userId IS NULL OR a.userId = :userId) "
                    + "AND (:agentName IS NULL OR a.agentName = :agentName) "
                    + "AND (:eventType IS NULL OR a.eventType = :eventType) "
                    + "AND (:startTime IS NULL OR a.timestamp >= :startTime) "
                    + "AND (:endTime IS NULL OR a.timestamp <= :endTime) "
                    + "ORDER BY a.timestamp DESC")
    @QueryHints(@QueryHint(name = org.hibernate.jpa.HibernateHints.HINT_FETCH_SIZE, value = "100"))
    Page<AgentAuditLog> findByConditions(
            @Param("userId") String userId,
            @Param("agentName") String agentName,
            @Param("eventType") AuditEventType eventType,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime,
            Pageable pageable);

    @Query(
            "SELECT COUNT(a) FROM AgentAuditLog a WHERE a.eventType = :eventType AND a.timestamp >= :since")
    long countByEventTypeSince(
            @Param("eventType") AuditEventType eventType, @Param("since") Instant since);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM AgentAuditLog a WHERE a.timestamp < :before")
    int deleteByTimestampBefore(@Param("before") Instant before);
}
