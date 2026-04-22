package com.jonychen.observability.evaluation;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.jonychen.observability.trace.AgentTrace;
import com.jonychen.observability.trace.AgentTraceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 评测服务
 *
 * @author jonychen
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationService {

    private final AgentTraceService traceService;
    private final List<AgentEvaluator> evaluators;

    /** 执行完整评测 */
    public FullEvaluationResult evaluateFull(String traceId, EvaluationRequest request) {
        AgentTrace trace = traceService.getTrace(traceId);

        List<AgentEvaluator.EvaluationResult> evaluatorResults = new ArrayList<>();
        for (AgentEvaluator evaluator : evaluators) {
            try {
                AgentEvaluator.EvaluationResult result =
                        evaluator.evaluate(
                                new AgentEvaluator.EvaluationContext(
                                        trace,
                                        request.expectedOutput(),
                                        request.expectedTools(),
                                        request.constraints(),
                                        request.mode()));
                evaluatorResults.add(result);
            } catch (Exception e) {
                log.error("Evaluator {} failed", evaluator.getName(), e);
            }
        }

        // 计算综合指标
        EvaluationMetrics metrics = aggregateMetrics(trace, evaluatorResults);

        return new FullEvaluationResult(
                traceId,
                evaluatorResults,
                metrics,
                metrics.calculateOverallScore(),
                generateOverallRecommendation(evaluatorResults));
    }

    /** 批量评测 */
    public List<FullEvaluationResult> evaluateBatch(List<String> traceIds) {
        return traceIds.stream()
                .map(id -> evaluateFull(id, new EvaluationRequest(null, null, Map.of(), "batch")))
                .toList();
    }

    /** 生成评测报告 */
    public EvaluationReport generateReport(List<String> traceIds) {
        List<FullEvaluationResult> results = evaluateBatch(traceIds);

        // 汇总统计
        double avgScore =
                results.stream()
                        .mapToDouble(FullEvaluationResult::overallScore)
                        .average()
                        .orElse(0);

        int passCount = (int) results.stream().filter(r -> r.overallScore() >= 0.7).count();

        EvaluationMetrics aggregateMetrics =
                EvaluationMetrics.builder()
                        .taskCompletionRate(
                                traceIds.isEmpty() ? 0 : (double) passCount / traceIds.size())
                        .build();

        return new EvaluationReport(
                UUID.randomUUID().toString(),
                new Date(),
                traceIds.size(),
                results,
                aggregateMetrics,
                Map.of(
                        "avgOverallScore",
                        avgScore,
                        "passRate",
                        traceIds.isEmpty() ? 0 : (double) passCount / traceIds.size()));
    }

    /** 获取可用的评测器列表 */
    public List<EvaluatorInfo> getAvailableEvaluators() {
        return evaluators.stream()
                .map(e -> new EvaluatorInfo(e.getName(), e.getDescription()))
                .toList();
    }

    private EvaluationMetrics aggregateMetrics(
            AgentTrace trace, List<AgentEvaluator.EvaluationResult> results) {
        EvaluationMetrics.EvaluationMetricsBuilder builder = EvaluationMetrics.builder();

        for (AgentEvaluator.EvaluationResult result : results) {
            Map<String, Object> details = result.details();
            if (details.containsKey("successRate")) {
                builder.toolSuccessRate((double) details.get("successRate"));
            }
            if (details.containsKey("taskCompletionRate")) {
                builder.taskCompletionRate((double) details.get("taskCompletionRate"));
            }
        }

        builder.avgIterations(trace.getIterations() != null ? trace.getIterations() : 0);
        if (trace.getExecutionTimeMs() != null) {
            builder.avgResponseTimeMs(trace.getExecutionTimeMs());
        }

        return builder.build();
    }

    private String generateOverallRecommendation(List<AgentEvaluator.EvaluationResult> results) {
        List<String> allIssues = results.stream().flatMap(r -> r.issues().stream()).toList();

        if (allIssues.isEmpty()) {
            return "整体表现良好";
        }

        // 统计最常见的问题
        Map<String, Long> issueCounts =
                allIssues.stream()
                        .collect(
                                java.util.stream.Collectors.groupingBy(
                                        s -> s, java.util.stream.Collectors.counting()));

        return "主要问题: "
                + issueCounts.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .limit(3)
                        .map(Map.Entry::getKey)
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("无明显问题");
    }

    // DTOs
    public record EvaluationRequest(
            String expectedOutput,
            List<String> expectedTools,
            Map<String, Object> constraints,
            String mode) {}

    public record FullEvaluationResult(
            String traceId,
            List<AgentEvaluator.EvaluationResult> evaluatorResults,
            EvaluationMetrics metrics,
            double overallScore,
            String recommendation) {}

    public record EvaluationReport(
            String reportId,
            Date generatedAt,
            int totalTraces,
            List<FullEvaluationResult> results,
            EvaluationMetrics aggregateMetrics,
            Map<String, Object> summary) {}

    public record EvaluatorInfo(String name, String description) {}
}
