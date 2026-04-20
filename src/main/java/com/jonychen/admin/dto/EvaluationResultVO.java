package com.jonychen.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 评测结果 VO
 *
 * @author jonychen
 */
@Data
@Builder
public class EvaluationResultVO {

    private String traceId;
    private double overallScore;
    private String recommendation;
    private List<EvaluatorResultVO> evaluatorResults;
    private MetricsVO metrics;

    @Data
    @Builder
    public static class EvaluatorResultVO {
        private String evaluatorId;
        private boolean passed;
        private double score;
        private Map<String, Object> details;
        private List<String> issues;
        private String recommendation;
    }

    @Data
    @Builder
    public static class MetricsVO {
        private double taskCompletionRate;
        private double avgIterations;
        private double toolSuccessRate;
        private double avgResponseTimeMs;
        private double qualityScore;
        private double errorRate;
    }

    /**
     * 评测报告 VO
     */
    @Data
    @Builder
    public static class ReportVO {
        private String reportId;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private Date generatedAt;

        private int totalTraces;
        private double avgOverallScore;
        private double passRate;
        private List<EvaluationResultVO> results;
    }
}
