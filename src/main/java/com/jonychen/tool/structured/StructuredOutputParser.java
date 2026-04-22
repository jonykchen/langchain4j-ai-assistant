package com.jonychen.tool.structured;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 结构化输出解析器
 *
 * <p>从 LLM 响应中提取结构化数据
 *
 * @author jonychen
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StructuredOutputParser {

    private final ObjectMapper objectMapper;
    private final SchemaValidator schemaValidator;

    /** JSON 代码块模式 */
    private static final Pattern JSON_BLOCK_PATTERN =
            Pattern.compile("```(?:json)?\\s*\\n?([\\s\\S]*?)\\n?```", Pattern.MULTILINE);

    /** JSON 对象模式 */
    private static final Pattern JSON_OBJECT_PATTERN =
            Pattern.compile("\\{[\\s\\S]*\\}", Pattern.MULTILINE);

    /** JSON 数组模式 */
    private static final Pattern JSON_ARRAY_PATTERN =
            Pattern.compile("\\[[\\s\\S]*\\]", Pattern.MULTILINE);

    /**
     * 解析 LLM 响应为结构化数据
     *
     * @param response LLM 响应文本
     * @param config 结构化输出配置
     * @return 解析后的结构化数据
     */
    public Map<String, Object> parse(String response, StructuredOutputConfig config) {
        if (response == null || response.isBlank()) {
            throw ResponseFormatException.parseFailed("", "Response is empty");
        }

        String jsonStr = extractJson(response);
        if (jsonStr == null) {
            throw ResponseFormatException.parseFailed(response, "No JSON found in response");
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(jsonStr, Map.class);

            // 验证
            if (config.validateOnParse()) {
                SchemaValidator.ValidationResult validation =
                        schemaValidator.validate(data, config.schema());
                if (!validation.valid()) {
                    if (config.strictMode()) {
                        throw ResponseFormatException.validationFailed(
                                validation.getErrorMessage());
                    }
                    log.warn("Schema validation warnings: {}", validation.getErrorMessage());
                }
            }

            return data;

        } catch (JsonProcessingException e) {
            throw ResponseFormatException.parseFailed(
                    response, "JSON parse error: " + e.getMessage());
        }
    }

    /**
     * 解析为指定类型
     *
     * @param response LLM 响应
     * @param config 配置
     * @param type 目标类型
     * @return 解析后的对象
     */
    public <T> T parse(String response, StructuredOutputConfig config, Class<T> type) {
        if (response == null || response.isBlank()) {
            throw ResponseFormatException.parseFailed("", "Response is empty");
        }

        String jsonStr = extractJson(response);
        if (jsonStr == null) {
            throw ResponseFormatException.parseFailed(response, "No JSON found in response");
        }

        try {
            T data = objectMapper.readValue(jsonStr, type);

            // 验证（如果是 Map 类型）
            if (config.validateOnParse() && data instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) data;
                SchemaValidator.ValidationResult validation =
                        schemaValidator.validate(map, config.schema());
                if (!validation.valid() && config.strictMode()) {
                    throw ResponseFormatException.validationFailed(validation.getErrorMessage());
                }
            }

            return data;

        } catch (JsonProcessingException e) {
            throw ResponseFormatException.parseFailed(
                    response, "JSON parse error: " + e.getMessage());
        }
    }

    /** 从文本中提取 JSON */
    private String extractJson(String text) {
        // 1. 尝试从代码块中提取
        Matcher blockMatcher = JSON_BLOCK_PATTERN.matcher(text);
        if (blockMatcher.find()) {
            return blockMatcher.group(1).trim();
        }

        // 2. 尝试匹配 JSON 对象
        Matcher objectMatcher = JSON_OBJECT_PATTERN.matcher(text);
        if (objectMatcher.find()) {
            return objectMatcher.group();
        }

        // 3. 尝试匹配 JSON 数组
        Matcher arrayMatcher = JSON_ARRAY_PATTERN.matcher(text);
        if (arrayMatcher.find()) {
            return arrayMatcher.group();
        }

        return null;
    }

    /**
     * 生成用于 Prompt 的 Schema 说明
     *
     * @param schema Schema 定义
     * @return Schema 说明文本
     */
    public String generateSchemaPrompt(OutputSchema schema) {
        StringBuilder sb = new StringBuilder();
        sb.append("请按照以下 JSON Schema 格式输出：\n\n");
        sb.append("```json\n");

        try {
            sb.append(
                    objectMapper
                            .writerWithDefaultPrettyPrinter()
                            .writeValueAsString(schema.toJsonSchemaMap()));
        } catch (JsonProcessingException e) {
            sb.append(schema.toJsonSchemaMap().toString());
        }

        sb.append("\n```\n");

        if (schema.required() != null && !schema.required().isEmpty()) {
            sb.append("\n必需字段: ").append(String.join(", ", schema.required())).append("\n");
        }

        return sb.toString();
    }

    /**
     * 生成用于 Prompt 的示例输出
     *
     * @param schema Schema 定义
     * @return 示例 JSON
     */
    public String generateExampleOutput(OutputSchema schema) {
        if (schema.properties() == null || schema.properties().isEmpty()) {
            return "{}";
        }

        Map<String, Object> example = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, OutputSchema.PropertySchema> entry :
                schema.properties().entrySet()) {
            example.put(entry.getKey(), generateExampleValue(entry.getValue()));
        }

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(example);
        } catch (JsonProcessingException e) {
            return example.toString();
        }
    }

    private Object generateExampleValue(OutputSchema.PropertySchema prop) {
        String type = prop.type();

        return switch (type) {
            case "string" ->
                    prop.enumValues() != null && !prop.enumValues().isEmpty()
                            ? prop.enumValues().get(0)
                            : "example_string";
            case "integer" -> 123;
            case "number" -> 123.45;
            case "boolean" -> true;
            case "array" -> java.util.List.of();
            case "object" -> Map.of();
            default -> null;
        };
    }
}
