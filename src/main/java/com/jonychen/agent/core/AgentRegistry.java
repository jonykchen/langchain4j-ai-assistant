package com.jonychen.agent.core;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Agent 注册表
 *
 * <p>管理所有 Agent 实例，支持按名称查找和能力查询。 提供线程安全的注册和查找操作。
 *
 * @author jonychen
 */
@Component
public class AgentRegistry {

    private static final Logger log = LoggerFactory.getLogger(AgentRegistry.class);

    private final Map<String, Agent> agents = new ConcurrentHashMap<>();

    /**
     * 注册 Agent
     *
     * @param agent Agent 实例
     */
    public void register(Agent agent) {
        AgentMetadata metadata = agent.getMetadata();
        agents.put(metadata.name(), agent);
        log.info("[AgentRegistry] 注册 Agent: {} ({})", metadata.name(), metadata.displayName());
    }

    /**
     * 注销 Agent
     *
     * @param name Agent 名称
     */
    public void unregister(String name) {
        Agent removed = agents.remove(name);
        if (removed != null) {
            log.info("[AgentRegistry] 注销 Agent: {}", name);
        }
    }

    /**
     * 获取 Agent
     *
     * @param name Agent 名称
     * @return Agent 实例（可选）
     */
    public Optional<Agent> getAgent(String name) {
        return Optional.ofNullable(agents.get(name));
    }

    /**
     * 按能力查找 Agent
     *
     * @param capability 能力标签
     * @return 匹配的 Agent 列表
     */
    public List<Agent> findByCapability(String capability) {
        return agents.values().stream()
                .filter(agent -> agent.getMetadata().capabilities().contains(capability))
                .toList();
    }

    /**
     * 按类型查找 Agent
     *
     * @param agentType Agent 类型
     * @return 匹配的 Agent 列表
     */
    public List<Agent> findByType(AgentType agentType) {
        return agents.values().stream()
                .filter(agent -> agent.getMetadata().agentType() == agentType)
                .toList();
    }

    /**
     * 获取所有 Agent 元信息
     *
     * @return Agent 元信息列表
     */
    public List<AgentMetadata> getAllMetadata() {
        return agents.values().stream().map(Agent::getMetadata).toList();
    }

    /**
     * 获取所有 Agent 名称
     *
     * @return Agent 名称列表
     */
    public List<String> getAgentNames() {
        return List.copyOf(agents.keySet());
    }

    /**
     * 获取 Agent 数量
     *
     * @return Agent 数量
     */
    public int size() {
        return agents.size();
    }

    /**
     * 检查是否包含指定 Agent
     *
     * @param name Agent 名称
     * @return 是否包含
     */
    public boolean contains(String name) {
        return agents.containsKey(name);
    }
}
