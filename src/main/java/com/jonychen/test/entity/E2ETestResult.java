package com.jonychen.test.entity;

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
 * E2E 测试结果实体
 *
 * @author jonychen
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "e2e_test_results",
        indexes = {
            @Index(name = "idx_e2e_results_job", columnList = "jobId"),
            @Index(name = "idx_e2e_results_status", columnList = "status"),
            @Index(name = "idx_e2e_results_created", columnList = "createdAt")
        })
public class E2ETestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联的测试任务ID */
    @Column(name = "job_id", nullable = false, length = 36)
    private String jobId;

    /** 测试用例名称 */
    @Column(name = "test_name", nullable = false, length = 255)
    private String testName;

    /** 测试状态: passed, failed, running, pending */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 执行时长（毫秒） */
    @Column(name = "duration_ms")
    private Long durationMs;

    /** 通过的断言数 */
    @Column(name = "assertions_passed")
    @Builder.Default
    private Integer assertionsPassed = 0;

    /** 失败的断言数 */
    @Column(name = "assertions_failed")
    @Builder.Default
    private Integer assertionsFailed = 0;

    /** 错误信息 */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** 创建时间 */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (assertionsPassed == null) {
            assertionsPassed = 0;
        }
        if (assertionsFailed == null) {
            assertionsFailed = 0;
        }
    }
}
