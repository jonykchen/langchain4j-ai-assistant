package com.jonychen.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 测试结果对比 DTO
 *
 * @author jonychen
 */
public record TestComparisonResult(
        TestJobInfo job1,
        TestJobInfo job2,
        String testType,
        List<ResultDiff> diffs,
        ComparisonSummary summary) {

    /** 测试任务信息 */
    public record TestJobInfo(
            String jobId, String testType, LocalDateTime startTime, String status) {}

    /** 结果差异 */
    public record ResultDiff(
            String testName,
            String field,
            Object value1,
            Object value2,
            boolean changed,
            String changeDirection // improved, degraded, unchanged
            ) {}

    /** 对比摘要 */
    public record ComparisonSummary(int totalTests, int improved, int degraded, int unchanged) {}
}
