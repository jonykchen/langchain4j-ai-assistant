package com.jonychen.planning;

import java.util.List;

/**
 * 步骤执行结果
 *
 * @param success 是否成功
 * @param output 输出数据
 * @param error 错误信息
 * @param logs 执行日志
 * @param observation 观察结果（用于 ReAct）
 * @author jonychen
 */
public record StepResult(
        boolean success, Object output, String error, List<String> logs, String observation) {
    /** 创建成功结果 */
    public static StepResult success(Object output) {
        return new StepResult(true, output, null, List.of(), null);
    }

    /** 创建成功结果（带日志） */
    public static StepResult success(Object output, List<String> logs) {
        return new StepResult(true, output, null, logs, null);
    }

    /** 创建失败结果 */
    public static StepResult failure(String error) {
        return new StepResult(false, null, error, List.of(), null);
    }

    /** 创建失败结果（带异常） */
    public static StepResult failure(String error, Exception e) {
        String message = error + ": " + e.getMessage();
        return new StepResult(false, null, message, List.of(), null);
    }

    /** 创建带观察的结果（ReAct 用） */
    public static StepResult withObservation(Object output, String observation) {
        return new StepResult(true, output, null, List.of(), observation);
    }

    /** 添加日志 */
    public StepResult addLog(String log) {
        List<String> newLogs = new java.util.ArrayList<>(logs);
        newLogs.add(log);
        return new StepResult(success, output, error, newLogs, observation);
    }
}
