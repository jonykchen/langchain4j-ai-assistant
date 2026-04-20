package com.jonychen.admin.dto;

import java.time.Instant;
import java.util.List;

/**
 * 测试结果摘要
 */
public record TestResultSummary(
    String testName,
    String status,       // passed, failed, running, pending
    long duration,
    Assertions assertions,
    String error,
    Instant timestamp
) {
    public record Assertions(
        int passed,
        int failed,
        int total
    ) {}
}
