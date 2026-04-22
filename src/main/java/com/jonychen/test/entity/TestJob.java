package com.jonychen.test.entity;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 测试任务实体 - 跟踪每次测试执行
 *
 * @author jonychen
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "test_jobs",
        indexes = {
            @Index(name = "idx_test_jobs_type", columnList = "testType"),
            @Index(name = "idx_test_jobs_status", columnList = "status"),
            @Index(name = "idx_test_jobs_start_time", columnList = "startTime"),
            @Index(name = "idx_test_jobs_created", columnList = "createdAt")
        })
public class TestJob {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    /** 测试类型: E2E, PERFORMANCE, AI_MODEL */
    @Column(name = "test_type", nullable = false, length = 20)
    private String testType;

    /** 状态: PENDING, RUNNING, COMPLETED, FAILED, CANCELLED */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    /** 开始时间 */
    @Column(name = "start_time")
    private LocalDateTime startTime;

    /** 结束时间 */
    @Column(name = "end_time")
    private LocalDateTime endTime;

    /** 消息 */
    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    /** 执行进度 0-100 */
    @Column(name = "progress")
    @Builder.Default
    private Integer progress = 0;

    /** 触发者（用户ID或system） */
    @Column(name = "triggered_by", length = 50)
    private String triggeredBy;

    /** 元数据 */
    @Column(name = "metadata", columnDefinition = "JSONB")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private Map<String, Object> metadata;

    /** 创建时间 */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (status == null) {
            status = "PENDING";
        }
        if (progress == null) {
            progress = 0;
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /** 创建新的测试任务 */
    public static TestJob create(String testType, String triggeredBy) {
        return TestJob.builder()
                .testType(testType)
                .status("RUNNING")
                .startTime(LocalDateTime.now())
                .progress(0)
                .triggeredBy(triggeredBy)
                .build();
    }
}
