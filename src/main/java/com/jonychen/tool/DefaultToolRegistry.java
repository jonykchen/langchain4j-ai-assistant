package com.jonychen.tool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import dev.langchain4j.agent.tool.ToolSpecification;
import lombok.extern.slf4j.Slf4j;

/**
 * 默认工具注册中心实现
 *
 * @author jonychen
 */
@Slf4j
@Component
public class DefaultToolRegistry implements ToolRegistry {

    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();
    private final Map<String, ToolSpecification> specifications = new ConcurrentHashMap<>();

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
        log.info("Registered tool: {} [{}]", name, tool.category());
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

        // 创建工具定义
        ToolDefinition definition =
                ToolDefinition.builder()
                        .name(name)
                        .description(annotation.description())
                        .category(annotation.category())
                        .parameters(schema)
                        .executor(executor)
                        .requiredPermissions(Arrays.asList(annotation.requiredPermissions()))
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
                return ToolResult.failure(validationError);
            }

            // 执行
            ToolResult result = tool.executor().execute(params != null ? params : Map.of());
            return result.withExecutionTime(System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("Tool '{}' execution error: {}", toolName, e.getMessage(), e);
            ToolResult result = ToolResult.failure("Execution error: " + e.getMessage());
            return result.withExecutionTime(System.currentTimeMillis() - startTime);
        }
    }

    @Override
    public List<ToolDefinition> getToolsByCategory(ToolCategory category) {
        return tools.values().stream().filter(t -> t.category() == category).toList();
    }

    @Override
    public List<ToolDefinition> getToolsByPermissions(List<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            // 无权限要求时返回所有工具
            return getAllTools();
        }
        return tools.values().stream().filter(t -> hasRequiredPermissions(t, permissions)).toList();
    }

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
}
