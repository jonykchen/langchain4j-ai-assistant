package com.jonychen.tool;

import dev.langchain4j.agent.tool.ToolSpecification;
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
        if (schema != null && !schema.getProperties().isEmpty()) {
            for (Map.Entry<String, ToolParameterSchema.Property> entry : schema.getProperties().entrySet()) {
                String paramName = entry.getKey();
                ToolParameterSchema.Property prop = entry.getValue();

                builder.addParameter(paramName, p -> {
                    p.description(prop.getDescription());
                    p.required(schema.getRequired().contains(paramName));

                    // 类型转换
                    switch (prop.getType()) {
                        case "string" -> p.type(dev.langchain4j.agent.tool.JsonSchemaProperty.STRING);
                        case "integer" -> p.type(dev.langchain4j.agent.tool.JsonSchemaProperty.INTEGER);
                        case "number" -> p.type(dev.langchain4j.agent.tool.JsonSchemaProperty.NUMBER);
                        case "boolean" -> p.type(dev.langchain4j.agent.tool.JsonSchemaProperty.BOOLEAN);
                        case "array" -> p.type(dev.langchain4j.agent.tool.JsonSchemaProperty.ARRAY);
                        case "object" -> p.type(dev.langchain4j.agent.tool.JsonSchemaProperty.OBJECT);
                        default -> p.type(dev.langchain4j.agent.tool.JsonSchemaProperty.STRING);
                    }
                });
            }
        }

        return builder.build();
    }
}
