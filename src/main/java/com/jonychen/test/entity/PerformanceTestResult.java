package com.jonychen.test.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 性能测试结果实体
 *
 * @author jonychen
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "performance_test_results",
        indexes = {
            @Index(name = "idx_perf_results_job", columnList = "jobId"),
            @Index(name = "idx_perf_results_simulation", columnList = "simulation"),
            @Index(name = "idx_perf_results_created", columnList = "createdAt")
        })
public class PerformanceTestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联的测试任务ID */
    @Column(name = "job_id", nullable = false, length = 36)
    private String jobId;

    /** 模拟场景名称 */
    @Column(name = "simulation", nullable = false, length = 100)
    private String simulation;

    /** 总请求数 */
    @Column(name = "requests")
    @Builder.Default
    private Long requests = 0L;

    /** 成功率（百分比） */
    @Column(name = "success_rate", precision = 5, scale = 2)
    private BigDecimal successRate;

    /** 平均响应时间（毫秒） */
    @Column(name = "avg_response_time")
    private Long avgResponseTime;

    /** 最大响应时间（毫秒） */
    @Column(name = "max_response_time")
    private Long maxResponseTime;

    /** P95 响应时间（毫秒） */
    @Column(name = "p95_response_time")
    private Long p95ResponseTime;

    /** P99 响应时间（毫秒） */
    @Column(name = "p99_response_time")
    private Long p99ResponseTime;

    /** 测试开始时间 */
    @Column(name = "start_time")
    private LocalDateTime startTime;

    /** 测试结束时间 */
    @Column(name = "end_time")
    private LocalDateTime endTime;

    /** 创建时间 */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (requests == null) {
            requests = 0L;
        }
    }
}
