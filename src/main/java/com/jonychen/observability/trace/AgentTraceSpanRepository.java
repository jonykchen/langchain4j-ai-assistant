package com.jonychen.observability.trace;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Agent Span 仓库
 *
 * @author jonychen
 */
@Repository
public interface AgentTraceSpanRepository extends JpaRepository<AgentTraceSpan, Long> {

    List<AgentTraceSpan> findByTraceIdOrderByStartTimeAsc(String traceId);

    List<AgentTraceSpan> findByTraceIdOrderByStartTimeDesc(String traceId);

    List<AgentTraceSpan> findByType(AgentTraceSpan.SpanType type);

    @Query("SELECT s FROM AgentTraceSpan s WHERE s.traceId = :traceId ORDER BY s.startTime ASC")
    List<AgentTraceSpan> findByTraceIdSorted(@Param("traceId") String traceId);

    @Query("SELECT COUNT(s) FROM AgentTraceSpan s WHERE s.traceId = :traceId")
    long countByTraceId(@Param("traceId") String traceId);

    @Query("SELECT COUNT(s) FROM AgentTraceSpan s WHERE s.traceId = :traceId AND s.success = true")
    long countSuccessfulByTraceId(@Param("traceId") String traceId);

    @Query("SELECT SUM(s.durationMs) FROM AgentTraceSpan s WHERE s.traceId = :traceId")
    Long sumDurationByTraceId(@Param("traceId") String traceId);

    @Query("SELECT SUM(s.promptTokens) FROM AgentTraceSpan s WHERE s.traceId = :traceId")
    Long sumPromptTokensByTraceId(@Param("traceId") String traceId);

    @Query("SELECT SUM(s.completionTokens) FROM AgentTraceSpan s WHERE s.traceId = :traceId")
    Long sumCompletionTokensByTraceId(@Param("traceId") String traceId);

    void deleteByTraceId(String traceId);
}
