package com.jonychen.agent.core;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 执行请求
 *
 * <p>封装用户对 Agent 的执行请求，包含会话信息、用户输入和执行选项。 通过 clientIp 和 userAgent 支持审计日志，避免异步线程依赖
 * HttpServletRequest。
 *
 * @param sessionId 会话 ID，用于关联同一会话的多次请求
 * @param userId 用户 ID，用于权限校验和审计
 * @param userInput 用户输入，Agent 需要处理的问题或任务
 * @param params 额外参数，用于传递上下文信息
 * @param options 执行选项，控制迭代次数、超时等
 * @param clientIp 客户端 IP（用于审计日志）
 * @param userAgent 客户端 UA（用于审计日志）
 * @author jonychen
 */
public record AgentRequest(
        String sessionId,
        String userId,
        String userInput,
        Map<String, Object> params,
        AgentRequestOptions options,
        String clientIp,
        String userAgent) {

    /**
     * 创建简单请求（自动生成会话 ID，使用默认选项）
     *
     * @param userInput 用户输入
     * @param userId 用户 ID
     * @return Agent 请求
     */
    public static AgentRequest of(String userInput, String userId) {
        return new AgentRequest(
                UUID.randomUUID().toString(),
                userId,
                userInput,
                Collections.emptyMap(),
                AgentRequestOptions.defaults(),
                null,
                null);
    }

    /**
     * 创建带审计信息的请求
     *
     * @param userInput 用户输入
     * @param userId 用户 ID
     * @param clientIp 客户端 IP
     * @param userAgent 客户端 UA
     * @return Agent 请求
     */
    public static AgentRequest of(
            String userInput, String userId, String clientIp, String userAgent) {
        return new AgentRequest(
                UUID.randomUUID().toString(),
                userId,
                userInput,
                Collections.emptyMap(),
                AgentRequestOptions.defaults(),
                clientIp,
                userAgent);
    }

    /**
     * 创建带自定义选项的请求
     *
     * @param userInput 用户输入
     * @param userId 用户 ID
     * @param options 执行选项
     * @return Agent 请求
     */
    public static AgentRequest of(String userInput, String userId, AgentRequestOptions options) {
        return new AgentRequest(
                UUID.randomUUID().toString(),
                userId,
                userInput,
                Collections.emptyMap(),
                options,
                null,
                null);
    }

    /**
     * 创建完整请求（所有参数）
     *
     * @param sessionId 会话 ID
     * @param userId 用户 ID
     * @param userInput 用户输入
     * @param params 额外参数
     * @param options 执行选项
     * @param clientIp 客户端 IP
     * @param userAgent 客户端 UA
     * @return Agent 请求
     */
    public static AgentRequest of(
            String sessionId,
            String userId,
            String userInput,
            Map<String, Object> params,
            AgentRequestOptions options,
            String clientIp,
            String userAgent) {
        return new AgentRequest(sessionId, userId, userInput, params, options, clientIp, userAgent);
    }
}
