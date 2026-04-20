package com.jonychen.admin.dto;

import java.time.Instant;

/**
 * 测试任务状态
 */
public record TestJobStatus(
    String jobId,
    String status,       // running, completed, failed, cancelled, not_found
    long startTime,
    long endTime,
    String message,
    int progress         // 0-100
) {
    public boolean isRunning() {
        return "running".equals(status);
    }

    public boolean isCompleted() {
        return "completed".equals(status);
    }

    public long getDuration() {
        if (endTime > 0) {
            return endTime - startTime;
        }
        return System.currentTimeMillis() - startTime;
    }
}