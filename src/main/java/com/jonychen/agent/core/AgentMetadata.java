package com.jonychen.agent.core;

import java.time.Duration;
import java.util.Set;

/**
 * Agent 元信息
 *
 * @param name Agent 名称标识
 * @param agentType Agent 类型枚举
 * @param displayName 显示名称
 * @param description 功能描述
 * @param version 版本号
 * @param capabilities 能力标签
 * @param requiredPermissions 需要的权限
 * @param maxIterations 最大迭代次数
 * @param timeout 执行超时
 * @param supportsStreaming 是否支持流式
 * @author jonychen
 */
public record AgentMetadata(
        String name,
        AgentType agentType,
        String displayName,
        String description,
        String version,
        Set<String> capabilities,
        Set<String> requiredPermissions,
        int maxIterations,
        Duration timeout,
        boolean supportsStreaming) {

    /** 创建运维助手元信息 */
    public static AgentMetadata ops() {
        return new AgentMetadata(
                "ops",
                AgentType.OPS,
                "运维助手",
                "智能运维：模型诊断与故障处置",
                "1.0.0",
                Set.of("model:read", "model:write", "circuit-breaker:read"),
                Set.of("ADMIN"),
                10,
                Duration.ofMinutes(5),
                true);
    }

    /** 创建数据分析助手元信息 */
    public static AgentMetadata data() {
        return new AgentMetadata(
                "data",
                AgentType.DATA,
                "数据分析助手",
                "自然语言查库、图表生成、数据导出",
                "1.0.0",
                Set.of("database:read", "chart:generate"),
                Set.of("USER"),
                5,
                Duration.ofMinutes(3),
                true);
    }

    /** 创建通用对话元信息 */
    public static AgentMetadata chat() {
        return new AgentMetadata(
                "chat",
                AgentType.CHAT,
                "通用对话",
                "普通问答、闲聊",
                "1.0.0",
                Set.of("chat"),
                Set.of(),
                5,
                Duration.ofMinutes(2),
                true);
    }
}
