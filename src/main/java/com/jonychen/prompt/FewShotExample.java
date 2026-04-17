package com.jonychen.prompt;

import java.util.Map;

/**
 * Few-shot 示例
 * 用于提供输入输出的示例，帮助模型理解任务
 */
public record FewShotExample(
    /**
     * 输入示例
     */
    Map<String, Object> input,

    /**
     * 输出示例
     */
    String output,

    /**
     * 解释说明
     */
    String explanation
) {
    /**
     * 创建简单示例
     */
    public static FewShotExample of(String input, String output) {
        return new FewShotExample(Map.of("query", input), output, null);
    }

    /**
     * 创建带解释的示例
     */
    public static FewShotExample withExplanation(String input, String output, String explanation) {
        return new FewShotExample(Map.of("query", input), output, explanation);
    }
}