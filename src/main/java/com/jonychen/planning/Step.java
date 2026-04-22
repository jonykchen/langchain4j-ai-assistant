package com.jonychen.planning;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 执行步骤
 *
 * @param stepId 步骤ID
 * @param order 执行顺序
 * @param description 步骤描述
 * @param action 具体行动
 * @param tool 工具名称（可选）
 * @param params 执行参数
 * @param status 步骤状态
 * @param result 执行结果
 * @param dependsOn 依赖的步骤ID
 * @param createdAt 创建时间
 * @param startedAt 开始时间
 * @param completedAt 完成时间
 * @author jonychen
 */
public record Step(
        String stepId,
        int order,
        String description,
        String action,
        String tool,
        Map<String, Object> params,
        StepStatus status,
        StepResult result,
        String dependsOn,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt) {
    /** 创建新步骤 */
    public static Step create(int order, String description, String action) {
        return new Step(
                UUID.randomUUID().toString(),
                order,
                description,
                action,
                null,
                Map.of(),
                StepStatus.PENDING,
                null,
                null,
                LocalDateTime.now(),
                null,
                null);
    }

    /** 创建带工具的步骤 */
    public static Step createWithTool(
            int order, String description, String tool, Map<String, Object> params) {
        return new Step(
                UUID.randomUUID().toString(),
                order,
                description,
                "使用工具：" + tool,
                tool,
                params,
                StepStatus.PENDING,
                null,
                null,
                LocalDateTime.now(),
                null,
                null);
    }

    /** 更新状态 */
    public Step withStatus(StepStatus newStatus) {
        return new Step(
                stepId,
                order,
                description,
                action,
                tool,
                params,
                newStatus,
                result,
                dependsOn,
                createdAt,
                newStatus == StepStatus.RUNNING ? LocalDateTime.now() : startedAt,
                newStatus.isTerminal() ? LocalDateTime.now() : completedAt);
    }

    /** 更新结果 */
    public Step withResult(StepResult newResult) {
        return new Step(
                stepId,
                order,
                description,
                action,
                tool,
                params,
                status,
                newResult,
                dependsOn,
                createdAt,
                startedAt,
                completedAt);
    }

    /** 设置依赖 */
    public Step withDependsOn(String stepId) {
        return new Step(
                this.stepId,
                order,
                description,
                action,
                tool,
                params,
                status,
                result,
                stepId,
                createdAt,
                startedAt,
                completedAt);
    }

    /** 是否可以执行（依赖已完成） */
    public boolean canExecute(List<Step> allSteps) {
        if (dependsOn == null) {
            return true;
        }

        return allSteps.stream()
                .filter(s -> s.stepId().equals(dependsOn))
                .findFirst()
                .map(s -> s.status() == StepStatus.COMPLETED)
                .orElse(true);
    }

    /** 获取执行时长（毫秒） */
    public long executionTimeMs() {
        if (startedAt == null) {
            return 0;
        }
        LocalDateTime end = completedAt != null ? completedAt : LocalDateTime.now();
        return java.time.Duration.between(startedAt, end).toMillis();
    }
}
