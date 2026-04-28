package com.jonychen.agent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.jonychen.agent.core.AgentAuditService;
import com.jonychen.agent.core.AgentDelegationService;
import com.jonychen.agent.core.AgentMetricsService;
import com.jonychen.agent.core.AgentRegistry;
import com.jonychen.agent.core.TokenUsageTracker;
import com.jonychen.agent.impl.ChatAgent;
import com.jonychen.agent.impl.DataAgent;
import com.jonychen.agent.impl.OpsAgent;
import com.jonychen.agent.impl.PromptAgent;
import com.jonychen.agent.impl.RouterAgent;
import com.jonychen.agent.impl.TestAgent;
import com.jonychen.observability.trace.AgentTraceService;
import com.jonychen.observability.trace.TraceContext;
import com.jonychen.tool.ToolRegistry;
import com.jonychen.tool.builtin.ChartTools;
import com.jonychen.tool.builtin.DatabaseTools;
import com.jonychen.tool.builtin.ExportTools;
import com.jonychen.tool.builtin.PromptTools;
import com.jonychen.tool.builtin.TestGeneratorTools;

/**
 * Agent 配置类
 *
 * <p>配置 Spring Bean：Agent 实例和注册表。
 *
 * <h2>已注册 Agent</h2>
 *
 * <ul>
 *   <li>{@link RouterAgent} - 智能路由，分析意图选择最合适的 Agent
 *   <li>{@link OpsAgent} - 运维助手，处理模型诊断、熔断器管理等运维任务
 *   <li>{@link DataAgent} - 数据分析助手，处理自然语言查库、图表生成、数据导出
 *   <li>{@link PromptAgent} - Prompt 工程助手，处理模板管理、优化、A/B 测试
 *   <li>{@link TestAgent} - 测试生成助手，生成单元/集成测试
 *   <li>{@link ChatAgent} - 通用对话，处理普通问答和闲聊
 * </ul>
 *
 * @author jonychen
 */
@Configuration
public class AgentConfig {

    private static final Logger log = LoggerFactory.getLogger(AgentConfig.class);

    /**
     * 创建 AgentDelegationService Bean
     *
     * <p>支持 Agent 间委托协作的核心服务。 使用 @Lazy 延迟注入 AgentRegistry 打破循环依赖。
     *
     * @param metricsService 指标服务
     * @return 委托服务实例
     */
    @Bean
    public AgentDelegationService agentDelegationService(AgentMetricsService metricsService) {
        log.info("[AgentConfig] 创建 AgentDelegationService Bean");
        return new AgentDelegationService(metricsService);
    }

    /**
     * 创建 DataAgent Bean
     *
     * <p>DataAgent 负责数据分析任务，依赖三个工具集：
     *
     * <ul>
     *   <li>DatabaseTools - 数据库查询（SQL AST 安全校验）
     *   <li>ChartTools - 图表生成（ECharts 配置）
     *   <li>ExportTools - 数据导出（CSV/JSON）
     * </ul>
     *
     * @param chatModel LangChain4j 聊天模型
     * @param toolRegistry 工具注册中心
     * @param traceService Agent 追踪服务
     * @param databaseTools 数据库工具
     * @param chartTools 图表工具
     * @param exportTools 导出工具
     * @param delegationService 委托服务
     * @param auditService 审计服务
     * @return DataAgent 实例
     */
    @Bean
    public DataAgent dataAgent(
            dev.langchain4j.model.chat.ChatModel chatModel,
            ToolRegistry toolRegistry,
            AgentTraceService traceService,
            TraceContext traceContext,
            DatabaseTools databaseTools,
            ChartTools chartTools,
            ExportTools exportTools,
            AgentDelegationService delegationService,
            AgentAuditService auditService,
            AgentMetricsService metricsService,
            TokenUsageTracker tokenUsageTracker) {

        log.info("[AgentConfig] 创建 DataAgent Bean，依赖: DatabaseTools, ChartTools, ExportTools");

        DataAgent agent =
                new DataAgent(
                        chatModel,
                        toolRegistry,
                        traceService,
                        traceContext,
                        databaseTools,
                        chartTools,
                        exportTools);

        // 注入委托服务
        agent.setDelegationService(delegationService);
        // 注入审计服务
        agent.setAuditService(auditService);
        // 注入指标服务
        agent.setMetricsService(metricsService);
        // 注入 Token 追踪服务
        agent.setTokenUsageTracker(tokenUsageTracker);

        log.info(
                "[AgentConfig] DataAgent 创建成功，元信息: name={}, displayName={}",
                agent.getMetadata().name(),
                agent.getMetadata().displayName());

        return agent;
    }

