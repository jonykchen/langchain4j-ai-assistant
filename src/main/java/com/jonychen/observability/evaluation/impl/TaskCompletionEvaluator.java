package com.jonychen.observability.evaluation.impl;

import com.jonychen.observability.evaluation.AgentEvaluator;
import com.jonychen.observability.evaluation.EvaluationMetrics;
import com.jonychen.observability.trace.AgentTrace;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 任务完成度评测器
 *
 * @author jonychen
 */
@Slf4j
@Component
public class TaskCompletionEvaluator implements AgentEvaluator {

    @Override
    public String getName() {
        return "task-completion";
    }

    @Override
    public String getDescription() {
        return "评估 Agent 是否成功完成任务目标";
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext context) {
        AgentTrace trace = context.trace();

        // 基本完成度检查
        boolean statusSuccess = "COMPLETED".equals(trace.getStatus());

        // 输出存在性检查
        boolean hasOutput = trace.getFinalOutput() != null && !trace.getFinalOutput().isEmpty();

        // 预期输出匹配（如果提供）
        boolean outputMatches = true;
        if (context.expectedOutput() != null) {
            outputMatches = trace.getFinalOutput() != null &&
                    trace.getFinalOutput().toLowerCase().contains(context.expectedOutput().toLowerCase());
        }

        // 计算得分
        double score = 0;
        if (statusSuccess) score += 0.4;
        if (hasOutput) score += 0.3;
        if (outputMatches) score += 0.3;

        // 收集问题
        List<String> issues = new ArrayList<>();
        if (!statusSuccess) issues.add("任务状态非 COMPLETED: " + trace.getStatus());
        if (!hasOutput) issues.add("缺少最终输出");
        if (!outputMatches && context.expectedOutput() != null) {
            issues.add("输出与预期不匹配");
        }

        return new EvaluationResult(
                getName(),
                score >= 0.7,
                score,
                Map.of(
                        "statusSuccess", statusSuccess,
                        "hasOutput", hasOutput,
                        "outputMatches", outputMatches
                ),
                issues,
                generateRecommendation(issues)
        );
    }

    @Override
    public BatchEvaluationResult evaluateBatch(List<AgentTrace> traces) {
        List<EvaluationResult> results = new ArrayList<>();
        int passed = 0;
        int failed = 0;
        double totalScore = 0;

        for (AgentTrace trace : traces) {
            EvaluationResult result = evaluate(new EvaluationContext(trace, null, null, Map.of(), "batch"));
            results.add(result);
            totalScore += result.score();
            if (result.passed()) passed++;
            else failed++;
        }

        EvaluationMetrics metrics = EvaluationMetrics.builder()
                .taskCompletionRate(traces.isEmpty() ? 0 : (double) passed / traces.size())
                .errorRate(traces.isEmpty() ? 0 : (double) failed / traces.size())
                .build();

        return new BatchEvaluationResult(
                UUID.randomUUID().toString(),
                traces.size(),
                passed,
                failed,
                metrics,
                results,
                Map.of(
                        "avgScore", traces.isEmpty() ? 0 : totalScore / traces.size(),
                        "passRate", traces.isEmpty() ? 0 : (double) passed / traces.size()
                )
        );
    }

    private String generateRecommendation(List<String> issues) {
        if (issues.isEmpty()) return "任务执行良好";
        return "建议检查: " + String.join(", ", issues);
    }
}
