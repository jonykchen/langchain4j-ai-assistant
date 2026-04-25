package com.jonychen.tool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

import dev.langchain4j.agent.tool.ToolSpecification;
import lombok.extern.slf4j.Slf4j;

/**
 * 默认工具注册中心实现
 *
 * <p>增强功能： - 支持风险等级（riskLevel） - 支持确认机制（requiresConfirmation） - 支持角色限制（allowedRoles）
 *
 * @author jonychen
 */
@Slf4j
@Component
public class DefaultToolRegistry implements ToolRegistry {

    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();
    private final Map<String, ToolSpecification> specifications = new ConcurrentHashMap<>();

    /** 工具调用统计：toolName -> ToolStatsCounter */
    private final Map<String, ToolStatsCounter> statisticsMap = new ConcurrentHashMap<>();

    @Override
    public void register(ToolDefinition tool) {
        if (tool == null || tool.name() == null) {
            throw new IllegalArgumentException("Tool definition cannot be null");
        }

        String name = tool.name();
        if (tools.containsKey(name)) {
            log.warn("Tool '{}' already registered, will be replaced", name);
        }

        tools.put(name, tool);
        specifications.put(name, ToolSpecificationConverter.convert(tool));
        log.info(
                "Registered tool: {} [category={}, riskLevel={}, requiresConfirmation={}]",
                name,
                tool.category(),
                tool.riskLevel(),
                tool.requiresConfirmation());
    }

    @Override
    public void registerAnnotatedTools(Object toolBean) {
        if (toolBean == null) {
            return;
        }

        // 使用反射扫描 @AgentTool 注解的方法
        java.lang.reflect.Method[] methods = toolBean.getClass().getDeclaredMethods();
        for (java.lang.reflect.Method method : methods) {
            AgentTool agentTool = method.getAnnotation(AgentTool.class);
            if (agentTool != null) {
                registerFromMethod(toolBean, method, agentTool);
            }
        }
    }

    private void registerFromMethod(
            Object bean, java.lang.reflect.Method method, AgentTool annotation) {
        String name = annotation.name().isEmpty() ? method.getName() : annotation.name();

        // 构建 Parameter Schema
        ToolParameterSchema schema = new ToolParameterSchema();
        java.lang.reflect.Parameter[] params = method.getParameters();
        for (java.lang.reflect.Parameter param : params) {
            ToolParam toolParam = param.getAnnotation(ToolParam.class);
            String paramName =
                    toolParam != null && !toolParam.name().isEmpty()
                            ? toolParam.name()
                            : param.getName();
            String paramDesc = toolParam != null ? toolParam.description() : paramName;

            ToolParameterSchema.Property property =
                    convertTypeToProperty(param.getType(), paramDesc);
            if (toolParam != null && toolParam.enumValues().length > 0) {
                property = property.withEnum(Arrays.asList(toolParam.enumValues()));
            }

            schema.addProperty(paramName, property);
            if (toolParam == null || toolParam.required()) {
                schema.addRequired(paramName);
            }
        }

        // 创建执行器
        ToolExecutor executor =
                paramsMap -> {
                    try {
                        // 转换参数类型
                        Object[] args = new Object[params.length];
                        for (int i = 0; i < params.length; i++) {
                            java.lang.reflect.Parameter param = params[i];
                            ToolParam toolParam = param.getAnnotation(ToolParam.class);
                            String paramName =
                                    toolParam != null && !toolParam.name().isEmpty()
                                            ? toolParam.name()
                                            : param.getName();

                            Object value = paramsMap.get(paramName);
                            args[i] = convertValue(value, param.getType());
                        }

                        // 执行方法
                        Object result = method.invoke(bean, args);
                        if (result instanceof ToolResult) {
                            return (ToolResult) result;
                        }
                        return ToolResult.success(result);
                    } catch (Exception e) {
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        log.error(
                                "Tool '{}' execution failed: {}", name, cause.getMessage(), cause);
                        return ToolResult.failure(cause.getMessage());
                    }
                };

        // 创建工具定义（包含新增属性）
        ToolDefinition definition =
                ToolDefinition.builder()
                        .name(name)
                        .description(annotation.description())
                        .category(annotation.category())
                        .riskLevel(annotation.riskLevel())
                        .parameters(schema)
                        .executor(executor)
                        .requiredPermissions(Arrays.asList(annotation.requiredPermissions()))
                        .allowedRoles(Arrays.asList(annotation.allowedRoles()))
                        .requiresConfirmation(annotation.requiresConfirmation())
                        .timeout(java.time.Duration.ofMillis(annotation.timeoutMs()))
                        .maxRetries(annotation.maxRetries())
                        .build();

        register(definition);
        log.debug("Registered annotated tool '{}' from {}", name, bean.getClass().getSimpleName());
    }