    /**
     * 创建 RouterAgent Bean
     *
     * <p>RouterAgent 使用 LLM 分析用户意图，智能路由到最合适的 Agent。
     *
     * @param chatModel LangChain4j 聊天模型
     * @param agentRegistry Agent 注册表
     * @return RouterAgent 实例
     */
    @Bean
    public RouterAgent routerAgent(
            dev.langchain4j.model.chat.ChatModel chatModel, AgentRegistry agentRegistry) {

        log.info("[AgentConfig] 创建 RouterAgent Bean");

        RouterAgent agent = new RouterAgent(chatModel, agentRegistry);

        log.info(
                "[AgentConfig] RouterAgent 创建成功，元信息: name={}, displayName={}",
                agent.getMetadata().name(),
                agent.getMetadata().displayName());

        return agent;
    }

    /**
     * 创建 PromptAgent Bean
     *
     * <p>PromptAgent 负责 Prompt 模板管理、优化和 A/B 测试。
     *
     * @param chatModel LangChain4j 聊天模型
     * @param toolRegistry 工具注册中心
     * @param traceService 追踪服务
     * @param promptTools Prompt 工具
     * @param delegationService 委托服务
     * @param auditService 审计服务
     * @return PromptAgent 实例
     */
    @Bean
    public PromptAgent promptAgent(
            dev.langchain4j.model.chat.ChatModel chatModel,
            ToolRegistry toolRegistry,
            AgentTraceService traceService,
            TraceContext traceContext,
            PromptTools promptTools,
            AgentDelegationService delegationService,
            AgentAuditService auditService,
            AgentMetricsService metricsService) {

        log.info("[AgentConfig] 创建 PromptAgent Bean");

        PromptAgent agent =
                new PromptAgent(chatModel, toolRegistry, traceService, traceContext, promptTools);
        agent.setDelegationService(delegationService);
        agent.setAuditService(auditService);
        agent.setMetricsService(metricsService);

        log.info(
                "[AgentConfig] PromptAgent 创建成功，元信息: name={}, displayName={}",
                agent.getMetadata().name(),
                agent.getMetadata().displayName());

        return agent;
    }

    /**
     * 创建 TestAgent Bean
     *
     * <p>TestAgent 负责为业务代码生成单元测试、集成测试。
     *
     * @param chatModel LangChain4j 聊天模型
     * @param toolRegistry 工具注册中心
     * @param traceService 追踪服务
     * @param testGeneratorTools 测试生成工具
     * @param delegationService 委托服务
     * @param auditService 审计服务
     * @return TestAgent 实例
     */
    @Bean
    public TestAgent testAgent(
            dev.langchain4j.model.chat.ChatModel chatModel,
            ToolRegistry toolRegistry,
            AgentTraceService traceService,
            TraceContext traceContext,
            TestGeneratorTools testGeneratorTools,
            AgentDelegationService delegationService,
            AgentAuditService auditService,
            AgentMetricsService metricsService,
            TokenUsageTracker tokenUsageTracker) {

        log.info("[AgentConfig] 创建 TestAgent Bean");

        TestAgent agent =
                new TestAgent(
                        chatModel, toolRegistry, traceService, traceContext, testGeneratorTools);
        agent.setDelegationService(delegationService);
        agent.setAuditService(auditService);
        agent.setMetricsService(metricsService);
        agent.setTokenUsageTracker(tokenUsageTracker);

        log.info(
                "[AgentConfig] TestAgent 创建成功，元信息: name={}, displayName={}",
                agent.getMetadata().name(),
                agent.getMetadata().displayName());

        return agent;
    }

