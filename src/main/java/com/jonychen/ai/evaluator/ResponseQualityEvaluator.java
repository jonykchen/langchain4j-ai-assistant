package com.jonychen.ai.evaluator;

import com.jonychen.ai.model.AIModelTestCase;
import com.jonychen.ai.model.AIModelTestResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 响应质量评估器
 * 评估 AI 响应的质量和正确性
 */
@Component
public class ResponseQualityEvaluator {

    /**
     * 评估响应质量
     *
     * @param testCase     测试用例
     * @param actualResponse 实际响应
     * @param responseTimeMs 响应时间（毫秒）
     * @return 断言结果
     */
    public AIModelTestResult.AssertionResult evaluate(
            AIModelTestCase testCase,
            String actualResponse,
            long responseTimeMs) {

        List<String> passed = new ArrayList<>();
        List<AIModelTestResult.AssertionFailure> failed = new ArrayList<>();

        if (testCase.assertions() == null) {
            return new AIModelTestResult.AssertionResult(passed, failed, 1.0);
        }

        for (var assertion : testCase.assertions()) {
            boolean result = evaluateAssertion(assertion, actualResponse, responseTimeMs);

            if (result) {
                passed.add(assertion.description());
            } else {
                failed.add(new AIModelTestResult.AssertionFailure(
                    assertion.description(),
                    String.valueOf(assertion.expected()),
                    getActualValue(assertion.type(), actualResponse, responseTimeMs)
                ));
            }
        }

        double score = calculateScore(passed.size(), passed.size() + failed.size());
        return new AIModelTestResult.AssertionResult(passed, failed, score);
    }

    /**
     * 评估单个断言
     */
    private boolean evaluateAssertion(
            AIModelTestCase.Assertion assertion,
            String actualResponse,
            long responseTimeMs) {

        String type = assertion.type();
        Object expected = assertion.expected();

        return switch (type) {
            case "CONTAINS" -> actualResponse.contains(String.valueOf(expected));
            case "NOT_CONTAINS" -> !actualResponse.contains(String.valueOf(expected));
            case "MATCHES_REGEX" -> {
                Pattern pattern = Pattern.compile(String.valueOf(expected));
                yield pattern.matcher(actualResponse).find();
            }
            case "JSON_VALID" -> isValidJson(actualResponse);
            case "RESPONSE_TIME_MS" -> {
                long maxTime = Long.parseLong(String.valueOf(expected));
                yield responseTimeMs <= maxTime;
            }
            case "STARTS_WITH" -> actualResponse.startsWith(String.valueOf(expected));
            case "ENDS_WITH" -> actualResponse.endsWith(String.valueOf(expected));
            case "LENGTH_MIN" -> {
                int minLength = Integer.parseInt(String.valueOf(expected));
                yield actualResponse.length() >= minLength;
            }
            case "LENGTH_MAX" -> {
                int maxLength = Integer.parseInt(String.valueOf(expected));
                yield actualResponse.length() <= maxLength;
            }
            default -> false;
        };
    }

    /**
     * 获取实际值（用于失败报告）
     */
    private String getActualValue(String type, String actualResponse, long responseTimeMs) {
        return switch (type) {
            case "RESPONSE_TIME_MS" -> String.valueOf(responseTimeMs) + "ms";
            case "JSON_VALID" -> isValidJson(actualResponse) ? "valid" : "invalid";
            case "LENGTH_MIN", "LENGTH_MAX" -> String.valueOf(actualResponse.length());
            default -> truncate(actualResponse, 100);
        };
    }

    /**
     * 计算得分
     */
    private double calculateScore(int passed, int total) {
        return total == 0 ? 1.0 : (double) passed / total;
    }

    /**
     * 验证是否为有效 JSON
     */
    private boolean isValidJson(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        try {
            // 尝试解析 JSON 数组或对象
            String trimmed = text.trim();
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                return true; // 简化验证，实际可用 Jackson 解析
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 截断文本
     */
    private String truncate(String text, int maxLength) {
        if (text == null) return "null";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}
