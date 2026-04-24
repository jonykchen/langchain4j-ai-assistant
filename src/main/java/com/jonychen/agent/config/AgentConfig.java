package com.jonychen.agent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.jonychen.agent.core.Agent;
import com.jonychen.agent.core.AgentContext;
import com.jonychen.agent.core.AgentEvent;
import com.jonychen.agent.core.AgentMetadata;
import com.jonychen.agent.core.AgentRegistry;
import com.jonychen.agent.core.AgentRequest;
import com.jonychen.agent.core.AgentResult;
import com.jonychen.agent.core.TokenUsage;
import com.jonychen.agent.impl.OpsAgent;
import com.jonychen.observability.trace.AgentTraceService;
import com.jonychen.tool.ToolRegistry;

import reactor.core.publisher.Flux;

/**
 * Agent 配置类
 *
 * <p>配置 Spring Bean：Agent 实例和注册表。 P0 阶段仅注册 OpsAgent 和 ChatAgent。
 *
 * @author jonychen
 */
@Configuration
public class AgentConfig {

    private static final Logger log = LoggerFactory.getLogger(AgentConfig.class);

    @Bean
    public AgentRegistry agentRegistry(OpsAgent opsAgent) {
        AgentRegistry registry = new AgentRegistry();
        registry.register(opsAgent);

        // 注册 ChatAgent（简单实现，不依赖工具）
        registry.register(new ChatAgent(null, null, null));

        log.info("[AgentConfig] Agent 注册完成: {}", registry.getAgentNames());
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
