package com.jonychen.prompt;

import java.time.LocalDateTime;
import java.util.List;

/** Prompt 模板元数据 */
public record PromptMetadata(
        /** 作者 */
        String author,

        /** 创建时间 */
        LocalDateTime createdAt,

        /** 最后更新时间 */
        LocalDateTime updatedAt,

        /** 标签列表 */
        List<String> tags,

        /** 推荐使用的模型 */
        String modelRecommendation,

        /** 最大 Token 数 */
        int maxTokens,

        /** 推荐温度参数 */
        double temperature) {
    /** 创建默认元数据 */
    public static PromptMetadata defaultMeta() {
        return new PromptMetadata(
                "AI Team",
                LocalDateTime.now(),
                LocalDateTime.now(),
                List.of("general"),
                "qwen-plus",
                4000,
                0.7);
    }
}
