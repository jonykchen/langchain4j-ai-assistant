package com.jonychen.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具参数 Schema 定义
 *
 * @author jonychen
 */
public class ToolParameterSchema {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String type;
    private final Map<String, Property> properties;
    private final List<String> required;

    public ToolParameterSchema() {
        this.type = "object";
        this.properties = new HashMap<>();
        this.required = new ArrayList<>();
    }

    public ToolParameterSchema(Map<String, Property> properties, List<String> required) {
        this.type = "object";
        this.properties = properties;
        this.required = required;
    }

    /**
     * 添加属性
     */
    public ToolParameterSchema addProperty(String name, Property property) {
        properties.put(name, property);
        return this;
    }

    /**
     * 添加必需属性
     */
    public ToolParameterSchema addRequired(String name) {
        if (!required.contains(name)) {
            required.add(name);
        }
        return this;
    }

    /**
     * 转换为 JSON Schema Map
     */
    public Map<String, Object> toSchemaMap() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", type);

        Map<String, Object> props = new HashMap<>();
        properties.forEach((name, prop) -> props.put(name, prop.toMap()));
        schema.put("properties", props);

        if (!required.isEmpty()) {
            schema.put("required", required);
        }

        return schema;
    }

    /**
     * 转换为 JSON 字符串
     */
    public String toJsonSchema() {
        try {
            return OBJECT_MAPPER.writeValueAsString(toSchemaMap());
        } catch (Exception e) {
            return "{}";
        }
    }

    public Map<String, Property> getProperties() {
        return properties;
    }

    public List<String> getRequired() {
        return required;
    }

    /**
     * 属性定义
     */
    public static class Property {
        private final String type;
        private final String description;
        private final List<String> enumValues;
        private final Object defaultValue;

        public Property(String type, String description) {
            this(type, description, null, null);
        }

        public Property(String type, String description, List<String> enumValues, Object defaultValue) {
            this.type = type;
            this.description = description;
            this.enumValues = enumValues;
            this.defaultValue = defaultValue;
        }

        public static Property string(String description) {
            return new Property("string", description);
        }

        public static Property integer(String description) {
            return new Property("integer", description);
        }

        public static Property number(String description) {
            return new Property("number", description);
        }

        public static Property bool(String description) {
            return new Property("boolean", description);
        }

        public static Property array(String description) {
            return new Property("array", description);
        }

        public static Property object(String description) {
            return new Property("object", description);
        }

        public Property withEnum(List<String> enumValues) {
            return new Property(type, description, enumValues, defaultValue);
        }

        public Property withDefault(Object defaultValue) {
            return new Property(type, description, enumValues, defaultValue);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("type", type);
            map.put("description", description);
            if (enumValues != null && !enumValues.isEmpty()) {
                map.put("enum", enumValues);
            }
            if (defaultValue != null) {
                map.put("default", defaultValue);
            }
            return map;
        }

        public String getType() {
            return type;
        }

        public String getDescription() {
            return description;
        }
    }
}
