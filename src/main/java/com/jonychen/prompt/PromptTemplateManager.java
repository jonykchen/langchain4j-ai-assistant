package com.jonychen.prompt;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Prompt 模板管理器接口
 */
public interface PromptTemplateManager {

    /**
     * 加载模板
     *
     * @param name 模板名称
     * @return 模板定义（如果存在）
     */
    Optional<PromptTemplate> loadTemplate(String name);

    /**
     * 加载指定版本的模板
     *
     * @param name 模板名称
     * @param version 版本号
     * @return 模板定义（如果存在）
     */
    Optional<PromptTemplate> loadTemplate(String name, String version);

    /**
     * 渲染模板
     *
     * @param templateName 模板名称
     * @param variables 变量值映射
     * @return 渲染后的文本
     */
    String render(String templateName, Map<String, Object> variables);

    /**
     * 渲染模板（带验证）
     *
     * @param templateName 模板名称
     * @param variables 变量值映射
     * @param validate 是否验证必需变量
     * @return 渲染后的文本
     */
    String render(String templateName, Map<String, Object> variables, boolean validate);

    /**
     * 保存模板
     *
     * @param template 模板定义
     */
    void saveTemplate(PromptTemplate template);

    /**
     * 列出所有模板
     *
     * @return 模板信息列表
     */
    List<TemplateInfo> listTemplates();

    /**
     * 删除模板
     *
     * @param name 模板名称
     */
    void deleteTemplate(String name);

    /**
     * 检查模板是否存在
     *
     * @param name 模板名称
     * @return 是否存在
     */
    boolean existsTemplate(String name);

    /**
     * 模板简要信息
     */
    record TemplateInfo(
            String name,
            String version,
            String description,
            LocalDateTime updatedAt
    ) {}
}