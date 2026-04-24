package com.jonychen.agent.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jonychen.agent.core.AbstractAgent;
import com.jonychen.agent.core.AgentContext;
import com.jonychen.agent.core.AgentExecutor;
import com.jonychen.agent.core.AgentMetadata;
import com.jonychen.agent.core.AgentRequest;
import com.jonychen.agent.core.ToolCallRequest;
import com.jonychen.observability.trace.AgentTraceService;
import com.jonychen.tool.RiskLevel;
import com.jonychen.tool.ToolDefinition;
import com.jonychen.tool.ToolRegistry;
import com.jonychen.tool.ToolResult;
import com.jonychen.tool.builtin.CircuitBreakerTools;
import com.jonychen.tool.builtin.ModelStateTools;
import com.jonychen.tool.builtin.TokenUsageTools;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.tool.ToolExecutor;

/**
 * 运维助手 Agent
 *
 * <p>能力：
 *
 * <ul>
 *   <li>模型健康检查
 *   <li>熔断器状态查询
 *   <li>动态调整模型权重
 *   <li>启用/禁用模型
 *   <li>Token 用量分析
 * </ul>
 *
 * <p>权限：需要 ADMIN 角色
 *
 * @author jonychen
 */
@Component
public class OpsAgent extends AbstractAgent {