    private ToolParameterSchema.Property convertTypeToProperty(Class<?> type, String description) {
        if (type == String.class) {
            return ToolParameterSchema.Property.string(description);
        } else if (type == Integer.class
                || type == int.class
                || type == Long.class
                || type == long.class) {
            return ToolParameterSchema.Property.integer(description);
        } else if (type == Double.class
                || type == double.class
                || type == Float.class
                || type == float.class) {
            return ToolParameterSchema.Property.number(description);
        } else if (type == Boolean.class || type == boolean.class) {
            return ToolParameterSchema.Property.bool(description);
        } else if (type.isAssignableFrom(List.class)) {
            return ToolParameterSchema.Property.array(description);
        } else if (type.isAssignableFrom(Map.class)) {
            return ToolParameterSchema.Property.object(description);
        } else {
            return ToolParameterSchema.Property.string(description);
        }
    }

    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return value;
        }
        String strValue = String.valueOf(value);
        if (targetType == String.class) {
            return strValue;
        } else if (targetType == Integer.class || targetType == int.class) {
            return Integer.parseInt(strValue);
        } else if (targetType == Long.class || targetType == long.class) {
            return Long.parseLong(strValue);
        } else if (targetType == Double.class || targetType == double.class) {
            return Double.parseDouble(strValue);
        } else if (targetType == Float.class || targetType == float.class) {
            return Float.parseFloat(strValue);
        } else if (targetType == Boolean.class || targetType == boolean.class) {
            return Boolean.parseBoolean(strValue);
        }
        return value;
    }

    @Override
    public void unregister(String toolName) {
        if (toolName == null) {
            return;
        }
        ToolDefinition removed = tools.remove(toolName);
        specifications.remove(toolName);
        if (removed != null) {
            log.info("Unregistered tool: {}", toolName);
        }
    }

    @Override
    public Optional<ToolDefinition> getTool(String toolName) {
        if (toolName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(tools.get(toolName));
    }

    @Override
    public boolean hasTool(String toolName) {
        return toolName != null && tools.containsKey(toolName);
    }

    @Override
    public List<String> getToolNames() {
        return new ArrayList<>(tools.keySet());
    }

    @Override
    public List<ToolDefinition> getAllTools() {
        return new ArrayList<>(tools.values());
    }

    @Override
    public List<ToolSpecification> getToolSpecifications() {
        return new ArrayList<>(specifications.values());
    }

    @Override
    public Optional<ToolSpecification> getToolSpecification(String toolName) {
        if (toolName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(specifications.get(toolName));
    }

    @Override
    public ToolResult execute(String toolName, Map<String, Object> params) {
        ToolDefinition tool =
                getTool(toolName)
                        .orElseThrow(
                                () -> new ToolNotFoundException("Tool not found: " + toolName));

        long startTime = System.currentTimeMillis();
        try {
            // 执行前验证
            String validationError = tool.executor().validate(params);
            if (validationError != null) {
                long execTime = System.currentTimeMillis() - startTime;
                recordFailure(toolName, execTime, validationError);
                return ToolResult.failure(validationError).withExecutionTime(execTime);
            }

            // 敏感操作确认检查（仅返回 pending 状态，不发送 SSE 事件）
            // 注意：确认生命周期由 AgentContext 统一管理
            if (tool.requiresConfirmation()) {
                String confirmationId =
                        "confirm_" + java.util.UUID.randomUUID().toString().substring(0, 8);
                // 确认不算成功也不算失败，不记录统计
                return ToolResult.pendingConfirmation(
                        confirmationId, buildConfirmationMessage(tool, params), tool.riskLevel());
            }

            // 执行
            ToolResult result = tool.executor().execute(params != null ? params : Map.of());
            long execTime = System.currentTimeMillis() - startTime;

            // 记录统计
            if (result.success()) {
                recordSuccess(toolName, execTime);
            } else {
                recordFailure(toolName, execTime, result.error());
            }

            return result.withExecutionTime(execTime);
        } catch (Exception e) {
            long execTime = System.currentTimeMillis() - startTime;
            log.error("Tool '{}' execution error: {}", toolName, e.getMessage(), e);
            recordFailure(toolName, execTime, e.getMessage());
            ToolResult result = ToolResult.failure("Execution error: " + e.getMessage());
            return result.withExecutionTime(execTime);
        }
    }

    /** 构建确认提示消息 */
    private String buildConfirmationMessage(ToolDefinition tool, Map<String, Object> params) {
        return String.format("即将执行 %s（%s），参数: %s", tool.name(), tool.description(), params);
    }

    @Override
    public List<ToolDefinition> getToolsByCategory(ToolCategory category) {
        return tools.values().stream().filter(t -> t.category() == category).toList();
    }

    /** 按风险等级获取工具 */
    public List<ToolDefinition> getToolsByRiskLevel(RiskLevel riskLevel) {
        return tools.values().stream().filter(t -> t.riskLevel() == riskLevel).toList();
    }

    /** 按角色获取可用工具 */
    public List<ToolDefinition> getToolsByRole(String role) {
        return tools.values().stream()
                .filter(t -> t.allowedRoles().isEmpty() || t.allowedRoles().contains(role))
                .toList();
    }

    @Override
    public List<ToolDefinition> getToolsByPermissions(List<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            // 无权限要求时返回所有工具
            return getAllTools();
        }
        return tools.values().stream().filter(t -> hasRequiredPermissions(t, permissions)).toList();
    }

    /** 检查用户是否拥有工具所需的全部权限 */
    private boolean hasRequiredPermissions(ToolDefinition tool, List<String> userPermissions) {
        if (tool.requiredPermissions() == null || tool.requiredPermissions().isEmpty()) {
            return true;
        }
        return userPermissions.containsAll(tool.requiredPermissions());
    }

    @Override
    public int size() {
        return tools.size();
    }

    @Override
    public ToolStatistics getStatistics(String toolName) {
        if (toolName == null) {
            return null;
        }
        ToolStatsCounter counter = statisticsMap.get(toolName);
        if (counter == null) {
            return ToolStatistics.empty(toolName);
        }
        ToolStatistics stats = counter.toStatistics();
        return new ToolStatistics(
                toolName,
                stats.totalCalls(),
                stats.successCount(),
                stats.failureCount(),
                stats.avgExecutionTimeMs(),
                stats.lastCallTime(),
                stats.lastError());
    }

    @Override
    public List<ToolStatistics> getAllStatistics() {
        List<ToolStatistics> result = new ArrayList<>();
        for (String toolName : tools.keySet()) {
            result.add(getStatistics(toolName));
        }
        return result;
    }

    @Override
    public void resetStatistics(String toolName) {
        if (toolName != null) {
            statisticsMap.remove(toolName);
            log.debug("Reset statistics for tool: {}", toolName);
        }
    }

    @Override
    public void resetAllStatistics() {
        statisticsMap.clear();
        log.debug("Reset all tool statistics");
    }

    /** 记录工具调用成功 */
    private void recordSuccess(String toolName, long executionTimeMs) {
        ToolStatsCounter counter =
                statisticsMap.computeIfAbsent(toolName, k -> new ToolStatsCounter());
        counter.recordSuccess(executionTimeMs);
    }

    /** 记录工具调用失败 */
    private void recordFailure(String toolName, long executionTimeMs, String error) {
        ToolStatsCounter counter =
                statisticsMap.computeIfAbsent(toolName, k -> new ToolStatsCounter());
        counter.recordFailure(executionTimeMs, error);
    }

    /** 工具统计计数器（线程安全） */
    private static class ToolStatsCounter {
        private final AtomicLong totalCalls = new AtomicLong(0);
        private final AtomicLong successCount = new AtomicLong(0);
        private final AtomicLong failureCount = new AtomicLong(0);
        private final AtomicLong totalTimeMs = new AtomicLong(0);
        private final AtomicLong lastCallTime = new AtomicLong(0);
        private final AtomicReference<String> lastError = new AtomicReference<>(null);

        void recordSuccess(long executionTimeMs) {
            totalCalls.incrementAndGet();
            successCount.incrementAndGet();
            totalTimeMs.addAndGet(executionTimeMs);
            lastCallTime.set(System.currentTimeMillis());
        }

        void recordFailure(long executionTimeMs, String error) {
            totalCalls.incrementAndGet();
            failureCount.incrementAndGet();
            totalTimeMs.addAndGet(executionTimeMs);
            lastCallTime.set(System.currentTimeMillis());
            lastError.set(error);
        }

        ToolStatistics toStatistics() {
            long total = totalCalls.get();
            double avgTime = total > 0 ? (double) totalTimeMs.get() / total : 0.0;
            return new ToolStatistics(
                    null, // toolName 由外部设置
                    total,
                    successCount.get(),
                    failureCount.get(),
                    avgTime,
                    lastCallTime.get(),
                    lastError.get());
        }
    }
}
