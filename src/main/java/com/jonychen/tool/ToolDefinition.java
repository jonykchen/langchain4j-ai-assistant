package com.jonychen.tool;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * 工具定义（增强版）
 *
 * <p>新增字段： - riskLevel: 风险等级（LOW/MEDIUM/HIGH/CRITICAL） - requiresConfirmation: 是否需要用户确认 -
 * allowedRoles: 允许执行该工具的角色列表
 *
 * @param name 工具名称
 * @param description 功能描述
 * @param category 分类
 * @param riskLevel 风险等级
 * @param parameters 参数 Schema
 * @param executor 执行器
 * @param requiredPermissions 所需权限
 * @param allowedRoles 允许的角色列表
 * @param requiresConfirmation 是否需要确认
 * @param timeout 超时时间
 * @param maxRetries 最大重试次数
 * @author jonychen
 */
public record ToolDefinition(
        String name,
        String description,
        ToolCategory category,
        RiskLevel riskLevel,
        ToolParameterSchema parameters,
        ToolExecutor executor,
        List<String> requiredPermissions,
        List<String> allowedRoles,
        boolean requiresConfirmation,
        Duration timeout,
        int maxRetries) {
    /** 创建简单工具定义（只读，低风险，无需确认） */
    public static ToolDefinition of(String name, String description, ToolExecutor executor) {
        return new ToolDefinition(
                name,
                description,
                ToolCategory.CUSTOM,
                RiskLevel.LOW,
                new ToolParameterSchema(),
                executor,
                List.of(),
                List.of(),
                false,
                Duration.ofSeconds(30),
                2);
    }

    /** 创建带分类的工具定义（只读，低风险，无需确认） */
    public static ToolDefinition of(
            String name, String description, ToolCategory category, ToolExecutor executor) {
        return new ToolDefinition(
                name,
                description,
                category,
                RiskLevel.LOW,
                new ToolParameterSchema(),
                executor,
                List.of(),
                List.of(),
                false,
                Duration.ofSeconds(30),
                2);
    }

    /** 创建只读工具（低风险，无需确认） */
    public static ToolDefinition readOnly(String name, String description, ToolExecutor executor) {
        return new ToolDefinition(
                name,
                description,
                ToolCategory.CUSTOM,
                RiskLevel.LOW,
                new ToolParameterSchema(),
                executor,
                List.of(),
                List.of(),
                false,
                Duration.ofSeconds(30),
                2);
    }

    /** 创建写入工具（需要确认，风险等级由调用方指定） */
    public static ToolDefinition writeOperation(
            String name, String description, ToolExecutor executor, RiskLevel riskLevel) {
        return new ToolDefinition(
                name,
                description,
                ToolCategory.CUSTOM,
                riskLevel,
                new ToolParameterSchema(),
                executor,
                List.of(),
                List.of(),
                true,
                Duration.ofSeconds(30),
                2);
    }

    /** 构建器 */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String description;
        private ToolCategory category = ToolCategory.CUSTOM;
        private RiskLevel riskLevel = RiskLevel.LOW;
        private ToolParameterSchema parameters = new ToolParameterSchema();
        private ToolExecutor executor;
        private List<String> requiredPermissions = List.of();
        private List<String> allowedRoles = List.of();
        private boolean requiresConfirmation = false;
        private Duration timeout = Duration.ofSeconds(30);
        private int maxRetries = 2;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder category(ToolCategory category) {
            this.category = category;
            return this;
        }

        public Builder riskLevel(RiskLevel riskLevel) {
            this.riskLevel = riskLevel;
            return this;
        }

        public Builder parameters(ToolParameterSchema parameters) {
            this.parameters = parameters;
            return this;
        }

        public Builder executor(ToolExecutor executor) {
            this.executor = executor;
            return this;
        }

        public Builder requiredPermissions(List<String> permissions) {
            this.requiredPermissions = permissions;
            return this;
        }

        public Builder allowedRoles(List<String> roles) {
            this.allowedRoles = roles;
            return this;
        }

        public Builder requiresConfirmation(boolean requires) {
            this.requiresConfirmation = requires;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public ToolDefinition build() {
            Objects.requireNonNull(name, "Tool name is required");
            Objects.requireNonNull(description, "Tool description is required");
            Objects.requireNonNull(executor, "Tool executor is required");

            // HIGH 和 CRITICAL 级别自动设置为需要确认
            if (riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.CRITICAL) {
                this.requiresConfirmation = true;
            }

            return new ToolDefinition(
                    name,
                    description,
                    category,
                    riskLevel,
                    parameters,
                    executor,
                    requiredPermissions,
                    allowedRoles,
                    requiresConfirmation,
                    timeout,
                    maxRetries);
        }
    }
}
