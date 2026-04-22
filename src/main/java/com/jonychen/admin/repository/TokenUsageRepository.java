package com.jonychen.admin.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.jonychen.admin.entity.TokenUsageLog;

/**
 * Token 使用记录仓库
 *
 * @author jonychen
 */
@Repository
public interface TokenUsageRepository extends JpaRepository<TokenUsageLog, Long> {

    /** 统计时间范围内的使用量 */
    @Query(
            """
            SELECT SUM(t.totalTokens), SUM(t.promptTokens), SUM(t.completionTokens),
                   COUNT(t), SUM(t.cost)
            FROM TokenUsageLog t
            WHERE t.createdAt >= :start AND t.createdAt < :end
            """)
    List<Object[]> getUsageSummary(
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 统计时间范围内的活跃用户数 */
    @Query(
            "SELECT COUNT(DISTINCT t.userId) FROM TokenUsageLog t WHERE t.createdAt >= :start AND t.createdAt < :end")
    long countDistinctUsersByCreatedAtBetween(
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 按模型统计分布 */
    @Query(
            """
            SELECT t.modelName, SUM(t.totalTokens)
            FROM TokenUsageLog t
            WHERE t.createdAt >= :start AND t.createdAt < :end
            GROUP BY t.modelName
            ORDER BY SUM(t.totalTokens) DESC
            """)
    List<Object[]> getModelDistribution(
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 按模型统计成本 */
    @Query(
            """
            SELECT t.modelName, SUM(t.totalTokens), SUM(t.promptTokens), SUM(t.completionTokens),
                   SUM(t.cost), COUNT(t)
            FROM TokenUsageLog t
            WHERE t.createdAt >= :start AND t.createdAt < :end
            GROUP BY t.modelName
            ORDER BY SUM(t.cost) DESC
            """)
    List<Object[]> getModelCostStatistics(
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 用户消费排行 */
    @Query(
            """
            SELECT t.userId, u.username, SUM(t.totalTokens), SUM(t.cost)
            FROM TokenUsageLog t
            LEFT JOIN User u ON t.userId = u.id
            WHERE t.createdAt >= :start AND t.createdAt < :end
            GROUP BY t.userId, u.username
            ORDER BY SUM(t.cost) DESC
            LIMIT :limit
            """)
    List<Object[]> getTopUsersByCost(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("limit") int limit);

    /** 用户每日使用量 */
    @Query(
            """
            SELECT SUM(t.totalTokens), SUM(t.cost)
            FROM TokenUsageLog t
            WHERE t.userId = :userId AND t.createdAt >= :start AND t.createdAt < :end
            """)
    List<Object[]> getUserUsage(
            @Param("userId") String userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /** 按用户统计使用量 */
    List<TokenUsageLog> findByUserIdOrderByCreatedAtDesc(String userId);

    /** 按会话统计使用量 */
    List<TokenUsageLog> findBySessionIdOrderByCreatedAtDesc(String sessionId);

    /** 按 Trace ID 查询使用量 */
    List<TokenUsageLog> findByTraceIdOrderByCreatedAtDesc(String traceId);
}
