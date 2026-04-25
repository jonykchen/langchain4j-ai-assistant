package com.jonychen.tool.builtin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.jonychen.observability.prompt.PromptTemplateEntity;
import com.jonychen.observability.prompt.PromptVersionService;
import com.jonychen.tool.AgentTool;
import com.jonychen.tool.ToolCategory;
import com.jonychen.tool.ToolResult;

/**
 * Prompt 工程工具集
 *
 * <p>提供 Prompt 模板管理和优化的工具：
 *
 * <ul>
 *   <li>list_prompt_templates - 列出所有 Prompt 模板
 *   <li>get_prompt_template - 获取指定模板详情
 *   <li>get_prompt_versions - 获取模板版本历史
 *   <li>create_prompt_version - 创建新版本
 *   <li>compare_prompt_versions - 比较两个版本差异
 *   <li>activate_prompt_version - 激活指定版本
 *   <li>configure_ab_test - 配置 A/B 测试
 * </ul>
 *
 * <h2>使用场景</h2>
 *
 * <p>PromptAgent 通过这些工具帮助用户：
 *
 * <ul>
 *   <li>管理 Prompt 模板版本
 *   <li>优化 Prompt 效果
 *   <li>配置 A/B 测试对比效果
 *   <li>回滚到历史版本
 * </ul>
 *
 * @author jonychen
 */
@Component
public class PromptTools {

    private static final Logger log = LoggerFactory.getLogger(PromptTools.class);

    private final PromptVersionService promptVersionService;

    public PromptTools(PromptVersionService promptVersionService) {
        this.promptVersionService = promptVersionService;
        log.info("[PromptTools] 初始化完成");
    }

    /**
     * 列出所有 Prompt 模板名称
     *
     * @return 工具结果
     */
    @AgentTool(
            name = "list_prompt_templates",
            description = "列出所有 Prompt 模板名称",
            category = ToolCategory.PROMPT)
    public ToolResult listTemplates() {
        log.info("[PromptTools] 执行 list_prompt_templates");

        List<String> names = promptVersionService.getAllTemplateNames();

        Map<String, Object> data = new HashMap<>();
        data.put("templates", names);
        data.put("count", names.size());

        return ToolResult.success(data);
    }

