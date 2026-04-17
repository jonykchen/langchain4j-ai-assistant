package com.jonychen.planning.agent;

import java.util.List;

/**
 * ReAct 执行结果
 *
 * @param success    是否成功
 * @param answer     最终答案
 * @param steps      执行步骤
 * @param iterations 迭代次数
 * @author jonychen
 */
public record ReActResult(
        boolean success,
        String answer,
        List<ReActStep> steps,
        int iterations
) {
    /**
     * 创建成功结果
     */
    public static ReActResult success(String answer, List<ReActStep> steps, int iterations) {
        return new ReActResult(true, answer, steps, iterations);
    }

    /**
     * 创建失败结果
     */
    public static ReActResult failure(String reason, List<ReActStep> steps, int iterations) {
        return new ReActResult(false, reason, steps, iterations);
    }

    /**
     * 获取总思考次数
     */
    public int totalThoughts() {
        return (int) steps.stream().filter(s -> s.thought() != null).count();
    }

    /**
     * 获取总行动次数
     */
    public int totalActions() {
        return (int) steps.stream().filter(ReActStep::hasAction).count();
    }
}