    /**
     * 创建 ChatAgent Bean
     *
     * <p>ChatAgent 作为通用对话 Agent，是 RouterAgent 的兜底选择。
     *
     * @param chatModel LangChain4j 聊天模型
     * @param toolRegistry 工具注册中心
     * @param traceService 追踪服务
     * @param delegationService 委托服务
     * @param auditService 审计服务
     * @param metricsService 指标服务
     * @return ChatAgent 实例
     */
    @Bean
    public ChatAgent chatAgent(
            dev.langchain4j.model.chat.ChatModel chatModel,
            ToolRegistry toolRegistry,
            AgentTraceService traceService,
            TraceContext traceContext,
            AgentDelegationService delegationService,
            AgentAuditService auditService,
            AgentMetricsService metricsService,
            TokenUsageTracker tokenUsageTracker) {

        log.info("[AgentConfig] 创建 ChatAgent Bean");

        ChatAgent agent = new ChatAgent(chatModel, toolRegistry, traceService, traceContext);
        agent.setDelegationService(delegationService);
        agent.setAuditService(auditService);
        agent.setMetricsService(metricsService);
        agent.setTokenUsageTracker(tokenUsageTracker);

        log.info(
                "[AgentConfig] ChatAgent 创建成功，元信息: name={}, displayName={}",
                agent.getMetadata().name(),
                agent.getMetadata().displayName());

        return agent;
    }

    /**
     * 注册 Agent 到注册表
     *
     * <p>Spring 自动注入所有 Agent Bean，统一注册到 AgentRegistry。 通过 AgentOrchestrator 根据用户请求路由到合适的 Agent。
     *
     * @param opsAgent 运维助手（需 ADMIN 权限）
     * @param dataAgent 数据分析助手（需 USER 权限）
     * @param promptAgent Prompt 工程助手（需 ADMIN 权限）
     * @param testAgent 测试生成助手（需 ADMIN 权限）
     * @param chatAgent 通用对话助手（兜底 Agent）
     * @return Agent 注册表
     */
    @Bean
    public AgentRegistry agentRegistry(
            OpsAgent opsAgent,
            DataAgent dataAgent,
            PromptAgent promptAgent,
            TestAgent testAgent,
            ChatAgent chatAgent) {
        log.info("[AgentConfig] 开始注册 Agent...");

        AgentRegistry registry = new AgentRegistry();

        // 注册 OpsAgent（运维助手）
        registry.register(opsAgent);
        log.debug("[AgentConfig] OpsAgent 注册成功: {}", opsAgent.getMetadata().name());

        // 注册 DataAgent（数据分析助手）
        registry.register(dataAgent);
        log.debug("[AgentConfig] DataAgent 注册成功: {}", dataAgent.getMetadata().name());

        // 注册 PromptAgent（Prompt 工程助手）
        registry.register(promptAgent);
        log.debug("[AgentConfig] PromptAgent 注册成功: {}", promptAgent.getMetadata().name());

        // 注册 TestAgent（测试生成助手）
        registry.register(testAgent);
        log.debug("[AgentConfig] TestAgent 注册成功: {}", testAgent.getMetadata().name());

        // 注册 ChatAgent（通用对话助手，作为兜底）
        registry.register(chatAgent);
        log.debug("[AgentConfig] ChatAgent 注册成功: {}", chatAgent.getMetadata().name());

        log.info(
                "[AgentConfig] Agent 注册完成，共 {} 个: {}",
                registry.getAgentNames().size(),
                registry.getAgentNames());

        return registry;
    }
}
