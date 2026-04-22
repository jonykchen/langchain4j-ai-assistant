package com.jonychen.prompt;

/** 模板变量定义 */
public record TemplateVariable(
        /** 变量名称 */
        String name,

        /** 变量描述 */
        String description,

        /** 是否必需 */
        boolean required,

        /** 默认值 */
        String defaultValue,

        /** 变量类型 */
        String type) {
    /** 创建必需变量 */
    public static TemplateVariable required(String name, String description) {
        return new TemplateVariable(name, description, true, null, "string");
    }

    /** 创建可选变量 */
    public static TemplateVariable optional(String name, String description, String defaultValue) {
        return new TemplateVariable(name, description, false, defaultValue, "string");
    }
}
