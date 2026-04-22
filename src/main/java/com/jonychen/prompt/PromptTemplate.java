package com.jonychen.prompt;

import java.util.List;
import java.util.Map;

/** Prompt 模板定义 */
public record PromptTemplate(
        /** 模板名称 */
        String name,

        /** 版本号 */
        String version,

        /** 描述 */
        String description,

        /** 模板内容 */
        String template,

        /** 变量定义列表 */
        List<TemplateVariable> variables,

        /** Few-shot 示例 */
        List<FewShotExample> examples,

        /** 元数据 */
        PromptMetadata metadata) {
    /** 渲染模板 将变量值替换到模板中 */
    public String render(Map<String, Object> variables) {
        String result = template;

        // 替换简单变量
        if (variables != null) {
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                String placeholder = "${" + entry.getKey() + "}";
                String value = entry.getValue() != null ? String.valueOf(entry.getValue()) : "";
                result = result.replace(placeholder, value);
            }
        }

        // 替换默认值（如果有变量未提供）
        for (TemplateVariable var : this.variables) {
            String placeholder = "${" + var.name() + "}";
            if (result.contains(placeholder) && var.defaultValue() != null) {
                result = result.replace(placeholder, var.defaultValue());
            }
        }

        return result;
    }

    /** 验证变量是否满足要求 */
    public boolean validateVariables(Map<String, Object> variables) {
        if (this.variables == null) {
            return true;
        }

        for (TemplateVariable var : this.variables) {
            if (var.required()) {
                Object value = variables != null ? variables.get(var.name()) : null;
                if (value == null && var.defaultValue() == null) {
                    return false;
                }
            }
        }

        return true;
    }
}
