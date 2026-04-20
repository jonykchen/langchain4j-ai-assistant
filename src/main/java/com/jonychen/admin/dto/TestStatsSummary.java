package com.jonychen.admin.dto;

/**
 * 测试统计摘要
 */
public record TestStatsSummary(
    int totalTests,
    int passed,
    int failed,
    int running,
    double passRate,
    long avgResponseTime
) {}
