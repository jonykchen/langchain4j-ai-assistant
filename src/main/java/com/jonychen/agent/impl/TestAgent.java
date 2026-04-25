package com.jonychen.agent.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jonychen.agent.core.*;
import com.jonychen.observability.trace.AgentTraceService;
import com.jonychen.tool.ToolCategory;
import com.jonychen.tool.ToolDefinition;
import com.jonychen.tool.ToolRegistry;
import com.jonychen.tool.ToolResult;
import com.jonychen.tool.builtin.TestGeneratorTools;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.tool.ToolExecutor;

/**
 * 测试生成 Agent
 *
 * <p>负责为业务代码生成单元测试、集成测试和 E2E 测试。
 *
 * <h2>核心能力</h2>
 *
 * <ul>
 *   <li><strong>单元测试生成</strong>：根据源代码生成 JUnit5/TestNG 测试
 *   <li><strong>集成测试生成</strong>：生成包含 Spring 上下文的集成测试
 *   <li><strong>测试执行</strong>：运行测试并分析结果
 *   <li><strong>覆盖率分析</strong>：查看测试覆盖率报告
 * </ul>
 *
 * <h2>权限要求</h2>
 *
 * <p>ADMIN 角色（仅管理员可用）
 *
 * @author jonychen
 */
public class TestAgent extends AbstractAgent {

