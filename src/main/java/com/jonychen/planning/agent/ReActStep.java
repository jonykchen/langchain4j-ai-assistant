package com.jonychen.planning.agent;

/**
 * ReAct 步骤
 *
 * @param thought     思考内容
 * @param action      行动（工具名称）
 * @param actionInput 行动输入参数
 * @param observation 观察结果
 * @param finalAnswer 最终答案
 * @param rawResponse 原始响应
 * @author jonychen
 */
public record ReActStep(
        String thought,
        String action,
        String actionInput,
        String observation,
        String finalAnswer,
        String rawResponse
) {
    /**
     * 是否是最终答案
     */
    public boolean isFinalAnswer() {
        return finalAnswer != null && !finalAnswer.isBlank();
    }

    /**
     * 是否有行动
     */
    public boolean hasAction() {
        return action != null && !action.isBlank();
    }

    /**
     * 创建思考步骤
     */
    public static ReActStep thought(String thought) {
        return new ReActStep(thought, null, null, null, null, null);
    }

    /**
     * 创建行动步骤
     */
    public static ReActStep action(String thought, String action, String actionInput) {
        return new ReActStep(thought, action, actionInput, null, null, null);
    }

    /**
     * 设置观察结果
     */
    public ReActStep withObservation(String obs) {
        return new ReActStep(thought, action, actionInput, obs, finalAnswer, rawResponse);
    }

    /**
     * 创建最终答案步骤
     */
    public static ReActStep finalAnswer(String thought, String answer) {
        return new ReActStep(thought, null, null, null, answer, null);
    }
}