    private static final Logger log = LoggerFactory.getLogger(OpsAgent.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ModelStateTools modelStateTools;
    private final CircuitBreakerTools circuitBreakerTools;
    private final TokenUsageTools tokenUsageTools;

    public OpsAgent(
            ChatModel chatModel,
            ToolRegistry toolRegistry,
            AgentTraceService traceService,
            ModelStateTools modelStateTools,
            CircuitBreakerTools circuitBreakerTools,
            TokenUsageTools tokenUsageTools) {
        super(chatModel, toolRegistry, traceService);
        this.modelStateTools = modelStateTools;
        this.circuitBreakerTools = circuitBreakerTools;
        this.tokenUsageTools = tokenUsageTools;
    }

    @Override
    public AgentMetadata getMetadata() {
        return AgentMetadata.ops();
    }

    @Override
    public double canHandle(AgentRequest request) {
        String input = request.userInput().toLowerCase();
        if (input.contains("模型")
                && (input.contains("健康") || input.contains("状态") || input.contains("权重"))) {
            return 0.9;
        }
        if (input.contains("熔断") || input.contains("运维") || input.contains("ops")) {
            return 0.85;
        }
        if (input.contains("模型") || input.contains("token")) {
            return 0.6;
        }
        return 0.0;
    }

    @Override
    public List<ToolDefinition> getAvailableTools() {
        return toolRegistry.getToolsByCategory(com.jonychen.tool.ToolCategory.SYSTEM);
    }

    @Override
    protected String buildSystemPrompt() {
        return """
                你是一个智能运维助手，负责 AI 模型系统的诊断和维护。

                ## 可用工具
                - getHealth: 获取所有模型的健康状态
                - getStatus: 获取熔断器状态
                - adjustWeight: 调整模型负载均衡权重
                - toggleEnabled: 启用/禁用模型
                - getUsage: 获取 token 用量统计
                - reset: 重置熔断器

                ## 工作流程
                1. 先获取当前状态（健康检查、熔断器状态）
                2. 分析问题原因
                3. 制定处置方案
                4. 执行处置（敏感操作需要用户确认）
                5. 验证处置效果

                ## 输出格式
                在每个步骤前，用 <thinking></thinking> 标签说明你的思考过程。

                ## 安全约束
                - 调整权重和禁用模型是敏感操作，需要用户确认
                - 每次只能调整一个模型的权重
                - 权重调整幅度不能超过 50
                """;
    }

    @Override
    protected AgentExecutor buildExecutor(AgentContext context) {
        var tools = registerTools();
        var executors = registerToolExecutors();

        return new AgentExecutor(chatModel, buildSystemPrompt(), tools, executors);
    }

    /** 注册工具规范 */
    private List<dev.langchain4j.agent.tool.ToolSpecification> registerTools() {
        return List.of(
                dev.langchain4j.agent.tool.ToolSpecification.builder()
                        .name("getHealth")
                        .description("获取所有 AI 模型的健康状态")
                        .build(),
                dev.langchain4j.agent.tool.ToolSpecification.builder()
                        .name("getStatus")
                        .description("获取所有模型的熔断器状态")
                        .build(),
                dev.langchain4j.agent.tool.ToolSpecification.builder()
                        .name("adjustWeight")
                        .description("调整模型的负载均衡权重")
                        .build(),
                dev.langchain4j.agent.tool.ToolSpecification.builder()
                        .name("toggleEnabled")
                        .description("启用或禁用指定模型")
                        .build(),
                dev.langchain4j.agent.tool.ToolSpecification.builder()
                        .name("getUsage")
                        .description("获取 Token 使用量统计")
                        .build(),
                dev.langchain4j.agent.tool.ToolSpecification.builder()
                        .name("reset")
                        .description("重置指定模型的熔断器")
                        .build());
    }

    /** 注册工具执行器 */
    private Map<String, ToolExecutor> registerToolExecutors() {
        Map<String, ToolExecutor> executors = new HashMap<>();

        executors.put(
                "getHealth",
                (request, memoryId) ->
                        modelStateTools.getHealth(
                                parseBool(request.arguments(), "includeDisabled", false)));

        executors.put("getStatus", (request, memoryId) -> circuitBreakerTools.getStatus());

        executors.put(
                "adjustWeight",
                (request, memoryId) -> {
                    Map<String, Object> params = parseArgs(request.arguments());
                    return modelStateTools.adjustWeight(
                            (String) params.get("modelName"),
                            parseInt(params, "weight", 50),
                            (String) params.getOrDefault("reason", "运维调整"));
                });

        executors.put(
                "toggleEnabled",
                (request, memoryId) -> {
                    Map<String, Object> params = parseArgs(request.arguments());
                    return modelStateTools.toggleEnabled(
                            (String) params.get("modelName"),
                            parseBool(params, "enabled", true),
                            (String) params.getOrDefault("reason", "运维操作"));
                });

        executors.put("getUsage", (request, memoryId) -> tokenUsageTools.getUsage());

        executors.put(
                "reset",
                (request, memoryId) -> {
                    Map<String, Object> params = parseArgs(request.arguments());
                    return circuitBreakerTools.reset((String) params.get("modelName"));
                });

        return executors;
    }

    @Override
    protected ToolResult executeTool(ToolCallRequest toolCall, AgentContext context) {
        String toolName = toolCall.name();
        log.info("[OpsAgent] 执行工具: {} params={}", toolName, toolCall.params());

        // 检查是否需要确认（敏感操作）
        if (isSensitiveOperation(toolName)) {
            String confirmationId = generateConfirmationId();
            String message = "即将执行敏感操作: " + toolName;
            return ToolResult.pendingConfirmation(confirmationId, message, RiskLevel.HIGH);
        }

        return super.executeTool(toolCall, context);
    }

    /** 判断是否是敏感操作 */
    private boolean isSensitiveOperation(String toolName) {
        return "adjustWeight".equals(toolName)
                || "toggleEnabled".equals(toolName)
                || "reset".equals(toolName);
    }

    // ===== 辅助方法 =====

    private Map<String, Object> parseArgs(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(argumentsJson, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[OpsAgent] JSON 解析失败: {}", e.getMessage());
            return Map.of();
        }
    }

    private boolean parseBool(String argumentsJson, String key, boolean defaultValue) {
        Map<String, Object> params = parseArgs(argumentsJson);
        return parseBool(params, key, defaultValue);
    }

    private boolean parseBool(Map<String, Object> params, String key, boolean defaultValue) {
        Object value = params.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(value.toString());
    }

    private int parseInt(Map<String, Object> params, String key, int defaultValue) {
        Object value = params.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
