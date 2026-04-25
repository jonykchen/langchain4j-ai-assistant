package com.jonychen.tool.builtin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.jonychen.agent.core.AgentDelegationService;
import com.jonychen.agent.core.AgentMetadata;
import com.jonychen.tool.AgentTool;
import com.jonychen.tool.ToolCategory;
import com.jonychen.tool.ToolResult;

/**
 * Agent 委托工具集
 *
 * <p>提供 Agent 间委托协作的工具：
 *
 * <ul>
 *   <li>list_available_agents - 列出可委托的 Agent
 *   <li>delegate_to_agent - 委托给目标 Agent 执行
 * </ul>
 *
 * <h2>委托机制</h2>
 *
 * <p>当一个 Agent 在执行过程中发现需要其他 Agent 的能力时， 可以通过委托机制将子任务交给目标 Agent 处理， 获取结果后继续执行。 这实现了多 Agent 协作的核心能力。
 *
 * <h2>安全约束</h2>
 *
 * <ul>
 *   <li>委托深度限制（最大 3 层）
 *   <li>不能委托给自己
 *   <li>不能委托给 RouterAgent
 * </ul>
 *
 * @author jonychen
 */
@Component
public class AgentDelegationTools {

    private static final Logger log = LoggerFactory.getLogger(AgentDelegationTools.class);

    private final AgentDelegationService delegationService;

    public AgentDelegationTools(AgentDelegationService delegationService) {
        this.delegationService = delegationService;
        log.info("[AgentDelegationTools] 初始化完成");
    }

    /**
     * 列出可委托的 Agent
     *
     * <p>返回除 RouterAgent 和当前 Agent 之外的所有可用 Agent 列表， 包含名称、显示名称、描述和能力。
     *
     * @param sourceAgentName 当前 Agent 名称（用于排除自己）
     * @return 工具结果
     */
    @AgentTool(
            name = "list_available_agents",
            description = "列出可委托的 Agent，返回名称、描述和能力信息",
            category = ToolCategory.AGENT,
            riskLevel = com.jonychen.tool.RiskLevel.LOW)
    public ToolResult listAvailableAgents(String sourceAgentName) {
        log.info(
                "[AgentDelegationTools] 执行 list_available_agents: sourceAgent={}", sourceAgentName);

        List<AgentMetadata> agents = delegationService.getDelegatableAgents(sourceAgentName);

        List<Map<String, Object>> agentList =
                agents.stream()
                        .map(
                                meta -> {
                                    Map<String, Object> info = new HashMap<>();
                                    info.put("name", meta.name());
                                    info.put("displayName", meta.displayName());
                                    info.put("description", meta.description());
                                    info.put("capabilities", meta.capabilities());
                                    info.put("requiredPermissions", meta.requiredPermissions());
                                    return info;
                                })
                        .toList();

        Map<String, Object> data = new HashMap<>();
        data.put("agents", agentList);
        data.put("count", agentList.size());

        return ToolResult.success(data);
    }

    /**
     * 委托给目标 Agent
     *
     * <p>将子任务委托给目标 Agent 执行，等待结果返回。
     *
     * @param targetAgent 目标 Agent 名称
     * @param input 委托输入内容
     * @param sourceAgentName 当前 Agent 名称
     * @return 工具结果
     */
    @AgentTool(
            name = "delegate_to_agent",
            description = "将子任务委托给目标 Agent 执行，等待结果返回",
            category = ToolCategory.AGENT,
            riskLevel = com.jonychen.tool.RiskLevel.MEDIUM)
    public ToolResult delegateToAgent(String targetAgent, String input, String sourceAgentName) {
        log.info(
                "[AgentDelegationTools] 执行 delegate_to_agent: source={}, target={}, input={}",
                sourceAgentName,
                targetAgent,
                input != null && input.length() > 50 ? input.substring(0, 50) + "..." : input);

        // 参数校验
        if (targetAgent == null || targetAgent.isBlank()) {
            return ToolResult.failure("目标 Agent 名称不能为空");
        }

        if (input == null || input.isBlank()) {
            return ToolResult.failure("委托输入内容不能为空");
        }

        // 同步委托执行（注意：此方法在 Agent 工具调用上下文中使用时，
        // 需要配合 AbstractAgent 的委托处理逻辑，发送 AgentCall/AgentResult 事件）
        var result = delegationService.delegate(targetAgent, input, null, null, null, 0);

        if (result.success()) {
            Map<String, Object> data = new HashMap<>();
            data.put("targetAgent", result.targetAgent());
            data.put("output", result.output());
            data.put("durationMs", result.durationMs());
            return ToolResult.success(data);
        } else {
            return ToolResult.failure("委托失败: " + result.error());
        }
    }
}
