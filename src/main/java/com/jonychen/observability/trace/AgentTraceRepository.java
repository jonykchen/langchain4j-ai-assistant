package com.jonychen.observability.trace;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Agent 追踪仓库
 *
 * @author jonychen
 */
@Repository
public interface AgentTraceRepository extends JpaRepository<AgentTrace, Long> {

    Optional<AgentTrace> findByTraceId(String traceId);

    List<AgentTrace> findBySessionIdOrderByStartTimeDesc(String sessionId);

    List<AgentTrace> findByUserIdOrderByStartTimeDesc(String userId);

    List<AgentTrace> findByStatus(String status);

    List<AgentTrace> findByAgentTypeOrderByStartTimeDesc(String agentType);

    @Query(
            "SELECT t FROM AgentTrace t WHERE "
                    + "(:userId IS NULL OR t.userId = :userId) AND "
                    + "(:status IS NULL OR t.status = :status) AND "
                    + "(:agentType IS NULL OR t.agentType = :agentType) "
                    + "ORDER BY t.startTime DESC")
    List<AgentTrace> findByConditions(
            @Param("userId") String userId,
            @Param("status") String status,
            @Param("agentType") String agentType);

    @Query(
            "SELECT t FROM AgentTrace t WHERE "
                    + "(:userId IS NULL OR t.userId = :userId) AND "
                    + "(:status IS NULL OR t.status = :status) AND "
                    + "(:agentType IS NULL OR t.agentType = :agentType) "
                    + "ORDER BY t.startTime DESC LIMIT :limit")
    List<AgentTrace> findByConditionsLimit(
            @Param("userId") String userId,
            @Param("status") String status,
            @Param("agentType") String agentType,
            @Param("limit") int limit);

    long countByStatus(String status);

    long countByUserId(String userId);

    @Query("SELECT COUNT(t) FROM AgentTrace t WHERE t.status = 'COMPLETED'")
    long countCompleted();

    @Query("SELECT COUNT(t) FROM AgentTrace t WHERE t.status = 'FAILED'")
    long countFailed();
}
