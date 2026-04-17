package com.jonychen.tool;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ToolSpecification 转换器
 * 将内部工具定义转换为 LangChain4j 格式
 *
 * @author jonychen
 */
public class ToolSpecificationConverter {

    /**
     * 将 ToolDefinition 转换为 LangChain4j ToolSpecification
     */
    public static ToolSpecification convert(ToolDefinition definition) {
        ToolSpecification.Builder builder = ToolSpecification.builder()
                .name(definition.name())
                .description(definition.description());

        // 添加参数
        ToolParameterSchema schema = definition.parameters();
        if (schema != null && schema.getProperties() != null && !schema.getProperties().isEmpty()) {
            Map<String, JsonSchemaElement> paramSchemas = new LinkedHashMap<>();

            for (Map.Entry<String, ToolParameterSchema.Property> entry : schema.getProperties().entrySet()) {
                String paramName = entry.getKey();
                ToolParameterSchema.Property prop = entry.getValue();
                paramSchemas.put(paramName, convertProperty(prop));
            }

            List<String> required = schema.getRequired() != null ? schema.getRequired() : List.of();

            builder.parameters(JsonObjectSchema.builder()
                    .addProperties(paramSchemas)
                    .required(required)
                    .build());
        }

        return builder.build();
    }

    /**
     * 将内部 Property 转换为 JsonSchemaElement
     */
    private static JsonSchemaElement convertProperty(ToolParameterSchema.Property prop) {
        return switch (prop.getType().toLowerCase()) {
            case "string" -> JsonStringSchema.builder()
                    .description(prop.getDescription())
                    .build();
            case "integer" -> JsonIntegerSchema.builder()
                    .description(prop.getDescription())
                    .build();
            case "number" -> JsonNumberSchema.builder()
                    .description(prop.getDescription())
                    .build();
            case "boolean" -> JsonBooleanSchema.builder()
                    .description(prop.getDescription())
                    .build();
            case "array" -> JsonArraySchema.builder()
                    .description(prop.getDescription())
                    .items(JsonStringSchema.builder().build())
                    .build();
            case "object" -> JsonObjectSchema.builder()
                    .description(prop.getDescription())
                    .build();
            default -> JsonStringSchema.builder()
                    .description(prop.getDescription())
                    .build();
        };
    }
}
