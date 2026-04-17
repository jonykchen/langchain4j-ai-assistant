package com.jonychen.planning;

import java.util.List;

/**
 * 任务执行结果
 *
 * @param taskId             任务ID
 * @param success            是否成功
 * @param finalOutput        最终输出
 * @param stepResults        步骤结果列表
 * @param error              错误信息
 * @param totalExecutionTimeMs 总执行时长（毫秒）
 * @param iterations         迭代次数（ReAct 用）
 * @author jonychen
 */
public record TaskResult(
        String taskId,
        boolean success,
        Object finalOutput,
        List<StepResult> stepResults,
        String error,
        long totalExecutionTimeMs,
        int iterations
) {
    /**
     * 创建成功结果
     */
    public static TaskResult success(String taskId, Object output, List<StepResult> stepResults, long executionTime) {
        return new TaskResult(taskId, true, output, stepResults, null, executionTime, stepResults.size());
    }

    /**
     * 创建失败结果
     */
    public static TaskResult failure(String taskId, String error, List<StepResult> stepResults, long executionTime) {
        return new TaskResult(taskId, false, null, stepResults, error, executionTime, stepResults.size());
    }

    /**
     * 创建带迭代次数的结果（ReAct 用）
     */
    public static TaskResult withIterations(String taskId, boolean success, Object output,
                                            List<StepResult> stepResults, String error,
                                            long executionTime, int iterations) {
        return new TaskResult(taskId, success, output, stepResults, error, executionTime, iterations);
    }

    /**
     * 获取成功的步骤数
     */
    public int getSuccessCount() {
        return (int) stepResults.stream().filter(r -> r.success()).count();
    }

    /**
     * 获取失败的步骤数
     */
    public int getFailureCount() {
        return (int) stepResults.stream().filter(r -> !r.success()).count();
    }
}