    private static final Logger log = LoggerFactory.getLogger(TestAgent.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TestGeneratorTools testGeneratorTools;

    /**
     * 构造函数
     *
     * @param chatModel 聊天模型
     * @param toolRegistry 工具注册中心
     * @param traceService 追踪服务
     * @param testGeneratorTools 测试生成工具
     */
    public TestAgent(
            ChatModel chatModel,
            ToolRegistry toolRegistry,
            AgentTraceService traceService,
            TestGeneratorTools testGeneratorTools) {
        super(chatModel, toolRegistry, traceService);
        this.testGeneratorTools = testGeneratorTools;

        log.info(
                "[TestAgent] 初始化完成，可用工具: generate_unit_test, generate_integration_test, list_test_files, run_tests, get_test_coverage");
    }

    @Override
    public AgentMetadata getMetadata() {
        return AgentMetadata.test();
    }

    /**
     * 判断是否应该由此 Agent 处理请求
     *
     * <p>基于关键词置信度判断：
     *
     * <ul>
     *   <li>包含 "测试"/"test case"/"单元测试" → 0.9
     *   <li>包含 "覆盖率"/"coverage" → 0.85
     *   <li>包含 "e2e"/"集成测试" → 0.8
     * </ul>
     *
     * @param request 执行请求
     * @return 置信度分数（0-1）
     */
    @Override
    public double canHandle(AgentRequest request) {
        String input = request.userInput().toLowerCase();

        // 高置信度关键词
        if (input.contains("测试") || input.contains("test case") || input.contains("单元测试")) {
            log.debug("[TestAgent] canHandle 高置信度匹配: 测试/test case");
            return 0.9;
        }

        // 较高置信度关键词
        if (input.contains("覆盖率") || input.contains("coverage")) {
            log.debug("[TestAgent] canHandle 较高置信度匹配: 覆盖率");
            return 0.85;
        }

        // 一般置信度关键词
        if (input.contains("e2e") || input.contains("集成测试") || input.contains("junit")) {
            log.debug("[TestAgent] canHandle 一般置信度匹配: e2e/集成测试");
            return 0.8;
        }

        return 0.0;
    }

    @Override
    public List<ToolDefinition> getAvailableTools() {
        return toolRegistry.getToolsByCategory(ToolCategory.TEST);
    }

    @Override
    protected String buildSystemPrompt() {
        return """
                你是一个测试生成助手，帮助用户为 Java 代码生成高质量的测试用例。

                ## 可用工具

                ### 测试生成
                - generate_unit_test: 生成单元测试代码模板
                - generate_integration_test: 生成集成测试代码（包含 Spring 上下文）

                ### 测试执行
                - list_test_files: 列出测试文件
                - run_tests: 运行指定的测试
                - get_test_coverage: 获取测试覆盖率报告

                ## 工作流程

                1. **理解需求**：分析用户需要为哪个类/方法生成测试
                2. **查看现状**：使用 list_test_files 了解已有测试
                3. **生成测试**：使用 generate_unit_test 或 generate_integration_test
                4. **执行验证**：使用 run_tests 运行测试
                5. **检查覆盖率**：使用 get_test_coverage 查看覆盖率

                ## 测试编写规范

                - 使用 JUnit 5 + AssertJ 风格
                - 测试方法命名：should_预期结果_when_条件
                - 使用 @DisplayName 注解描述测试意图
                - 遵循 Given-When-Then 模式
                - 测试独立性：每个测试可独立运行
                - 边界条件：包含正常、异常、边界三种情况

                ## 测试分类

                - **单元测试**：测试单个类/方法，Mock 外部依赖
                - **集成测试**：测试组件交互，使用真实 Spring 上下文
                - **E2E 测试**：测试完整用户流程

                ## 输出格式

                - 在每个步骤前，用 <thinking></thinking> 标签说明思考过程
                - 生成的测试代码使用代码块展示
                - 提供覆盖率改进建议

                ## 错误处理

                - 如果源文件不存在，提示用户确认路径
                - 如果测试编译失败，分析原因并修复
                - 如果测试运行失败，分析失败原因
                """;
    }

    @Override
    protected AgentExecutor buildExecutor(AgentContext context) {
        List<dev.langchain4j.agent.tool.ToolSpecification> tools =
                List.of(
                        dev.langchain4j.agent.tool.ToolSpecification.builder()
                                .name("generate_unit_test")
                                .description("根据源代码生成单元测试代码模板")
                                .build(),
                        dev.langchain4j.agent.tool.ToolSpecification.builder()
                                .name("generate_integration_test")
                                .description("生成集成测试代码，包含 Spring 上下文配置")
                                .build(),
                        dev.langchain4j.agent.tool.ToolSpecification.builder()
                                .name("list_test_files")
                                .description("列出指定目录下的测试文件")
                                .build(),
                        dev.langchain4j.agent.tool.ToolSpecification.builder()
                                .name("run_tests")
                                .description("运行指定的测试或全部测试")
                                .build(),
                        dev.langchain4j.agent.tool.ToolSpecification.builder()
                                .name("get_test_coverage")
                                .description("获取测试覆盖率报告")
                                .build());

        Map<String, ToolExecutor> executors = registerToolExecutors();

        return new AgentExecutor(chatModel, buildSystemPrompt(), tools, executors);
    }

    private Map<String, ToolExecutor> registerToolExecutors() {
        Map<String, ToolExecutor> executors = new HashMap<>();

        executors.put(
                "generate_unit_test",
                (request, memoryId) -> {
                    Map<String, Object> params = parseArgs(request.arguments());
                    String sourceFilePath = (String) params.get("sourceFilePath");
                    String className = (String) params.get("className");
                    String testFramework = (String) params.getOrDefault("testFramework", "junit5");
                    log.info("[TestAgent] 执行 generate_unit_test: class={}", className);
                    ToolResult result =
                            testGeneratorTools.generateUnitTest(
                                    sourceFilePath, className, testFramework);
                    return resultToString(result);
                });

        executors.put(
                "generate_integration_test",
                (request, memoryId) -> {
                    Map<String, Object> params = parseArgs(request.arguments());
                    String className = (String) params.get("className");
                    String testType = (String) params.getOrDefault("testType", "service");
                    log.info("[TestAgent] 执行 generate_integration_test: class={}", className);
                    ToolResult result =
                            testGeneratorTools.generateIntegrationTest(className, testType);
                    return resultToString(result);
                });

        executors.put(
                "list_test_files",
                (request, memoryId) -> {
                    Map<String, Object> params = parseArgs(request.arguments());
                    String directory = (String) params.getOrDefault("directory", "src/test/java/");
                    log.info("[TestAgent] 执行 list_test_files: directory={}", directory);
                    ToolResult result = testGeneratorTools.listTestFiles(directory);
                    return resultToString(result);
                });

        executors.put(
                "run_tests",
                (request, memoryId) -> {
                    Map<String, Object> params = parseArgs(request.arguments());
                    String testClass = (String) params.get("testClass");
                    String testMethod = (String) params.get("testMethod");
                    log.info("[TestAgent] 执行 run_tests: class={}", testClass);
                    ToolResult result = testGeneratorTools.runTests(testClass, testMethod);
                    return resultToString(result);
                });

        executors.put(
                "get_test_coverage",
                (request, memoryId) -> {
                    Map<String, Object> params = parseArgs(request.arguments());
                    String packageName = (String) params.get("packageName");
                    log.info("[TestAgent] 执行 get_test_coverage: package={}", packageName);
                    ToolResult result = testGeneratorTools.getTestCoverage(packageName);
                    return resultToString(result);
                });

        return executors;
    }

    // ==================== 辅助方法 ====================

    private Map<String, Object> parseArgs(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(argumentsJson, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[TestAgent] JSON 解析失败: {}", e.getMessage());
            return Map.of();
        }
    }

    private String resultToString(ToolResult result) {
        if (result.success() && result.data() != null) {
            try {
                return MAPPER.writeValueAsString(result.data());
            } catch (Exception e) {
                return String.valueOf(result.data());
            }
        }
        return result.error() != null ? "Error: " + result.error() : "Success";
    }
}
