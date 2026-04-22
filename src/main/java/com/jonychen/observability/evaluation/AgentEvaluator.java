package com.jonychen.observability.evaluation;

import java.util.List;
import java.util.Map;

import com.jonychen.observability.trace.AgentTrace;

/**
 * Agent 评测器接口
 *
 * @author jonychen
 */
public interface AgentEvaluator {

    /** 评测名称 */
    String getName();

    /** 评测描述 */
    String getDescription();

    /** 执行评测 */
    EvaluationResult evaluate(EvaluationContext context);

    /** 评测一批追踪记录 */
    BatchEvaluationResult evaluateBatch(List<AgentTrace> traces);

    /** 评测上下文 */
    record EvaluationContext(
            AgentTrace trace,
            String expectedOutput,
            List<String> expectedTools,
            Map<String, Object> constraints,
            String evaluationMode) {}

    /** 评测结果 */
    record EvaluationResult(
            String evaluatorId,
            boolean passed,
            double score,
            Map<String, Object> details,
            List<String> issues,
            String recommendation) {}

    /** 批量评测结果 */
    record BatchEvaluationResult(
            String batchId,
            int totalCount,
            int passedCount,
            int failedCount,
            EvaluationMetrics aggregateMetrics,
            List<EvaluationResult> individualResults,
            Map<String, Object> summary) {}
}
