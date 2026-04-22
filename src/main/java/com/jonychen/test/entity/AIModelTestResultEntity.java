package com.jonychen.test.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
 * AI 模型测试结果实体
 *
 * @author jonychen
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "ai_model_test_results",
        indexes = {
            @Index(name = "idx_ai_results_job", columnList = "jobId"),
            @Index(name = "idx_ai_results_category", columnList = "category"),
            @Index(name = "idx_ai_results_passed", columnList = "passed"),
            @Index(name = "idx_ai_results_created", columnList = "createdAt"),
            @Index(name = "idx_ai_results_test_case", columnList = "testCaseId")
        })
public class AIModelTestResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联的测试任务ID */
    @Column(name = "job_id", nullable = false, length = 36)
    private String jobId;

    /** 测试用例ID */
    @Column(name = "test_case_id", nullable = false, length = 100)
    private String testCaseId;

    /** 测试用例名称 */
    @Column(name = "test_name", nullable = false, length = 255)
    private String testName;

    /** 测试分类: basic, code-generation, tool-calling, conversation */
    @Column(name = "category", length = 50)
    private String category;

    /** 测试得分（0-1） */
    @Column(name = "score", precision = 5, scale = 4)
    private BigDecimal score;

    /** 是否通过 */
    @Column(name = "passed")
    @Builder.Default
    private Boolean passed = false;

    /** 断言详情（JSON数组） */
    @Column(name = "details", columnDefinition = "JSONB")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Builder.Default
    private List<Map<String, Object>> details = List.of();

    /** 响应时间（毫秒） */
    @Column(name = "response_time")
    private Long responseTime;

    /** AI 实际输出 */
    @Column(name = "actual_output", columnDefinition = "TEXT")
    private String actualOutput;

    /** 创建时间 */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (passed == null) {
            passed = false;
        }
        if (details == null) {
            details = List.of();
        }
    }
}
