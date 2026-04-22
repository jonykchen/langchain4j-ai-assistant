package com.jonychen.planning;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 任务定义
 *
 * @param taskId 任务ID
 * @param goal 任务目标
 * @param type 任务类型
 * @param context 上下文变量
 * @param status 任务状态
 * @param steps 执行步骤列表
 * @param createdAt 创建时间
 * @param completedAt 完成时间
 * @author jonychen
 */
public record Task(
        String taskId,
        String goal,
        TaskType type,
        Map<String, Object> context,
        TaskStatus status,
        List<Step> steps,
        LocalDateTime createdAt,
        LocalDateTime completedAt) {
    /** 创建新任务 */
    public static Task create(String goal) {
        TaskType type = determineTaskType(goal);
        return new Task(
                UUID.randomUUID().toString(),
                goal,
                type,
                Map.of(),
                TaskStatus.PENDING,
                List.of(),
                LocalDateTime.now(),
                null);
    }

    /** 创建带上下文的任务 */
    public static Task create(String goal, Map<String, Object> context) {
        TaskType type = determineTaskType(goal);
        return new Task(
                UUID.randomUUID().toString(),
                goal,
                type,
                context,
                TaskStatus.PENDING,
                List.of(),
                LocalDateTime.now(),
                null);
    }

    /** 更新状态 */
    public Task withStatus(TaskStatus newStatus) {
        return new Task(
                taskId,
                goal,
                type,
                context,
                newStatus,
                steps,
                createdAt,
                newStatus.isTerminal() ? LocalDateTime.now() : completedAt);
    }

    /** 设置步骤 */
    public Task withSteps(List<Step> newSteps) {
        return new Task(taskId, goal, type, context, status, newSteps, createdAt, completedAt);
    }

    /** 添加步骤 */
    public Task addStep(Step step) {
        List<Step> newSteps = new ArrayList<>(steps);
        newSteps.add(step);
        return new Task(taskId, goal, type, context, status, newSteps, createdAt, completedAt);
    }

    /** 更新步骤 */
    public Task updateStep(String stepId, Step updatedStep) {
        List<Step> newSteps =
                steps.stream().map(s -> s.stepId().equals(stepId) ? updatedStep : s).toList();
        return new Task(taskId, goal, type, context, status, newSteps, createdAt, completedAt);
    }

    /** 获取当前执行的步骤 */
    public Step getCurrentStep() {
        return steps.stream()
                .filter(s -> s.status() == StepStatus.RUNNING)
                .findFirst()
                .orElse(null);
    }

    /** 获取下一个待执行的步骤 */
    public Step getNextPendingStep() {
        return steps.stream()
                .filter(s -> s.status() == StepStatus.PENDING)
                .filter(s -> s.canExecute(steps))
                .findFirst()
                .orElse(null);
    }

    /** 获取已完成的步骤数 */
    public int getCompletedStepCount() {
        return (int) steps.stream().filter(s -> s.status() == StepStatus.COMPLETED).count();
    }

    /** 获取进度百分比 */
    public int getProgressPercentage() {
        if (steps.isEmpty()) {
            return 0;
        }
        return (getCompletedStepCount() * 100) / steps.size();
    }

    /** 判断任务类型 */
    private static TaskType determineTaskType(String goal) {
        if (goal == null) {
            return TaskType.SIMPLE;
        }

        String lowerGoal = goal.toLowerCase();

        // 复杂任务关键词
        if (lowerGoal.contains("分析")
                || lowerGoal.contains("比较")
                || lowerGoal.contains("总结")
                || lowerGoal.contains("规划")) {
            return TaskType.COMPLEX;
        }

        // 多步骤关键词
        if (lowerGoal.contains("然后")
                || lowerGoal.contains("接着")
                || lowerGoal.contains("之后")
                || lowerGoal.contains("步骤")) {
            return TaskType.MULTI_STEP;
        }

        return TaskType.SIMPLE;
    }

    /** 获取总执行时长（毫秒） */
    public long totalExecutionTimeMs() {
        return steps.stream().mapToLong(Step::executionTimeMs).sum();
    }
}
