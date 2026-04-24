package com.jonychen.agent.security;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.jonychen.agent.core.AgentMetadata;
import com.jonychen.agent.core.AgentType;
import com.jonychen.tool.RiskLevel;
import com.jonychen.tool.ToolDefinition;

/**
 * Agent 权限校验服务
 *
 * <p>提供 Agent 和工具级别的权限校验，基于 RBAC 模型。 核心职责：
 *
 * <ul>
 *   <li>判断用户角色是否可以执行指定 Agent
 *   <li>判断用户角色是否可以执行指定工具
 *   <li>判断工具是否需要用户确认（基于风险等级和执行选项）
 * </ul>
 *
 * @author jonychen
 */
@Service
public class AgentPermissionService {

    private static final Logger log = LoggerFactory.getLogger(AgentPermissionService.class);

    /**
     * 判断用户角色是否可以执行指定 Agent
     *
     * <p>检查逻辑：根据 Agent 类型确定所需权限，判断角色是否拥有该权限。
     *
     * @param role 用户角色
     * @param metadata Agent 元信息
     * @return 是否可以执行
     */
    public boolean canExecuteAgent(AgentRole role, AgentMetadata metadata) {
        if (metadata == null) {
            log.warn("[AgentPermission] Agent 元信息为空，拒绝执行");
            return false;
        }

        // 检查 Agent 类型对应的执行权限
        AgentPermission requiredPermission = getExecutePermission(metadata.agentType());
        if (requiredPermission == null) {
            // 无需特殊权限的 Agent 类型（如 CHAT）
            return true;
        }

        boolean allowed = role.hasPermission(requiredPermission);
        if (!allowed) {
            log.warn(
                    "[AgentPermission] 权限不足: role={}, agent={}, required={}",
                    role,
                    metadata.name(),
                    requiredPermission);
        }
        return allowed;
    }

    /**
     * 判断用户角色是否可以执行指定工具
     *
     * <p>检查逻辑：如果工具定义了 allowedRoles，则用户角色必须在其中； 否则根据工具风险等级判断。
     *
     * @param role 用户角色
     * @param tool 工具定义
     * @return 是否可以执行
     */
    public boolean canExecuteTool(AgentRole role, ToolDefinition tool) {
        if (tool == null) {
            return false;
        }

        List<String> allowedRoles = tool.allowedRoles();

        // 如果工具未限制角色，允许所有角色执行
        if (allowedRoles == null || allowedRoles.isEmpty()) {
            return true;
        }

        // 检查用户角色是否在允许列表中
        boolean allowed = allowedRoles.contains(role.name());
        if (!allowed) {
            log.warn(
                    "[AgentPermission] 工具权限不足: role={}, tool={}, allowedRoles={}",
                    role,
                    tool.name(),
                    allowedRoles);
        }
        return allowed;
    }

    /**
     * 判断工具是否需要用户确认
     *
     * <p>确认条件：
     *
     * <ol>
     *   <li>工具标记了 requiresConfirmation=true
     *   <li>工具风险等级为 HIGH 或 CRITICAL
     *   <li>执行选项开启了 requireConfirmation
     * </ol>
     *
     * @param tool 工具定义
     * @param requireConfirmation 执行选项中是否需要确认
     * @return 是否需要确认
     */
    public boolean requiresConfirmation(ToolDefinition tool, boolean requireConfirmation) {
        if (!requireConfirmation) {
            return false;
        }
        if (tool.requiresConfirmation()) {
            return true;
        }
        // HIGH 和 CRITICAL 风险等级自动需要确认
        return tool.riskLevel() == RiskLevel.HIGH || tool.riskLevel() == RiskLevel.CRITICAL;
    }

    /**
     * 根据 Agent 类型获取执行权限
     *
     * @param agentType Agent 类型
     * @return 所需权限，null 表示无需特殊权限
     */
    private AgentPermission getExecutePermission(AgentType agentType) {
        return switch (agentType) {
            case OPS -> AgentPermission.AGENT_OPS_EXECUTE;
            case DATA -> AgentPermission.AGENT_DATA_EXECUTE;
            case PROMPT -> AgentPermission.AGENT_PROMPT_EXECUTE;
            case TEST -> AgentPermission.AGENT_TEST_EXECUTE;
            case ROUTER, CHAT -> null;
        };
    }
}
