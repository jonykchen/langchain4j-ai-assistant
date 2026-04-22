package com.jonychen.model;

import java.time.LocalDateTime;

/**
 * 模型健康状态
 *
 * <p>记录单个模型的运行状态，用于健康检查和监控。
 */
public class ModelHealthStatus {

    private final String modelName;
    private volatile boolean healthy;
    private volatile String lastError;
    private volatile long successCount;
    private volatile long failureCount;
    private volatile LocalDateTime lastSuccessTime;
    private volatile LocalDateTime lastFailureTime;

    public ModelHealthStatus(String modelName, boolean healthy) {
        this.modelName = modelName;
        this.healthy = healthy;
        this.successCount = 0;
        this.failureCount = 0;
    }

    public String getModelName() {
        return modelName;
    }

    public boolean isHealthy() {
        return healthy;
    }

    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
        this.lastFailureTime = LocalDateTime.now();
    }

    public long getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(long successCount) {
        this.successCount = successCount;
        this.lastSuccessTime = LocalDateTime.now();
    }

    public long getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(long failureCount) {
        this.failureCount = failureCount;
    }

    public LocalDateTime getLastSuccessTime() {
        return lastSuccessTime;
    }

    public LocalDateTime getLastFailureTime() {
        return lastFailureTime;
    }

    @Override
    public String toString() {
        return "ModelHealthStatus{"
                + "modelName='"
                + modelName
                + '\''
                + ", healthy="
                + healthy
                + ", successCount="
                + successCount
                + ", failureCount="
                + failureCount
                + ", lastError='"
                + lastError
                + '\''
                + '}';
    }
}
