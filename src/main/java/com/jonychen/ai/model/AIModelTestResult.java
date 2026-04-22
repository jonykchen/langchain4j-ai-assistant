package com.jonychen.ai.model;

import java.util.List;

/**
 * AI 模型测试结果
 *
 * @param testCaseId 测试用例 ID
 * @param testName 测试名称
 * @param category 分类
 * @param input 输入
 * @param actualOutput 实际输出
 * @param responseTime 响应时间（毫秒）
 * @param passed 是否通过
 * @param score 得分（0-1）
 * @param assertionResult 断言结果
 * @param errorMessage 错误信息
 */
public record AIModelTestResult(
        String testCaseId,
        String testName,
        String category,
        String input,
        String actualOutput,
        long responseTime,
        boolean passed,
        double score,
        AssertionResult assertionResult,
        String errorMessage) {
    /**
     * 断言结果
     *
     * @param passed 通过的断言列表
     * @param failed 失败的断言列表
     * @param score 得分
     */
    public record AssertionResult(
            List<String> passed, List<AssertionFailure> failed, double score) {}

    /**
     * 断言失败详情
     *
     * @param description 断言描述
     * @param expected 期望值
     * @param actual 实际值
     */
    public record AssertionFailure(String description, String expected, String actual) {}
}
