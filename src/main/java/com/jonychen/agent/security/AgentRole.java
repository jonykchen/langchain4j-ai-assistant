package com.jonychen.agent.security;

import java.util.Set;

/**
 * Agent 角色枚举
 *
 * <p>定义 Agent 系统的角色及其权限映射。 每个角色拥有一组预定义的权限，用于控制 Agent 和工具的访问。
 *
 * <p>角色层级：
 *
 * <ul>
 *   <li>ADMIN - 管理员，拥有所有权限，可执行运维、Prompt 工程等敏感操作
 *   <li>USER - 普通用户，拥有只读和通用对话权限
 * </ul>
 *
 * @author jonychen
 */
public enum AgentRole {

    /** 管理员 - 拥有所有权限 */
    ADMIN(
            Set.of(
                    AgentPermission.AGENT_OPS_EXECUTE,
                    AgentPermission.MODEL_READ,
                    AgentPermission.MODEL_WRITE,
                    AgentPermission.CIRCUIT_BREAKER_READ,
                    AgentPermission.CIRCUIT_BREAKER_WRITE,
                    AgentPermission.AGENT_DATA_EXECUTE,
                    AgentPermission.DATABASE_READ,
                    AgentPermission.CHART_GENERATE,
                    AgentPermission.AGENT_PROMPT_EXECUTE,
                    AgentPermission.PROMPT_READ,
                    AgentPermission.PROMPT_WRITE,
                    AgentPermission.EVALUATION_RUN,
                    AgentPermission.AGENT_TEST_EXECUTE,
                    AgentPermission.CODE_READ,
                    AgentPermission.TEST_CREATE,
                    AgentPermission.TEST_RUN,
                    AgentPermission.CHAT)),

    /** 普通用户 - 拥有只读和通用对话权限 */
    USER(
            Set.of(
                    AgentPermission.AGENT_DATA_EXECUTE,
                    AgentPermission.DATABASE_READ,
                    AgentPermission.CHART_GENERATE,
                    AgentPermission.CHAT));

    private final Set<AgentPermission> permissions;

    AgentRole(Set<AgentPermission> permissions) {
        this.permissions = permissions;
    }

    /**
     * 获取角色拥有的权限集合
     *
     * @return 权限集合
     */
    public Set<AgentPermission> getPermissions() {
        return permissions;
    }

    /**
     * 判断角色是否拥有指定权限
     *
     * @param permission 权限
     * @return 是否拥有
     */
    public boolean hasPermission(AgentPermission permission) {
        return permissions.contains(permission);
    }

    /**
     * 根据角色名称获取枚举值（大小写不敏感）
     *
     * @param name 角色名称
     * @return 角色枚举，未匹配则返回 USER
     */
    public static AgentRole fromName(String name) {
        if (name == null) {
            return USER;
        }
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return USER;
        }
    }
}
