package com.jonychen.tool.structured;

import java.util.Map;

/**
 * 输出 Schema 定义
 *
 * @author jonychen
 */
public record OutputSchema(
        String name,
        String description,
        SchemaType type,
        Map<String, PropertySchema> properties,
        java.util.List<String> required
) {
    /**
     * 创建对象类型 Schema
     */
    public static OutputSchema object(String name, String description,
                                       Map<String, PropertySchema> properties,
                                       java.util.List<String> required) {
        return new OutputSchema(name, description, SchemaType.OBJECT, properties, required);
    }

    /**
     * 创建简单类型 Schema
     */
    public static OutputSchema simple(String name, String description, SchemaType type) {
        return new OutputSchema(name, description, type, Map.of(), java.util.List.of());
    }

    /**
     * 转换为 JSON Schema Map
     */
    public Map<String, Object> toJsonSchemaMap() {
        Map<String, Object> schema = new java.util.LinkedHashMap<>();
        schema.put("name", name);
        schema.put("description", description);
        schema.put("type", type.getValue());

        if (type == SchemaType.OBJECT && properties != null && !properties.isEmpty()) {
            Map<String, Object> props = new java.util.LinkedHashMap<>();
            properties.forEach((k, v) -> props.put(k, v.toMap()));
            schema.put("properties", props);

            if (required != null && !required.isEmpty()) {
                schema.put("required", required);
            }
        }

        return schema;
    }

    /**
     * Schema 类型
     */
    public enum SchemaType {
        STRING("string"),
        INTEGER("integer"),
        NUMBER("number"),
        BOOLEAN("boolean"),
        ARRAY("array"),
        OBJECT("object");

        private final String value;

        SchemaType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * 属性 Schema
     */
    public record PropertySchema(
            String type,
            String description,
            java.util.List<String> enumValues,
            PropertySchema items,
            Map<String, PropertySchema> nestedProperties
    ) {
        /**
         * 字符串属性
         */
        public static PropertySchema string(String description) {
            return new PropertySchema("string", description, null, null, null);
        }

        /**
         * 整数属性
         */
        public static PropertySchema integer(String description) {
            return new PropertySchema("integer", description, null, null, null);
        }

        /**
         * 数字属性
         */
        public static PropertySchema number(String description) {
            return new PropertySchema("number", description, null, null, null);
        }

        /**
         * 布尔属性
         */
        public static PropertySchema bool(String description) {
            return new PropertySchema("boolean", description, null, null, null);
        }

        /**
         * 数组属性
         */
        public static PropertySchema array(String description, PropertySchema items) {
            return new PropertySchema("array", description, null, items, null);
        }

        /**
         * 枚举属性
         */
        public static PropertySchema enumOf(String description, java.util.List<String> values) {
            return new PropertySchema("string", description, values, null, null);
        }

        /**
         * 转换为 Map
         */
        public Map<String, Object> toMap() {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("type", type);
            map.put("description", description);

            if (enumValues != null && !enumValues.isEmpty()) {
                map.put("enum", enumValues);
            }
            if (items != null) {
                map.put("items", items.toMap());
            }
            if (nestedProperties != null && !nestedProperties.isEmpty()) {
                map.put("properties", nestedProperties.entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                Map.Entry::getKey,
                                e -> e.getValue().toMap()
                        )));
            }

            return map;
        }
    }
}