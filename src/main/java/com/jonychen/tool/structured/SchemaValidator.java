package com.jonychen.tool.structured;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Schema 验证器
 *
 * @author jonychen
 */
@Slf4j
@Component
public class SchemaValidator {

    /**
     * 验证数据是否符合 Schema
     *
     * @param data   待验证数据
     * @param schema Schema 定义
     * @return 验证结果
     */
    public ValidationResult validate(Object data, OutputSchema schema) {
        if (schema == null) {
            return ValidationResult.success();
        }

        List<String> errors = new java.util.ArrayList<>();

        // 根据类型验证
        validateType(data, schema, "", errors);

        return errors.isEmpty()
                ? ValidationResult.success()
                : ValidationResult.failure(errors);
    }

    /**
     * 类型验证
     */
    private void validateType(Object data, OutputSchema schema, String path, List<String> errors) {
        if (data == null) {
            // 检查是否必需
            if (schema.required() != null && !schema.required().isEmpty()) {
                errors.add(path + ": 值不能为空");
            }
            return;
        }

        switch (schema.type()) {
            case STRING -> validateString(data, path, errors);
            case INTEGER -> validateInteger(data, path, errors);
            case NUMBER -> validateNumber(data, path, errors);
            case BOOLEAN -> validateBoolean(data, path, errors);
            case ARRAY -> validateArray(data, schema, path, errors);
            case OBJECT -> validateObject(data, schema, path, errors);
        }
    }

    private void validateString(Object data, String path, List<String> errors) {
        if (!(data instanceof String)) {
            errors.add(String.format("%s: 期望 string 类型，实际 %s",
                    path, data.getClass().getSimpleName()));
        }
    }

    private void validateInteger(Object data, String path, List<String> errors) {
        if (data instanceof Number) {
            // 检查是否为整数
            double value = ((Number) data).doubleValue();
            if (value != Math.floor(value)) {
                errors.add(path + ": 期望整数，实际浮点数 " + value);
            }
        } else if (!(data instanceof Integer || data instanceof Long)) {
            errors.add(String.format("%s: 期望 integer 类型，实际 %s",
                    path, data.getClass().getSimpleName()));
        }
    }

    private void validateNumber(Object data, String path, List<String> errors) {
        if (!(data instanceof Number)) {
            errors.add(String.format("%s: 期望 number 类型，实际 %s",
                    path, data.getClass().getSimpleName()));
        }
    }

    private void validateBoolean(Object data, String path, List<String> errors) {
        if (!(data instanceof Boolean)) {
            errors.add(String.format("%s: 期望 boolean 类型，实际 %s",
                    path, data.getClass().getSimpleName()));
        }
    }

    @SuppressWarnings("unchecked")
    private void validateArray(Object data, OutputSchema schema, String path, List<String> errors) {
        if (!(data instanceof List<?>)) {
            errors.add(String.format("%s: 期望 array 类型，实际 %s",
                    path, data.getClass().getSimpleName()));
            return;
        }

        List<?> list = (List<?>) data;
        OutputSchema.PropertySchema itemSchema = schema.properties() != null
                ? schema.properties().get("items")
                : null;

        if (itemSchema != null) {
            for (int i = 0; i < list.size(); i++) {
                String itemPath = path + "[" + i + "]";
                Object item = list.get(i);
                validatePropertyValue(item, itemSchema, itemPath, errors);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void validateObject(Object data, OutputSchema schema, String path, List<String> errors) {
        if (!(data instanceof Map)) {
            errors.add(String.format("%s: 期望 object 类型，实际 %s",
                    path, data.getClass().getSimpleName()));
            return;
        }

        Map<String, Object> map = (Map<String, Object>) data;

        // 检查必需属性
        if (schema.required() != null) {
            for (String required : schema.required()) {
                if (!map.containsKey(required)) {
                    errors.add(path + ": 缺少必需属性 '" + required + "'");
                }
            }
        }

        // 验证每个属性
        if (schema.properties() != null) {
            for (Map.Entry<String, OutputSchema.PropertySchema> entry : schema.properties().entrySet()) {
                String propName = entry.getKey();
                if (map.containsKey(propName)) {
                    String propPath = path.isEmpty() ? propName : path + "." + propName;
                    validatePropertyValue(map.get(propName), entry.getValue(), propPath, errors);
                }
            }
        }
    }

    private void validatePropertyValue(Object value, OutputSchema.PropertySchema propSchema,
                                        String path, List<String> errors) {
        if (value == null) {
            return;
        }

        String type = propSchema.type();

        switch (type) {
            case "string" -> {
                if (!(value instanceof String)) {
                    errors.add(String.format("%s: 期望 string 类型，实际 %s",
                            path, value.getClass().getSimpleName()));
                }
                // 检查枚举值
                if (propSchema.enumValues() != null && !propSchema.enumValues().isEmpty()) {
                    if (!propSchema.enumValues().contains(value)) {
                        errors.add(path + ": 值 '" + value + "' 不在枚举值列表中");
                    }
                }
            }
            case "integer" -> {
                if (!(value instanceof Number)) {
                    errors.add(String.format("%s: 期望 integer 类型，实际 %s",
                            path, value.getClass().getSimpleName()));
                }
            }
            case "number" -> {
                if (!(value instanceof Number)) {
                    errors.add(String.format("%s: 期望 number 类型，实际 %s",
                            path, value.getClass().getSimpleName()));
                }
            }
            case "boolean" -> {
                if (!(value instanceof Boolean)) {
                    errors.add(String.format("%s: 期望 boolean 类型，实际 %s",
                            path, value.getClass().getSimpleName()));
                }
            }
            case "array" -> {
                if (!(value instanceof List)) {
                    errors.add(String.format("%s: 期望 array 类型，实际 %s",
                            path, value.getClass().getSimpleName()));
                } else if (propSchema.items() != null) {
                    List<?> list = (List<?>) value;
                    for (int i = 0; i < list.size(); i++) {
                        validatePropertyValue(list.get(i), propSchema.items(),
                                path + "[" + i + "]", errors);
                    }
                }
            }
            case "object" -> {
                if (!(value instanceof Map)) {
                    errors.add(String.format("%s: 期望 object 类型，实际 %s",
                            path, value.getClass().getSimpleName()));
                }
            }
        }
    }

    /**
     * 验证结果
     */
    public record ValidationResult(
            boolean valid,
            List<String> errors
    ) {
        public static ValidationResult success() {
            return new ValidationResult(true, List.of());
        }

        public static ValidationResult failure(List<String> errors) {
            return new ValidationResult(false, errors);
        }

        public String getErrorMessage() {
            return String.join("; ", errors);
        }
    }
}