    /**
     * 获取 Prompt 模板详情
     *
     * @param name 模板名称
     * @return 工具结果
     */
    @AgentTool(
            name = "get_prompt_template",
            description = "获取指定 Prompt 模板的详细信息（当前激活版本）",
            category = ToolCategory.PROMPT)
    public ToolResult getTemplate(String name) {
        log.info("[PromptTools] 执行 get_prompt_template: name={}", name);

        if (name == null || name.isBlank()) {
            return ToolResult.failure("模板名称不能为空");
        }

        var template = promptVersionService.getActiveVersion(name).orElse(null);

        if (template == null) {
            return ToolResult.failure("模板不存在: " + name);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("name", template.getName());
        data.put("version", template.getVersion());
        data.put("description", template.getDescription());
        data.put("content", template.getContent());
        data.put("variables", template.getVariables());
        data.put("tags", template.getTags());
        data.put("active", template.getActive());
        data.put("production", template.getProduction());
        data.put("totalUses", template.getTotalUses());

        return ToolResult.success(data);
    }

    /**
     * 获取模板版本历史
     *
     * @param name 模板名称
     * @return 工具结果
     */
    @AgentTool(
            name = "get_prompt_versions",
            description = "获取指定 Prompt 模板的版本历史",
            category = ToolCategory.PROMPT)
    public ToolResult getVersions(String name) {
        log.info("[PromptTools] 执行 get_prompt_versions: name={}", name);

        if (name == null || name.isBlank()) {
            return ToolResult.failure("模板名称不能为空");
        }

        List<PromptTemplateEntity> versions = promptVersionService.getVersionHistory(name);

        List<Map<String, Object>> versionList =
                versions.stream()
                        .map(
                                v -> {
                                    Map<String, Object> info = new HashMap<>();
                                    info.put("version", v.getVersion());
                                    info.put("description", v.getDescription());
                                    info.put("active", v.getActive());
                                    info.put("production", v.getProduction());
                                    info.put("totalUses", v.getTotalUses());
                                    info.put("createdAt", v.getCreatedAt());
                                    return info;
                                })
                        .toList();

        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("versions", versionList);
        data.put("count", versionList.size());

        return ToolResult.success(data);
    }

    /**
     * 创建 Prompt 新版本
     *
     * @param name 模板名称
     * @param content 新版本内容
     * @param changeDescription 变更描述
     * @return 工具结果
     */
    @AgentTool(
            name = "create_prompt_version",
            description = "为 Prompt 模板创建新版本",
            category = ToolCategory.PROMPT)
    public ToolResult createVersion(String name, String content, String changeDescription) {
        log.info("[PromptTools] 执行 create_prompt_version: name={}", name);

        if (name == null || name.isBlank()) {
            return ToolResult.failure("模板名称不能为空");
        }

        if (content == null || content.isBlank()) {
            return ToolResult.failure("模板内容不能为空");
        }

        try {
            PromptTemplateEntity newVersion =
                    promptVersionService.createVersion(name, content, changeDescription);

            Map<String, Object> data = new HashMap<>();
            data.put("name", newVersion.getName());
            data.put("version", newVersion.getVersion());
            data.put("description", newVersion.getDescription());

            return ToolResult.success(data);
        } catch (Exception e) {
            log.error("[PromptTools] 创建版本失败: {}", e.getMessage());
            return ToolResult.failure("创建版本失败: " + e.getMessage());
        }
    }

    /**
     * 比较两个版本差异
     *
     * @param name 模板名称
     * @param version1 版本1
     * @param version2 版本2
     * @return 工具结果
     */
    @AgentTool(
            name = "compare_prompt_versions",
            description = "比较 Prompt 模板的两个版本差异",
            category = ToolCategory.PROMPT)
    public ToolResult compareVersions(String name, String version1, String version2) {
        log.info(
                "[PromptTools] 执行 compare_prompt_versions: name={}, v1={}, v2={}",
                name,
                version1,
                version2);

        if (name == null || name.isBlank()) {
            return ToolResult.failure("模板名称不能为空");
        }

        if (version1 == null || version2 == null) {
            return ToolResult.failure("版本号不能为空");
        }

        try {
            var diff = promptVersionService.compareVersions(name, version1, version2);

            Map<String, Object> data = new HashMap<>();
            data.put("name", diff.name());
            data.put("version1", diff.version1());
            data.put("version2", diff.version2());
            data.put("changes", diff.changes());

            return ToolResult.success(data);
        } catch (Exception e) {
            log.error("[PromptTools] 比较版本失败: {}", e.getMessage());
            return ToolResult.failure("比较版本失败: " + e.getMessage());
        }
    }

    /**
     * 激活指定版本
     *
     * @param name 模板名称
     * @param version 版本号
     * @return 工具结果
     */
    @AgentTool(
            name = "activate_prompt_version",
            description = "激活 Prompt 模板的指定版本",
            category = ToolCategory.PROMPT)
    public ToolResult activateVersion(String name, String version) {
        log.info("[PromptTools] 执行 activate_prompt_version: name={}, version={}", name, version);

        if (name == null || name.isBlank()) {
            return ToolResult.failure("模板名称不能为空");
        }

        if (version == null || version.isBlank()) {
            return ToolResult.failure("版本号不能为空");
        }

        try {
            promptVersionService.activateVersion(name, version);

            return ToolResult.success(Map.of("message", "已激活版本 " + name + "@" + version));
        } catch (Exception e) {
            log.error("[PromptTools] 激活版本失败: {}", e.getMessage());
            return ToolResult.failure("激活版本失败: " + e.getMessage());
        }
    }

    /**
     * 回滚到指定版本
     *
     * @param name 模板名称
     * @param targetVersion 目标版本
     * @return 工具结果
     */
    @AgentTool(
            name = "rollback_prompt_version",
            description = "回滚 Prompt 模板到指定版本",
            category = ToolCategory.PROMPT)
    public ToolResult rollbackVersion(String name, String targetVersion) {
        log.info(
                "[PromptTools] 执行 rollback_prompt_version: name={}, target={}",
                name,
                targetVersion);

        if (name == null || name.isBlank()) {
            return ToolResult.failure("模板名称不能为空");
        }

        if (targetVersion == null || targetVersion.isBlank()) {
            return ToolResult.failure("目标版本号不能为空");
        }

        try {
            promptVersionService.rollback(name, targetVersion);

            return ToolResult.success(Map.of("message", "已回滚到版本 " + name + "@" + targetVersion));
        } catch (Exception e) {
            log.error("[PromptTools] 回滚版本失败: {}", e.getMessage());
            return ToolResult.failure("回滚版本失败: " + e.getMessage());
        }
    }

    /**
     * 配置 A/B 测试
     *
     * @param name 模板名称
     * @param baselineVersion 基线版本
     * @param variantVersion 变体版本
     * @param trafficPercentage 变体流量百分比
     * @return 工具结果
     */
    @AgentTool(
            name = "configure_ab_test",
            description = "为 Prompt 模板配置 A/B 测试",
            category = ToolCategory.PROMPT)
    public ToolResult configureABTest(
            String name, String baselineVersion, String variantVersion, double trafficPercentage) {
        log.info(
                "[PromptTools] 执行 configure_ab_test: name={}, baseline={}, variant={}, traffic={}%",
                name, baselineVersion, variantVersion, trafficPercentage);

        if (name == null || name.isBlank()) {
            return ToolResult.failure("模板名称不能为空");
        }

        if (baselineVersion == null || variantVersion == null) {
            return ToolResult.failure("基线版本和变体版本不能为空");
        }

        if (trafficPercentage < 1 || trafficPercentage > 99) {
            return ToolResult.failure("流量百分比必须在 1-99 之间");
        }

        try {
            promptVersionService.configureABTest(
                    name,
                    new PromptVersionService.ABTestConfigRequest(
                            baselineVersion, variantVersion, "variant", trafficPercentage));

            return ToolResult.success(
                    Map.of(
                            "message",
                            "已配置 A/B 测试",
                            "baseline",
                            baselineVersion,
                            "variant",
                            variantVersion,
                            "trafficSplit",
                            (100 - trafficPercentage) + "/" + trafficPercentage));
        } catch (Exception e) {
            log.error("[PromptTools] 配置 A/B 测试失败: {}", e.getMessage());
            return ToolResult.failure("配置 A/B 测试失败: " + e.getMessage());
        }
    }

    /**
     * 停止 A/B 测试
     *
     * @param name 模板名称
     * @return 工具结果
     */
    @AgentTool(
            name = "stop_ab_test",
            description = "停止 Prompt 模板的 A/B 测试",
            category = ToolCategory.PROMPT)
    public ToolResult stopABTest(String name) {
        log.info("[PromptTools] 执行 stop_ab_test: name={}", name);

        if (name == null || name.isBlank()) {
            return ToolResult.failure("模板名称不能为空");
        }

        try {
            promptVersionService.stopABTest(name);

            return ToolResult.success(Map.of("message", "已停止 A/B 测试"));
        } catch (Exception e) {
            log.error("[PromptTools] 停止 A/B 测试失败: {}", e.getMessage());
            return ToolResult.failure("停止 A/B 测试失败: " + e.getMessage());
        }
    }
}
