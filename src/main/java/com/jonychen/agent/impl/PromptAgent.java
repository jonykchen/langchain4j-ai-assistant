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
import com.jonychen.observability.trace.TraceContext;
import com.jonychen.tool.ToolCategory;
import com.jonychen.tool.ToolDefinition;
import com.jonychen.tool.ToolRegistry;
import com.jonychen.tool.ToolResult;
import com.jonychen.tool.builtin.PromptTools;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.tool.ToolExecutor;

/**
 * Prompt 工程 Agent
 *
 * <p>负责 Prompt 模板管理、优化和 A/B 测试配置。
 *
 * <h2>核心能力</h2>
 *
 * <ul>
 *   <li><strong>版本管理</strong>：查看模板、创建新版本、激活版本、回滚
 *   <li><strong>效果优化</strong>：分析模板使用统计，优化 Prompt 效果
 *   <li><strong>A/B 测试</strong>：配置和停止 A/B 测试，对比效果
 * </ul>
 *
 * <h2>权限要求</h2>
 *
 * <p>ADMIN 角色（仅管理员可用）
 *
 * @author jonychen
 */
public class PromptAgent extends AbstractAgent {

    private static final Logger log = LoggerFactory.getLogger(PromptAgent.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PromptTools promptTools;

    /**
     * 构造函数
     *
     * @param chatModel 聊天模型
     * @param toolRegistry 工具注册中心
     * @param traceService 追踪服务
     * @param traceContext 追踪上下文
     * @param promptTools Prompt 工具
     */
    public PromptAgent(
            ChatModel chatModel,
            ToolRegistry toolRegistry,
            AgentTraceService traceService,
            TraceContext traceContext,
            PromptTools promptTools) {
        super(chatModel, toolRegistry, traceService, traceContext);
        this.promptTools = promptTools;

        log.info(
                "[PromptAgent] 初始化完成，可用工具: list_prompt_templates, get_prompt_template, get_prompt_versions, create_prompt_version, compare_prompt_versions, activate_prompt_version, rollback_prompt_version, configure_ab_test, stop_ab_test");
    }

    @Override
    public AgentMetadata getMetadata() {
        return AgentMetadata.prompt();
    }

    /**
     * 判断是否应该由此 Agent 处理请求
     *
     * <p>基于关键词置信度判断：
     *
     * <ul>
     *   <li>包含 "prompt"/"模板"/"版本" → 0.9
     *   <li>包含 "A/B 测试"/"ab test" → 0.85
     *   <li>包含 "优化"/"效果" → 0.6
     * </ul>
     *
     * @param request 执行请求
     * @return 置信度分数（0-1）
     */
    @Override
    public double canHandle(AgentRequest request) {
        String input = request.userInput().toLowerCase();

        // 高置信度关键词
        if (input.contains("prompt") || input.contains("模板") || input.contains("版本")) {
            log.debug("[PromptAgent] canHandle 高置信度匹配: prompt/模板/版本");
            return 0.9;
        }

        // 较高置信度关键词
        if (input.contains("a/b") || input.contains("ab test") || input.contains("ab测试")) {
            log.debug("[PromptAgent] canHandle 较高置信度匹配: A/B 测试");
            return 0.85;
        }

        // 一般置信度关键词
        if (input.contains("优化") || input.contains("效果") || input.contains("回滚")) {
            log.debug("[PromptAgent] canHandle 一般置信度匹配: 优化/效果/回滚");
            return 0.6;
        }

        return 0.0;
    }

    @Override
    public List<ToolDefinition> getAvailableTools() {
        return toolRegistry.getToolsByCategory(ToolCategory.PROMPT);
    }

    /**
     * 构建系统提示词
     *
     * <p>指导 AI 如何使用 Prompt 管理工具。
     */
    @Override
    protected String buildSystemPrompt() {
        return """
                你是一个 Prompt 工程助手，帮助用户管理 Prompt 模板、优化效果和配置 A/B 测试。

                ## 可用工具

                ### 模板查看
                - list_prompt_templates: 列出所有 Prompt 模板名称
                - get_prompt_template: 获取指定模板详情（当前激活版本）
                - get_prompt_versions: 获取模板版本历史

                ### 版本管理
                - create_prompt_version: 创建新版本
                - activate_prompt_version: 激活指定版本
                - rollback_prompt_version: 回滚到指定版本

                ### 差异分析
                - compare_prompt_versions: 比较两个版本差异

                ### A/B 测试
                - configure_ab_test: 配置 A/B 测试
                - stop_ab_test: 停止 A/B 测试

                ## 工作流程

                1. **了解需求**：分析用户的 Prompt 管理需求
                2. **查看现状**：使用 list_prompt_templates 或 get_prompt_template 了解当前状态
                3. **执行操作**：根据需求选择合适的工具
                4. **验证结果**：确认操作完成

                ## 最佳实践

                - 创建新版本前，先获取现有模板内容
                - 修改 Prompt 时，保留原有优点，针对性改进
                - A/B 测试流量建议从小到大（先 10%，观察效果后逐步增加）
                - 重要变更前建议先备份（创建带备注的新版本）

                ## 输出格式

                - 在每个步骤前，用 <thinking></thinking> 标签说明思考过程
                - 操作结果清晰展示
                - 给出后续优化建议

                ## 错误处理

                - 如果模板不存在，列出可用的模板名称
                - 如果版本号不正确，列出可用版本
                - 操作失败时，解释失败原因并给出解决建议
                """;
    }

    /**
     * 构建执行器
     *
     * <p>注册 PromptTools 到执行器。
     */
    @Override
    protected AgentExecutor buildExecutor(AgentContext context) {
        // 注册工具规范
        List<dev.langchain4j.agent.tool.ToolSpecification> tools =
                List.of(
                        dev.langchain4j.agent.tool.ToolSpecification.builder()
                                .name("list_prompt_templates")
                                .description("列出所有 Prompt 模板名称")
                                .build(),
                        dev.langchain4j.agent.tool.ToolSpecification.builder()
                                .name("get_prompt_template")
                                .description("获取指定 Prompt 模板的详细信息（当前激活版本）")
                                .build(),
                        dev.langchain4j.agent.tool.ToolSpecification.builder()
                                .name("get_prompt_versions")
                                .description("获取指定 Prompt 模板的版本历史")
                                .build(),
                        dev.langchain4j.agent.tool.ToolSpecification.builder()
                                .name("create_prompt_version")
                                .description("为 Prompt 模板创建新版本")
                                .build(),
                        dev.langchain4j.agent.tool.ToolSpecification.builder()
                                .name("compare_prompt_versions")
                                .description("比较 Prompt 模板的两个版本差异")
                                .build(),
                        dev.langchain4j.agent.tool.ToolSpecification.builder()
                                .name("activate_prompt_version")
                                .description("激活 Prompt 模板的指定版本")
                                .build(),
                        dev.langchain4j.agent.tool.ToolSpecification.builder()
                                .name("rollback_prompt_version")
                                .description("回滚 Prompt 模板到指定版本")
                                .build(),
                        dev.langchain4j.agent.tool.ToolSpecification.builder()
                                .name("configure_ab_test")
                                .description("为 Prompt 模板配置 A/B 测试")
                                .build(),
                        dev.langchain4j.agent.tool.ToolSpecification.builder()
                                .name("stop_ab_test")
                                .description("停止 Prompt 模板的 A/B 测试")
                                .build());

        // 注册工具执行器
        Map<String, ToolExecutor> executors = registerToolExecutors();

        return new AgentExecutor(chatModel, buildSystemPrompt(), tools, executors);
    }

    /**
     * 注册工具执行器
     *
     * <p>每个工具对应一个 ToolExecutor，处理工具调用请求。
     */
    private Map<String, ToolExecutor> registerToolExecutors() {
        Map<String, ToolExecutor> executors = new HashMap<>();

        // list_prompt_templates 工具
        executors.put(
                "list_prompt_templates",
                (request, memoryId) -> {
                    log.info("[PromptAgent] 执行 list_prompt_templates");
                    ToolResult result = promptTools.listTemplates();
                    return resultToString(result);
                });

        // get_prompt_template 工具
        executors.put(
                "get_prompt_template",
                (request, memoryId) -> {
                    Map<String, Object> params = parseArgs(request.arguments());
                    String name = (String) params.get("name");
                    log.info("[PromptAgent] 执行 get_prompt_template: name={}", name);
                    ToolResult result = promptTools.getTemplate(name);
                    return resultToString(result);
                });

        // get_prompt_versions 工具
        executors.put(
                "get_prompt_versions",
                (request, memoryId) -> {
                    Map<String, Object> params = parseArgs(request.arguments());
                    String name = (String) params.get("name");
                    log.info("[PromptAgent] 执行 get_prompt_versions: name={}", name);
                    ToolResult result = promptTools.getVersions(name);
                    return resultToString(result);
                });

        // create_prompt_version 工具
        executors.put(
                "create_prompt_version",
                (request, memoryId) -> {
                    Map<String, Object> params = parseArgs(request.arguments());
                    String name = (String) params.get("name");
                    String content = (String) params.get("content");
                    String description = (String) params.getOrDefault("changeDescription", "创建新版本");
                    log.info("[PromptAgent] 执行 create_prompt_version: name={}", name);
                    ToolResult result = promptTools.createVersion(name, content, description);
                    return resultToString(result);
                });

        // compare_prompt_versions 工具
        executors.put(
                "compare_prompt_versions",
                (request, memoryId) -> {
                    Map<String, Object> params = parseArgs(request.arguments());
                    String name = (String) params.get("name");
                    String version1 = (String) params.get("version1");
                    String version2 = (String) params.get("version2");
                    log.info(
                            "[PromptAgent] 执行 compare_prompt_versions: name={}, v1={}, v2={}",
                            name,
                            version1,
                            version2);
                    ToolResult result = promptTools.compareVersions(name, version1, version2);
                    return resultToString(result);
                });

        // activate_prompt_version 工具
        executors.put(
                "activate_prompt_version",
                (request, memoryId) -> {
                    Map<String, Object> params = parseArgs(request.arguments());
                    String name = (String) params.get("name");
                    String version = (String) params.get("version");
                    log.info(
                            "[PromptAgent] 执行 activate_prompt_version: name={}, version={}",
                            name,
                            version);
                    ToolResult result = promptTools.activateVersion(name, version);
                    return resultToString(result);
                });

        // rollback_prompt_version 工具
        executors.put(
                "rollback_prompt_version",
                (request, memoryId) -> {
                    Map<String, Object> params = parseArgs(request.arguments());
                    String name = (String) params.get("name");
                    String targetVersion = (String) params.get("targetVersion");
                    log.info(
                            "[PromptAgent] 执行 rollback_prompt_version: name={}, target={}",
                            name,
                            targetVersion);
                    ToolResult result = promptTools.rollbackVersion(name, targetVersion);
                    return resultToString(result);
                });

        // configure_ab_test 工具
        executors.put(
                "configure_ab_test",
                (request, memoryId) -> {
                    Map<String, Object> params = parseArgs(request.arguments());
                    String name = (String) params.get("name");
                    String baseline = (String) params.get("baselineVersion");
                    String variant = (String) params.get("variantVersion");
                    double traffic =
                            params.get("trafficPercentage") != null
                                    ? ((Number) params.get("trafficPercentage")).doubleValue()
                                    : 50.0;
                    log.info(
                            "[PromptAgent] 执行 configure_ab_test: name={}, baseline={}, variant={}, traffic={}%",
                            name, baseline, variant, traffic);
                    ToolResult result =
                            promptTools.configureABTest(name, baseline, variant, traffic);
                    return resultToString(result);
                });

        // stop_ab_test 工具
        executors.put(
                "stop_ab_test",
                (request, memoryId) -> {
                    Map<String, Object> params = parseArgs(request.arguments());
                    String name = (String) params.get("name");
                    log.info("[PromptAgent] 执行 stop_ab_test: name={}", name);
                    ToolResult result = promptTools.stopABTest(name);
                    return resultToString(result);
                });

        return executors;
    }

    // ==================== 辅助方法 ====================

    /** 解析 JSON 参数 */
    private Map<String, Object> parseArgs(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(argumentsJson, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[PromptAgent] JSON 解析失败: {}", e.getMessage());
            return Map.of();
        }
    }

    /** ToolResult 转换为字符串（供 LangChain4j 返回） */
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
