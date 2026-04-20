package com.jonychen.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Prompt 模板 VO
 *
 * @author jonychen
 */
@Data
@Builder
public class PromptTemplateVO {

    private Long id;
    private String name;
    private String version;
    private String description;
    private String content;
    private String variables;
    private String tags;
    private Boolean active;
    private Boolean production;
    private String createdBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    // A/B 测试
    private Boolean abTestEnabled;
    private String abTestVariantName;
    private Double abTestTrafficPercentage;
    private String abTestBaselineVersion;

    // 使用统计
    private Long totalUses;
    private Long successCount;
    private Long failureCount;
    private Double avgResponseTime;
    private Double avgTokenUsage;

    /**
     * 从实体转换为 VO
     */
    public static PromptTemplateVO from(com.jonychen.observability.prompt.PromptTemplateEntity entity) {
        if (entity == null) return null;

        return PromptTemplateVO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .version(entity.getVersion())
                .description(entity.getDescription())
                .content(entity.getContent())
                .variables(entity.getVariables())
                .tags(entity.getTags())
                .active(entity.getActive())
                .production(entity.getProduction())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .abTestEnabled(entity.getAbTestEnabled())
                .abTestVariantName(entity.getAbTestVariantName())
                .abTestTrafficPercentage(entity.getAbTestTrafficPercentage())
                .abTestBaselineVersion(entity.getAbTestBaselineVersion())
                .totalUses(entity.getTotalUses())
                .successCount(entity.getSuccessCount())
                .failureCount(entity.getFailureCount())
                .avgResponseTime(entity.getAvgResponseTime())
                .avgTokenUsage(entity.getAvgTokenUsage())
                .build();
    }
}
