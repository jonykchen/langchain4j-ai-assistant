package com.jonychen.agent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.jonychen.agent.core.Agent;
import com.jonychen.agent.core.AgentAuditService;
import com.jonychen.agent.core.AgentContext;
import com.jonychen.agent.core.AgentDelegationService;
import com.jonychen.agent.core.AgentEvent;
import com.jonychen.agent.core.AgentMetadata;
import com.jonychen.agent.core.AgentMetricsService;
import com.jonychen.agent.core.AgentRegistry;
import com.jonychen.agent.core.AgentRequest;
import com.jonychen.agent.core.AgentResult;
import com.jonychen.agent.core.TokenUsage;
import com.jonychen.agent.impl.DataAgent;
import com.jonychen.agent.impl.OpsAgent;
import com.jonychen.agent.impl.PromptAgent;
import com.jonychen.agent.impl.RouterAgent;
import com.jonychen.agent.impl.TestAgent;
import com.jonychen.observability.trace.AgentTraceService;
import com.jonychen.tool.ToolRegistry;
import com.jonychen.tool.builtin.ChartTools;
import com.jonychen.tool.builtin.DatabaseTools;
import com.jonychen.tool.builtin.ExportTools;
import com.jonychen.tool.builtin.PromptTools;
import com.jonychen.tool.builtin.TestGeneratorTools;

import reactor.core.publisher.Flux;

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
     * <p>支持 Agent 间委托协作的核心服务。
     *
     * @param agentRegistry Agent 注册表
     * @return 委托服务实例
     */
    @Bean
    public AgentDelegationService agentDelegationService(
            AgentRegistry agentRegistry, AgentMetricsService metricsService) {
        log.info("[AgentConfig] 创建 AgentDelegationService Bean");
        return new AgentDelegationService(agentRegistry, metricsService);
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
            DatabaseTools databaseTools,
            ChartTools chartTools,
            ExportTools exportTools,
            AgentDelegationService delegationService,
            AgentAuditService auditService,
            AgentMetricsService metricsService) {

        log.info("[AgentConfig] 创建 DataAgent Bean，依赖: DatabaseTools, ChartTools, ExportTools");

        DataAgent agent =
                new DataAgent(
                        chatModel,
                        toolRegistry,
                        traceService,
                        databaseTools,
                        chartTools,
                        exportTools);

        // 注入委托服务
        agent.setDelegationService(delegationService);
        // 注入审计服务
        agent.setAuditService(auditService);
        // 注入指标服务
        agent.setMetricsService(metricsService);

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
            PromptTools promptTools,
            AgentDelegationService delegationService,
            AgentAuditService auditService,
            AgentMetricsService metricsService) {

        log.info("[AgentConfig] 创建 PromptAgent Bean");

        PromptAgent agent = new PromptAgent(chatModel, toolRegistry, traceService, promptTools);
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
            TestGeneratorTools testGeneratorTools,
            AgentDelegationService delegationService,
            AgentAuditService auditService,
            AgentMetricsService metricsService) {

        log.info("[AgentConfig] 创建 TestAgent Bean");

        TestAgent agent = new TestAgent(chatModel, toolRegistry, traceService, testGeneratorTools);
        agent.setDelegationService(delegationService);
        agent.setAuditService(auditService);
        agent.setMetricsService(metricsService);

        log.info(
                "[AgentConfig] TestAgent 创建成功，元信息: name={}, displayName={}",
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
     * @return Agent 注册表
     */
    @Bean
    public AgentRegistry agentRegistry(
            OpsAgent opsAgent, DataAgent dataAgent, PromptAgent promptAgent, TestAgent testAgent) {
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

        // 注册 ChatAgent（通用对话，简单内部实现）
        registry.register(new ChatAgent(null, null, null));
        log.debug("[AgentConfig] ChatAgent 注册成功");

        log.info(
                "[AgentConfig] Agent 注册完成，共 {} 个: {}",
                registry.getAgentNames().size(),
                registry.getAgentNames());

        return registry;
    }

    /**
     * 通用对话 Agent（简单内部实现）
     *
     * <p>不继承 AbstractAgent，直接实现 Agent 接口。 不调用工具，直接使用 LLM 回答问题。
     */
    static class ChatAgent implements Agent {

        private final dev.langchain4j.model.chat.ChatModel chatModel;
        private final ToolRegistry toolRegistry;
        private final AgentTraceService traceService;

        ChatAgent(
                dev.langchain4j.model.chat.ChatModel chatModel,
                ToolRegistry toolRegistry,
                AgentTraceService traceService) {
            this.chatModel = chatModel;
            this.toolRegistry = toolRegistry;
            this.traceService = traceService;
        }

        @Override
        public AgentMetadata getMetadata() {
            return AgentMetadata.chat();
        }

        @Override
        public AgentResult execute(AgentRequest request, AgentContext context) {
            if (chatModel == null) {
                return AgentResult.failure(context.getTraceId(), "ChatModel 未配置");
            }

            try {
                dev.langchain4j.model.chat.request.ChatRequest chatRequest =
                        dev.langchain4j.model.chat.request.ChatRequest.builder()
                                .messages(
                                        java.util.List.of(
                                                new dev.langchain4j.data.message.SystemMessage(
                                                        "你是一个友好的 AI 助手。"),
                                                new dev.langchain4j.data.message.UserMessage(
                                                        request.userInput())))
                                .build();

                dev.langchain4j.model.chat.response.ChatResponse response =
                        chatModel.chat(chatRequest);
                String output = response.aiMessage().text();

                return AgentResult.success(context.getTraceId(), output, java.util.List.of());
            } catch (Exception e) {
                return AgentResult.failure(context.getTraceId(), e.getMessage());
            }
        }

        @Override
        public Flux<AgentEvent> executeStream(AgentRequest request, AgentContext context) {
            var result = execute(request, context);
            if (result.isSuccess()) {
                return Flux.just(
                        AgentEvent.done(
                                context.getTraceId(),
                                0,
                                getMetadata().name(),
                                result.output(),
                                0,
                                TokenUsage.empty(),
                                result.durationMs()));
            }
            return Flux.just(
                    AgentEvent.error(
                            context.getTraceId(),
                            0,
                            "EXECUTION_ERROR",
                            result.errorMessage(),
                            null,
                            true));
        }

        @Override
        public double canHandle(AgentRequest request) {
            return 0.1;
        }
    }
}
