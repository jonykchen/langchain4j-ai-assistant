package com.jonychen.tool.structured;

/**
 * 结构化输出配置
 *
 * @param schema              输出 Schema
 * @param maxRetries          最大重试次数
 * @param validateOnParse     解析时是否验证
 * @param strictMode          严格模式（严格类型检查）
 * @param fallbackToJson      失败时是否回退到 JSON
 * @author jonychen
 */
public record StructuredOutputConfig(
        OutputSchema schema,
        int maxRetries,
        boolean validateOnParse,
        boolean strictMode,
        boolean fallbackToJson
) {
    /**
     * 默认配置
     */
    public static StructuredOutputConfig defaultConfig(OutputSchema schema) {
        return new StructuredOutputConfig(schema, 3, true, false, true);
    }

    /**
     * 严格配置
     */
    public static StructuredOutputConfig strict(OutputSchema schema) {
        return new StructuredOutputConfig(schema, 3, true, true, false);
    }

    /**
     * 宽松配置
     */
    public static StructuredOutputConfig lenient(OutputSchema schema) {
        return new StructuredOutputConfig(schema, 1, false, false, true);
    }

    /**
     * 构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private OutputSchema schema;
        private int maxRetries = 3;
        private boolean validateOnParse = true;
        private boolean strictMode = false;
        private boolean fallbackToJson = true;

        public Builder schema(OutputSchema schema) {
            this.schema = schema;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder validateOnParse(boolean validate) {
            this.validateOnParse = validate;
            return this;
        }

        public Builder strictMode(boolean strict) {
            this.strictMode = strict;
            return this;
        }

        public Builder fallbackToJson(boolean fallback) {
            this.fallbackToJson = fallback;
            return this;
        }

        public StructuredOutputConfig build() {
            if (schema == null) {
                throw new IllegalArgumentException("Schema is required");
            }
            return new StructuredOutputConfig(schema, maxRetries, validateOnParse, strictMode, fallbackToJson);
        }
    }
}