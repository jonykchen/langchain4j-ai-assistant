package com.jonychen.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import com.jonychen.assistant.ChatAssistant;
import com.jonychen.model.LoadBalancedChatModel;
import com.jonychen.model.LoadBalancedStreamingChatModel;
import com.jonychen.observability.trace.RequestTraceService;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServices;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * AI 配置类
 *
 * <p>职责： 1. 配置 ChatAssistant Bean（核心 AI 服务） 2. 设置对话记忆（ChatMemory） 3. 构建负载均衡模型（多模型高可用）
 *
 * <p>高可用架构： - LoadBalancedChatModel: 多模型负载均衡 + 故障转移 - LoadBalancedStreamingChatModel: 流式模型负载均衡 -
 * 模型级熔断器: 每个模型独立熔断
 *
 * @author 30240
 */
@Configuration
public class AiConfig {

    private static final Logger LOG = LoggerFactory.getLogger(AiConfig.class);

    private final ModelProperties modelProperties;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final MeterRegistry meterRegistry;
    private final RequestTraceService requestTraceService;

    private volatile LoadBalancedChatModel loadBalancedChatModel;
    private volatile LoadBalancedStreamingChatModel loadBalancedStreamingChatModel;
    private volatile ChatAssistant chatAssistant;

    public AiConfig(
            ModelProperties modelProperties,
            CircuitBreakerRegistry circuitBreakerRegistry,
            MeterRegistry meterRegistry,
            RequestTraceService requestTraceService) {
        this.modelProperties = modelProperties;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.meterRegistry = meterRegistry;
        this.requestTraceService = requestTraceService;
        initModels();
    }

    private void initModels() {
        var providers = modelProperties.getEnabledProviders();
        if (providers.isEmpty()) {
            LOG.error("没有可用的模型提供者配置");
            throw new IllegalStateException("没有配置任何可用的 AI 模型");
        }
        LOG.info("初始化负载均衡模型，共 {} 个提供者", providers.size());
        for (var p : providers) {
            LOG.info("  - {} (优先级={}, 权重={})", p.name(), p.priority(), p.weight());
        }
        this.loadBalancedChatModel =
                new LoadBalancedChatModel(
                        providers, circuitBreakerRegistry, meterRegistry, requestTraceService);
        this.loadBalancedStreamingChatModel =
                new LoadBalancedStreamingChatModel(
                        providers, circuitBreakerRegistry, meterRegistry, requestTraceService);
        this.chatAssistant =
                AiServices.builder(ChatAssistant.class)
                        .chatModel(loadBalancedChatModel)
                        .streamingChatModel(loadBalancedStreamingChatModel)
                        .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                        .build();
        LOG.info("ChatAssistant 构建完成");
    }

    @EventListener(EnvironmentChangeEvent.class)
    public void onRefresh(EnvironmentChangeEvent event) {
        LOG.info("检测到配置变更，重新加载模型配置");
        initModels();
    }

    @Bean
    public LoadBalancedChatModel loadBalancedChatModel() {
        return loadBalancedChatModel;
    }

    @Bean
    public LoadBalancedStreamingChatModel loadBalancedStreamingChatModel() {
        return loadBalancedStreamingChatModel;
    }

    @Bean
    public ChatAssistant chatAssistant() {
        return chatAssistant;
    }
}
