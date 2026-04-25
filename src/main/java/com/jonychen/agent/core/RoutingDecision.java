package com.jonychen.agent.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * 路由决策结构化输出
 *
 * <p>用于 LLM 返回路由决策的结构化数据，通过 JSON Schema 约束输出格式。
 *
 * <h2>字段说明</h2>
 *
 * <ul>
 *   <li>targetAgent - 目标 Agent 名称（ops, data, chat 等）
 *   <li>confidence - 置信度分数（0.0-1.0）
 *   <li>reason - 选择理由（用于用户确认时展示）
 *   <li>alternativeAgent - 备选 Agent（置信度较低时的替代选择）
 * </ul>
 *
 * @author jonychen
 */
public record RoutingDecision(
        @JsonProperty(required = true)
                @JsonPropertyDescription("选中的目标 Agent 名称，必须是以下之一: ops, data, chat")
                String targetAgent,
        @JsonProperty(required = true) @JsonPropertyDescription("路由置信度分数，范围 0.0-1.0，表示对选择的确定程度")
                double confidence,
        @JsonProperty(required = true) @JsonPropertyDescription("选择该 Agent 的理由，简短说明为什么选择它")
                String reason,
        @JsonProperty @JsonPropertyDescription("备选 Agent 名称，当置信度较低时可考虑") String alternativeAgent) {

    /**
     * 创建路由决策
     *
     * @param targetAgent 目标 Agent
     * @param confidence 置信度
     * @param reason 理由
     * @return 路由决策
     */
    public static RoutingDecision of(String targetAgent, double confidence, String reason) {
        return new RoutingDecision(targetAgent, confidence, reason, null);
    }

    /**
     * 创建带备选的路由决策
     *
     * @param targetAgent 目标 Agent
     * @param confidence 置信度
     * @param reason 理由
     * @param alternativeAgent 备选 Agent
     * @return 路由决策
     */
    public static RoutingDecision of(
            String targetAgent, double confidence, String reason, String alternativeAgent) {
        return new RoutingDecision(targetAgent, confidence, reason, alternativeAgent);
    }

    /**
     * 判断是否需要用户确认
     *
     * <p>当置信度低于 0.7 时，需要用户确认选择是否正确。
     *
     * @return 是否需要确认
     */
    public boolean needsConfirmation() {
        return confidence < 0.7;
    }

    /**
     * 获取置信度百分比
     *
     * @return 置信度百分比（0-100）
     */
    public int getConfidencePercent() {
        return (int) Math.round(confidence * 100);
    }
}
