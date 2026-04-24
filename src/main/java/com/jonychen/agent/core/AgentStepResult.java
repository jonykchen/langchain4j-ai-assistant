package com.jonychen.agent.core;

/**
 * Agent 步骤执行结果
 *
 * <p>封装单步执行的结果状态，用于控制执行循环的流程。
 *
 * <p>三种状态：
 *
 * <ul>
 *   <li>done - 执行完成，返回最终输出
 *   <li>continue - 继续执行，将 nextInput 作为下一次输入
 *   <li>pending - 等待用户确认，需要用户批准后才能继续
 * </ul>
 *
 * @param done 是否完成
 * @param success 是否成功
 * @param output 输出（完成时有值）
 * @param nextInput 下一次输入（继续执行时有值）
 * @param confirmationId 确认 ID（需要确认时有值）
 * @param summary 步骤摘要
 * @author jonychen
 */
public record AgentStepResult(
        boolean done,
        boolean success,
        String output,
        String nextInput,
        String confirmationId,
        String summary) {

    /**
     * 创建完成结果
     *
     * @param output 最终输出
     * @return 完成结果
     */
    public static AgentStepResult done(String output) {
        return new AgentStepResult(true, true, output, null, null, "完成");
    }

    /**
     * 创建继续执行结果
     *
     * @param nextInput 下一次输入
     * @return 继续执行结果
     */
    public static AgentStepResult continueExecution(String nextInput) {
        return new AgentStepResult(false, true, null, nextInput, null, "继续执行");
    }

    /**
     * 创建等待确认结果
     *
     * @param confirmationId 确认 ID
     * @return 等待确认结果
     */
    public static AgentStepResult pendingConfirmation(String confirmationId) {
        return new AgentStepResult(false, false, null, null, confirmationId, "等待确认");
    }

    /**
     * 创建失败结果
     *
     * @param error 错误信息
     * @return 失败结果
     */
    public static AgentStepResult failed(String error) {
        return new AgentStepResult(true, false, null, null, null, "失败: " + error);
    }

    /**
     * 判断是否需要确认
     *
     * @return 是否需要确认
     */
    public boolean needsConfirmation() {
        return confirmationId != null;
    }
}
