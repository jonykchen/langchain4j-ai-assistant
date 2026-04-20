package com.jonychen.observability.prompt;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Prompt 模板实体
 *
 * @author jonychen
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "prompt_templates", schema = "app", uniqueConstraints = {
    @UniqueConstraint(name = "uk_prompt_name_version", columnNames = {"name", "version"})
}, indexes = {
    @Index(name = "idx_prompt_name", columnList = "name"),
    @Index(name = "idx_prompt_active", columnList = "active")
})
public class PromptTemplateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 模板名称
     */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /**
     * 版本号（语义化版本）
     */
    @Column(name = "version", nullable = false, length = 20)
    private String version;

    /**
     * 描述
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * 模板内容
     */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * 变量定义（JSON）
     */
    @Column(name = "variables", columnDefinition = "JSONB")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private String variables;

    /**
     * 标签
     */
    @Column(name = "tags", length = 500)
    private String tags;

    /**
     * 是否为当前激活版本
     */
    @Column(name = "active")
    @Builder.Default
    private Boolean active = false;

    /**
     * 是否为生产环境版本
     */
    @Column(name = "production")
    @Builder.Default
    private Boolean production = false;

    /**
     * 创建者
     */
    @Column(name = "created_by", length = 50)
    private String createdBy;

    /**
     * 创建时间
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ==================== A/B 测试配置 ====================

    /**
     * 是否启用 A/B 测试
     */
    @Column(name = "ab_test_enabled")
    @Builder.Default
    private Boolean abTestEnabled = false;

    /**
     * A/B 测试变体名称
     */
    @Column(name = "ab_test_variant_name", length = 50)
    private String abTestVariantName;

    /**
     * A/B 测试流量百分比
     */
    @Column(name = "ab_test_traffic_percentage", precision = 5, scale = 2)
    @Builder.Default
    private Double abTestTrafficPercentage = 0.0;

    /**
     * A/B 测试基线版本
     */
    @Column(name = "ab_test_baseline_version", length = 20)
    private String abTestBaselineVersion;

    // ==================== 使用统计 ====================

    /**
     * 总使用次数
     */
    @Column(name = "total_uses")
    @Builder.Default
    private Long totalUses = 0L;

    /**
     * 成功次数
     */
    @Column(name = "success_count")
    @Builder.Default
    private Long successCount = 0L;

    /**
     * 失败次数
     */
    @Column(name = "failure_count")
    @Builder.Default
    private Long failureCount = 0L;

    /**
     * 平均响应时间
     */
    @Column(name = "avg_response_time", precision = 10, scale = 2)
    private Double avgResponseTime;

    /**
     * 平均 Token 使用
     */
    @Column(name = "avg_token_usage", precision = 10, scale = 2)
    private Double avgTokenUsage;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = LocalDateTime.now();
        if (active == null) {
            active = false;
        }
        if (production == null) {
            production = false;
        }
        if (abTestEnabled == null) {
            abTestEnabled = false;
        }
        if (abTestTrafficPercentage == null) {
            abTestTrafficPercentage = 0.0;
        }
        if (totalUses == null) {
            totalUses = 0L;
        }
        if (successCount == null) {
            successCount = 0L;
        }
        if (failureCount == null) {
            failureCount = 0L;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 记录一次使用
     */
    public void recordUse(boolean success) {
        this.totalUses++;
        if (success) {
            this.successCount++;
        } else {
            this.failureCount++;
        }
    }
}
