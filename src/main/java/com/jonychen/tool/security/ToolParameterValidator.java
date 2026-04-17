package com.jonychen.tool.security;

import com.jonychen.tool.ToolCategory;
import com.jonychen.tool.ToolDefinition;
import com.jonychen.tool.ToolParameterSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 工具参数校验器
 *
 * 提供：JSON Schema 校验、类型校验、安全校验
 *
 * @author jonychen
 */
@Slf4j
@Component
public class ToolParameterValidator {

    // SQL 注入检测模式
    private static final List<Pattern> SQL_INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i)(drop|delete|truncate|alter|create|exec|execute)\\s+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("--", Pattern.CASE_INSENSITIVE),
            Pattern.compile(";\\s*(drop|delete|truncate)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)union\\s+(all|select)\\s+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)insert\\s+into\\s+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)update\\s+.+\\s+set\\s+", Pattern.CASE_INSENSITIVE)
    );

    // 路径遍历检测模式
    private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile("(\\.\\.\\/|\\.\\.\\\\|~\\/)");

    // XSS 检测模式
    private static final List<Pattern> XSS_PATTERNS = List.of(
            Pattern.compile("<script[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("on(load|error|click|mouseover|focus|blur)\\s*=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<iframe[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<object[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<embed[^>]*>", Pattern.CASE_INSENSITIVE)
    );

    /**
     * 校验参数是否符合工具定义
     *
     * @param params   参数 Map
     * @param toolDef  工具定义
     * @return 校验结果
     */
    public ValidationResult validate(Map<String, Object> params, ToolDefinition toolDef) {
        if (params == null) {
            params = Map.of();
        }

        List<String> errors = new ArrayList<>();

        // 1. 必需参数校验
        errors.addAll(validateRequired(params, toolDef));

        // 2. 类型校验
        errors.addAll(validateTypes(params, toolDef));

        // 3. 业务规则校验
        errors.addAll(validateBusinessRules(params, toolDef));

        // 4. 安全校验
        errors.addAll(validateSecurity(params, toolDef));

        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }

    /**
     * 必需参数校验
     */
    private List<String> validateRequired(Map<String, Object> params, ToolDefinition toolDef) {
        List<String> errors = new ArrayList<>();

        ToolParameterSchema schema = toolDef.parameters();
        if (schema == null || schema.getRequired() == null) {
            return errors;
        }

        for (String requiredParam : schema.getRequired()) {
            if (!params.containsKey(requiredParam) || params.get(requiredParam) == null) {
                errors.add("缺少必需参数: " + requiredParam);
            }
        }

        return errors;
    }

    /**
     * 类型校验
     */
    private List<String> validateTypes(Map<String, Object> params, ToolDefinition toolDef) {
        List<String> errors = new ArrayList<>();

        ToolParameterSchema schema = toolDef.parameters();
        if (schema == null || schema.getProperties() == null) {
            return errors;
        }

        for (Map.Entry<String, ToolParameterSchema.Property> entry : schema.getProperties().entrySet()) {
            String paramName = entry.getKey();
            ToolParameterSchema.Property prop = entry.getValue();

            Object value = params.get(paramName);
            if (value != null) {
                String typeError = validateType(paramName, prop.getType(), value);
                if (typeError != null) {
                    errors.add(typeError);
                }
            }
        }

        return errors;
    }

    /**
     * 单个参数类型校验
     */
    private String validateType(String paramName, String expectedType, Object value) {
        boolean valid = switch (expectedType.toLowerCase()) {
            case "string" -> value instanceof String;
            case "integer" -> value instanceof Integer || value instanceof Long ||
                    (value instanceof Number && ((Number) value).doubleValue() == ((Number) value).longValue());
            case "number" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "array" -> value instanceof List || value instanceof Object[];
            case "object" -> value instanceof Map;
            default -> true;  // 未知类型不校验
        };

        if (!valid) {
            return String.format("参数 '%s' 类型错误: 期望 %s, 实际 %s",
                    paramName, expectedType, value.getClass().getSimpleName());
        }

        return null;
    }

    /**
     * 业务规则校验（范围、格式等）
     */
    private List<String> validateBusinessRules(Map<String, Object> params, ToolDefinition toolDef) {
        List<String> errors = new ArrayList<>();

        ToolParameterSchema schema = toolDef.parameters();
        if (schema == null || schema.getProperties() == null) {
            return errors;
        }

        for (Map.Entry<String, ToolParameterSchema.Property> entry : schema.getProperties().entrySet()) {
            String paramName = entry.getKey();
            ToolParameterSchema.Property prop = entry.getValue();
            Object value = params.get(paramName);

            if (value == null) continue;

            // 枚举值校验
            if (prop.getEnumValues() != null && !prop.getEnumValues().isEmpty() && value instanceof String) {
                if (!prop.getEnumValues().contains((String) value)) {
                    errors.add(String.format("参数 '%s' 值无效: 可选值为 %s",
                            paramName, prop.getEnumValues()));
                }
            }

            // 数值范围校验
            if (value instanceof Number num) {
                if (num.doubleValue() < 0 && "limit".equals(paramName)) {
                    errors.add("参数 'limit' 必须为正数");
                }
                if (num.doubleValue() > 10000 && "limit".equals(paramName)) {
                    errors.add("参数 'limit' 不能超过 10000");
                }
            }

            // 字符串长度校验
            if (value instanceof String str) {
                if (str.length() > 10000) {
                    errors.add(String.format("参数 '%s' 长度过长，最大 10000 字符", paramName));
                }
            }
        }

        return errors;
    }

    /**
     * 安全校验（SQL注入、路径遍历、XSS等）
     */
    private List<String> validateSecurity(Map<String, Object> params, ToolDefinition toolDef) {
        List<String> errors = new ArrayList<>();

        // 数据库工具 - SQL 注入检测
        if (toolDef.category() == ToolCategory.DATABASE) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                if (entry.getValue() instanceof String str) {
                    if (containsSqlInjection(str)) {
                        errors.add(String.format("参数 '%s' 包含潜在的 SQL 注入风险", entry.getKey()));
                    }
                }
            }
        }

        // 文件工具 - 路径遍历检测
        if (toolDef.category() == ToolCategory.FILE) {
            Object path = params.get("path");
            if (path instanceof String str && containsPathTraversal(str)) {
                errors.add("参数 'path' 包含潜在的路径遍历风险");
            }
        }

        // 所有工具 - XSS 检测
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (entry.getValue() instanceof String str) {
                if (containsXss(str)) {
                    errors.add(String.format("参数 '%s' 包含潜在的 XSS 风险", entry.getKey()));
                }
            }
        }

        return errors;
    }

    /**
     * 检测 SQL 注入
     */
    private boolean containsSqlInjection(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        return SQL_INJECTION_PATTERNS.stream()
                .anyMatch(pattern -> pattern.matcher(str).find());
    }

    /**
     * 检测路径遍历
     */
    private boolean containsPathTraversal(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        return PATH_TRAVERSAL_PATTERN.matcher(path).find();
    }

    /**
     * 检测 XSS
     */
    private boolean containsXss(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        return XSS_PATTERNS.stream()
                .anyMatch(pattern -> pattern.matcher(str).find());
    }
}