package com.jonychen.ai.runner;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.jonychen.ai.evaluator.ResponseQualityEvaluator;
import com.jonychen.ai.model.AIModelTestCase;
import com.jonychen.ai.model.AIModelTestResult;
import com.jonychen.service.AiService;

/** AI 模型测试运行器 */
@SpringBootTest
@ActiveProfiles("ai-test")
public class AIModelTestRunner {

    @Autowired private AiService aiService;

    @Autowired private ResponseQualityEvaluator qualityEvaluator;

    /** 测试用例数据源 */
    static Stream<AIModelTestCase> testCases() {
        return Stream.of(
                // 基础问答测试
                new AIModelTestCase(
                        "tc-001",
                        "简单问候",
                        "basic",
                        "你好",
                        List.of(
                                new AIModelTestCase.Assertion("CONTAINS", "包含问候语", "你好", 0.0),
                                new AIModelTestCase.Assertion(
                                        "RESPONSE_TIME_MS", "响应时间在5秒内", "5000", 0.0)),
                        Map.of("difficulty", "easy")),
                new AIModelTestCase(
                        "tc-002",
                        "自我介绍",
                        "basic",
                        "请介绍一下你自己",
                        List.of(
                                new AIModelTestCase.Assertion("CONTAINS", "包含 AI 助手介绍", "助手", 0.0),
                                new AIModelTestCase.Assertion(
                                        "LENGTH_MIN", "回复长度至少10字符", "10", 0.0)),
                        Map.of("difficulty", "easy")),

                // 代码生成测试
                new AIModelTestCase(
                        "tc-003",
                        "生成排序算法",
                        "code-generation",
                        "用 Java 实现冒泡排序",
                        List.of(
                                new AIModelTestCase.Assertion(
                                        "CONTAINS", "包含 Java 代码块", "```java", 0.0),
                                new AIModelTestCase.Assertion("CONTAINS", "包含排序逻辑", "for", 0.0),
                                new AIModelTestCase.Assertion(
                                        "MATCHES_REGEX", "包含方法定义", "public.*void.*sort", 0.0)),
                        Map.of("difficulty", "medium")),
                new AIModelTestCase(
                        "tc-004",
                        "生成 HTTP 服务",
                        "code-generation",
                        "用 Python 实现一个简单的 HTTP 服务器",
                        List.of(
                                new AIModelTestCase.Assertion(
                                        "CONTAINS", "包含 Python 代码块", "```python", 0.0),
                                new AIModelTestCase.Assertion(
                                        "MATCHES_REGEX", "包含 HTTP 相关代码", "http|HTTP|socket", 0.0)),
                        Map.of("difficulty", "medium")),

                // 知识问答测试
                new AIModelTestCase(
                        "tc-005",
                        "解释 Spring Boot",
                        "knowledge",
                        "解释一下 Spring Boot 的特性",
                        List.of(
                                new AIModelTestCase.Assertion("CONTAINS", "提到自动配置", "自动配置", 0.0),
                                new AIModelTestCase.Assertion("CONTAINS", "提到起步依赖", "starter", 0.0),
                                new AIModelTestCase.Assertion(
                                        "LENGTH_MIN", "回复长度至少50字符", "50", 0.0)),
                        Map.of("difficulty", "medium")),

                // 多轮对话测试
                new AIModelTestCase(
                        "tc-006",
                        "连续提问",
                        "conversation",
                        "什么是微服务？它有什么优点？",
                        List.of(
                                new AIModelTestCase.Assertion("CONTAINS", "提到微服务", "微服务", 0.0),
                                new AIModelTestCase.Assertion("CONTAINS", "提到优点或特点", "优点", 0.0),
                                new AIModelTestCase.Assertion(
                                        "LENGTH_MIN", "回复长度至少30字符", "30", 0.0)),
                        Map.of("difficulty", "medium")));
    }

    @ParameterizedTest
    @MethodSource("testCases")
    @DisplayName("AI 模型测试")
    void runAiModelTest(AIModelTestCase testCase) {
        long startTime = System.currentTimeMillis();
        String response;
        String errorMessage = null;

        try {
            response = aiService.chat(testCase.input());
        } catch (Exception e) {
            response = "";
            errorMessage = e.getMessage();
        }

        long responseTime = System.currentTimeMillis() - startTime;

        AIModelTestResult.AssertionResult result =
                qualityEvaluator.evaluate(testCase, response, responseTime);

        // 构建测试结果
        AIModelTestResult testResult =
                new AIModelTestResult(
                        testCase.id(),
                        testCase.name(),
                        testCase.category(),
                        testCase.input(),
                        response,
                        responseTime,
                        errorMessage == null && result.score() >= 0.8,
                        result.score(),
                        result,
                        errorMessage);

        // 输出测试结果
        System.out.println("========================================");
        System.out.println("Test: " + testCase.name());
        System.out.println("Input: " + testCase.input());
        System.out.println("Response: " + truncate(response, 200));
        System.out.println("Response Time: " + responseTime + "ms");
        System.out.println("Passed assertions: " + result.passed());
        System.out.println("Failed assertions: " + result.failed());
        System.out.println("Score: " + result.score());
        if (errorMessage != null) {
            System.out.println("Error: " + errorMessage);
        }
        System.out.println("========================================");

        // 断言至少 80% 通过率
        assert result.score() >= 0.8
                : "Test failed with score: "
                        + result.score()
                        + ", failed assertions: "
                        + result.failed();
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "null";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}
