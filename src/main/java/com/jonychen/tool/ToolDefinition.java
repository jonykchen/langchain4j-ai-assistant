package com.jonychen.tool;

import java.time.Duration;
import java.util.List;

/**
 * 工具定义
 *
 * @param name              工具名称
 * @param description       功能描述
 * @param category          分类
 * @param parameters        参数 Schema
 * @param executor          执行器
 * @param requiredPermissions 所需权限
 * @param timeout           超时时间
 * @param maxRetries        最大重试次数
 * @author jonychen
 */
public record ToolDefinition(
        String name,
        String description,
        ToolCategory category,
        ToolParameterSchema parameters,
        ToolExecutor executor,
        List<String> requiredPermissions,
        Duration timeout,
        int maxRetries
) {
    /**
     * 创建简单工具定义
     */
    public static ToolDefinition of(String name, String description, ToolExecutor executor) {
        return new ToolDefinition(
                name, description, ToolCategory.CUSTOM,
                new ToolParameterSchema(), executor,
                List.of(), Duration.ofSeconds(30), 2
        );
    }

    /**
     * 创建带分类的工具定义
     */
    public static ToolDefinition of(String name, String description, ToolCategory category, ToolExecutor executor) {
        return new ToolDefinition(
                name, description, category,
                new ToolParameterSchema(), executor,
                List.of(), Duration.ofSeconds(30), 2
        );
    }

    /**
     * 构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String description;
        private ToolCategory category = ToolCategory.CUSTOM;
        private ToolParameterSchema parameters = new ToolParameterSchema();
        private ToolExecutor executor;
        private List<String> requiredPermissions = List.of();
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

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public ToolDefinition build() {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Tool name is required");
            }
            if (description == null || description.isBlank()) {
                throw new IllegalArgumentException("Tool description is required");
            }
            if (executor == null) {
                throw new IllegalArgumentException("Tool executor is required");
            }
            return new ToolDefinition(name, description, category, parameters, executor,
                    requiredPermissions, timeout, maxRetries);
        }
    }
}
