package com.jonychen.observability.evaluation.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.jonychen.observability.evaluation.AgentEvaluator;
import com.jonychen.observability.evaluation.EvaluationMetrics;
import com.jonychen.observability.trace.AgentTrace;

import lombok.extern.slf4j.Slf4j;

/**
 * 工具执行评测器
 *
 * @author jonychen
 */
@Slf4j
@Component
public class ToolExecutionEvaluator implements AgentEvaluator {

    @Override
    public String getName() {
        return "tool-execution";
    }

    @Override
    public String getDescription() {
        return "评估工具调用的正确性和效率";
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext context) {
        AgentTrace trace = context.trace();

        // 从 Trace 的 spans JSON 中提取工具调用信息
        // 由于 spans 在 AgentTrace 中存储为 JSON，我们需要从 trace 中获取关联的 span 记录
        List<Map<String, Object>> toolSpans =
                trace.getSpans().stream()
                        .filter(s -> "TOOL_EXECUTE".equals(s.get("type")))
                        .toList();

        if (toolSpans.isEmpty()) {
            return new EvaluationResult(
                    getName(), true, 1.0, Map.of("toolCallCount", 0), List.of(), "无工具调用");
        }

        // 统计成功/失败
        long successCount =
                toolSpans.stream().filter(s -> Boolean.TRUE.equals(s.get("success"))).count();
        long failureCount = toolSpans.size() - successCount;
        double successRate = (double) successCount / toolSpans.size();

        // 检查是否调用了预期工具
        Set<String> calledTools =
                toolSpans.stream()
                        .map(
                                s -> {
                                    String name = (String) s.get("name");
                                    return name != null ? name.replace("Tool: ", "") : "unknown";
                                })
                        .collect(Collectors.toSet());

        List<String> expectedTools =
                context.expectedTools() != null ? context.expectedTools() : List.of();
        Set<String> missingTools = new HashSet<>(expectedTools);
        missingTools.removeAll(calledTools);

        // 计算平均执行时间
        double avgExecutionTime =
                toolSpans.stream()
                        .filter(s -> s.get("durationMs") != null)
                        .mapToLong(s -> ((Number) s.get("durationMs")).longValue())
                        .average()
                        .orElse(0);

        // 计算得分
        double score =
                successRate * 0.5
                        + (missingTools.isEmpty() ? 0.3 : 0)
                        + (avgExecutionTime < 1000 ? 0.2 : 0.1);

        List<String> issues = new ArrayList<>();
        if (failureCount > 0) {
            issues.add(failureCount + " 次工具调用失败");
        }
        if (!missingTools.isEmpty()) {
            issues.add("缺少工具调用: " + missingTools);
        }

        return new EvaluationResult(
                getName(),
                successRate >= 0.8 && missingTools.isEmpty(),
                score,
                Map.of(
                        "toolCallCount", toolSpans.size(),
                        "successCount", successCount,
                        "failureCount", failureCount,
                        "successRate", successRate,
                        "avgExecutionTimeMs", avgExecutionTime,
                        "calledTools", calledTools),
                issues,
                generateRecommendation(issues, failureCount));
    }

    @Override
    public BatchEvaluationResult evaluateBatch(List<AgentTrace> traces) {
        List<EvaluationResult> results = new ArrayList<>();
        int passed = 0;
        int failed = 0;
        double totalSuccessRate = 0;
        long totalToolCalls = 0;

        for (AgentTrace trace : traces) {
            EvaluationResult result =
                    evaluate(new EvaluationContext(trace, null, null, Map.of(), "batch"));
            results.add(result);

            if (result.passed()) {
                passed++;
            } else {
                failed++;
            }

            totalSuccessRate += (double) result.details().get("successRate");
            totalToolCalls += (long) result.details().get("toolCallCount");
        }

        EvaluationMetrics metrics =
                EvaluationMetrics.builder()
                        .toolSuccessRate(traces.isEmpty() ? 0 : totalSuccessRate / traces.size())
                        .build();

        return new BatchEvaluationResult(
                UUID.randomUUID().toString(),
                traces.size(),
                passed,
                failed,
                metrics,
                results,
                Map.of(
                        "totalToolCalls",
                        totalToolCalls,
                        "avgToolSuccessRate",
                        traces.isEmpty() ? 0 : totalSuccessRate / traces.size()));
    }

    private String generateRecommendation(List<String> issues, long failureCount) {
        if (issues.isEmpty()) {
            return "工具调用表现良好";
        }
        if (failureCount > 0) {
            return "建议检查工具实现和参数验证";
        }
        return String.join("; ", issues);
    }
}
