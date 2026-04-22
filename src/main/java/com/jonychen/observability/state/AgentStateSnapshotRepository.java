package com.jonychen.observability.state;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Agent 状态快照仓库
 *
 * @author jonychen
 */
@Repository
public interface AgentStateSnapshotRepository extends JpaRepository<AgentStateSnapshot, Long> {

    Optional<AgentStateSnapshot> findBySnapshotId(String snapshotId);

    List<AgentStateSnapshot> findBySessionIdOrderByCreatedAtDesc(String sessionId);

    @Query(
            "SELECT s FROM AgentStateSnapshot s WHERE s.sessionId = :sessionId "
                    + "AND s.resumable = true AND s.expiresAt > CURRENT_TIMESTAMP "
                    + "ORDER BY s.createdAt DESC LIMIT 1")
    Optional<AgentStateSnapshot> findLatestResumableBySessionId(
            @Param("sessionId") String sessionId);

    List<AgentStateSnapshot> findByExpiresAtBefore(LocalDateTime expiresAt);

    List<AgentStateSnapshot> findByResumableTrue();

    List<AgentStateSnapshot> findByTraceId(String traceId);

    @Query(
            "SELECT s FROM AgentStateSnapshot s WHERE s.resumable = true "
                    + "AND s.expiresAt > CURRENT_TIMESTAMP")
    List<AgentStateSnapshot> findAllResumable();

    long countBySessionId(String sessionId);

    void deleteBySessionId(String sessionId);
}
