package com.jonychen.observability.evaluation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 评测指标
 *
 * @author jonychen
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationMetrics {

    /** 任务完成率 */
    @Builder.Default private double taskCompletionRate = 0.0;

    /** 平均迭代次数 */
    @Builder.Default private double avgIterations = 0.0;

    /** 工具调用成功率 */
    @Builder.Default private double toolSuccessRate = 0.0;

    /** 平均响应时间（毫秒） */
    @Builder.Default private double avgResponseTimeMs = 0.0;

    /** P95 响应时间 */
    @Builder.Default private double p95ResponseTimeMs = 0.0;

    /** 平均 Token 消耗 */
    @Builder.Default private double avgTokenUsage = 0.0;

    /** 输出质量得分（0-1） */
    @Builder.Default private double qualityScore = 0.0;

    /** 思考过程质量 */
    @Builder.Default private double thoughtQualityScore = 0.0;

    /** 最终答案相关性 */
    @Builder.Default private double answerRelevanceScore = 0.0;

    /** 错误率 */
    @Builder.Default private double errorRate = 0.0;

    /** 端到端成功率 */
    @Builder.Default private double endToEndSuccessRate = 0.0;

    /** 计算综合得分 */
    public double calculateOverallScore() {
        return (taskCompletionRate * 0.2
                + toolSuccessRate * 0.15
                + qualityScore * 0.25
                + answerRelevanceScore * 0.2
                + (1 - errorRate) * 0.1
                + endToEndSuccessRate * 0.1);
    }

    /** 创建默认指标 */
    public static EvaluationMetrics defaults() {
        return EvaluationMetrics.builder().build();
    }
}
