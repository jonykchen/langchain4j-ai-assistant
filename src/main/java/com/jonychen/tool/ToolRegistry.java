package com.jonychen.tool;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.langchain4j.agent.tool.ToolSpecification;

/**
 * 工具注册中心接口
 *
 * @author jonychen
 */
public interface ToolRegistry {

    /**
     * 注册工具
     *
     * @param tool 工具定义
     */
    void register(ToolDefinition tool);

    /**
     * 通过注解自动注册工具
     *
     * @param toolBean 包含 @AgentTool 注解方法的 Bean
     */
    void registerAnnotatedTools(Object toolBean);

    /**
     * 注销工具
     *
     * @param toolName 工具名称
     */
    void unregister(String toolName);

    /**
     * 获取工具定义
     *
     * @param toolName 工具名称
     * @return 工具定义（可选）
     */
    Optional<ToolDefinition> getTool(String toolName);

    /**
     * 检查工具是否存在
     *
     * @param toolName 工具名称
     * @return 是否存在
     */
    boolean hasTool(String toolName);

    /**
     * 获取所有工具名称
     *
     * @return 工具名称列表
     */
    List<String> getToolNames();

    /**
     * 获取所有工具定义
     *
     * @return 工具定义列表
     */
    List<ToolDefinition> getAllTools();

    /**
     * 获取工具规范（LangChain4j 格式）
     *
     * @return 工具规范列表
     */
    List<ToolSpecification> getToolSpecifications();

    /**
     * 获取指定工具的规范
     *
     * @param toolName 工具名称
     * @return 工具规范（可选）
     */
    Optional<ToolSpecification> getToolSpecification(String toolName);

    /**
     * 执行工具
     *
     * @param toolName 工具名称
     * @param params 参数
     * @return 执行结果
     */
    ToolResult execute(String toolName, Map<String, Object> params);

    /**
     * 按分类获取工具
     *
     * @param category 工具分类
     * @return 工具定义列表
     */
    List<ToolDefinition> getToolsByCategory(ToolCategory category);

    /**
     * 按权限获取可用工具
     *
     * @param permissions 用户拥有的权限
     * @return 可用工具定义列表
     */
    List<ToolDefinition> getToolsByPermissions(List<String> permissions);

    /**
     * 获取工具数量
     *
     * @return 工具数量
     */
    int size();

    /**
     * 获取指定工具的统计数据
     *
     * @param toolName 工具名称
     * @return 工具统计信息
     */
    ToolStatistics getStatistics(String toolName);

    /**
     * 获取所有工具的统计数据
     *
     * @return 工具统计列表
     */
    List<ToolStatistics> getAllStatistics();

    /**
     * 重置指定工具的统计数据
     *
     * @param toolName 工具名称
     */
    void resetStatistics(String toolName);

    /** 重置所有工具的统计数据 */
    void resetAllStatistics();
}
