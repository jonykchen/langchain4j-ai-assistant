package com.jonychen.tool.structured.model;

import java.util.List;

/**
 * 用户意图识别结果
 *
 * @param intent 意图类型
 * @param confidence 确信度（0-1）
 * @param entities 提取的实体
 * @param subIntents 子意图列表
 * @param requiresTool 是否需要工具调用
 * @param suggestedTool 建议使用的工具
 * @param responseStrategy 响应策略
 * @author jonychen
 */
public record UserIntent(
        String intent,
        double confidence,
        List<Entity> entities,
        List<String> subIntents,
        boolean requiresTool,
        String suggestedTool,
        String responseStrategy) {
    /** 提取的实体 */
    public record Entity(
            String type, String value, int startIndex, int endIndex, double confidence) {}

    /** 意图类型枚举 */
    public enum IntentType {
        QUESTION("question", "用户提问"),
        COMMAND("command", "执行命令"),
        SEARCH("search", "搜索信息"),
        CALCULATION("calculation", "计算"),
        CONVERSATION("conversation", "日常对话"),
        UNKNOWN("unknown", "未知意图");

        private final String value;
        private final String description;

        IntentType(String value, String description) {
            this.value = value;
            this.description = description;
        }

        public String getValue() {
            return value;
        }

        public String getDescription() {
            return description;
        }
    }

    /** 创建默认意图 */
    public static UserIntent unknown() {
        return new UserIntent(
                IntentType.UNKNOWN.getValue(),
                0.0,
                List.of(),
                List.of(),
                false,
                null,
                "direct_response");
    }

    /** 创建问题意图 */
    public static UserIntent question(double confidence) {
        return new UserIntent(
                IntentType.QUESTION.getValue(),
                confidence,
                List.of(),
                List.of(),
                false,
                null,
                "informational_response");
    }

    /** 创建需要工具的意图 */
    public static UserIntent withTool(String intent, String toolName, double confidence) {
        return new UserIntent(
                intent, confidence, List.of(), List.of(), true, toolName, "tool_execution");
    }
}
