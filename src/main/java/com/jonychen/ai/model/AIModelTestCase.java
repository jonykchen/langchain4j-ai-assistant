package com.jonychen.ai.model;

import java.util.List;
import java.util.Map;

/**
 * AI 模型测试用例
 *
 * @param id         测试用例 ID
 * @param name       测试名称
 * @param category   分类（basic/code-generation/tool-calling/conversation）
 * @param input      输入消息
 * @param assertions 断言列表
 * @param metadata   元数据
 */
public record AIModelTestCase(
    String id,
    String name,
    String category,
    String input,
    List<Assertion> assertions,
    Map<String, Object> metadata
) {
    /**
     * 断言定义
     *
     * @param type        断言类型
     * @param description 断言描述
     * @param expected    期望值
     * @param threshold   阈值（用于相似度等）
     */
    public record Assertion(
        String type,
        String description,
        Object expected,
        double threshold
    ) {}

    /**
     * 断言类型枚举
     */
    public enum AssertionType {
        CONTAINS,            // 包含指定文本
        NOT_CONTAINS,        // 不包含指定文本
        MATCHES_REGEX,       // 正则匹配
        JSON_VALID,          // 有效 JSON
        JSON_PATH,           // JSON 路径匹配
        SEMANTIC_SIMILARITY, // 语义相似度
        TOOL_CALLED,         // 正确调用工具
        TOOL_PARAMS_VALID,   // 工具参数有效
        RESPONSE_TIME_MS,    // 响应时间
        NO_HALLUCINATION     // 无幻觉
    }
}
