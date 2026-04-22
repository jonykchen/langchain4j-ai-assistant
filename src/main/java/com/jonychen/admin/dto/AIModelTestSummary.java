package com.jonychen.admin.dto;

import java.util.List;

/** AI 模型测试结果摘要 */
public record AIModelTestSummary(
        String testCaseId,
        String testName,
        String category,
        double score,
        boolean passed,
        List<AssertionDetail> details,
        long responseTime,
        String actualOutput) {
    public record AssertionDetail(
            String assertion, boolean passed, String expected, String actual) {}
}
