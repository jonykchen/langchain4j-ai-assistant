package com.jonychen.admin.dto;

/**
 * 性能测试结果摘要
 */
public record PerformanceResultSummary(
    String simulation,
    long requests,
    double successRate,
    long avgResponseTime,
    long maxResponseTime,
    long p95ResponseTime,
    long p99ResponseTime,
    long startTime,
    long endTime
) {}