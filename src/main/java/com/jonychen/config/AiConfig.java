package com.jonychen.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.jonychen.assistant.ChatAssistant;
import com.jonychen.model.LoadBalancedChatModel;
import com.jonychen.model.LoadBalancedStreamingChatModel;

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

    /**
     * 构建负载均衡聊天模型
     *
     * @param modelProperties 模型配置属性
     * @param circuitBreakerRegistry 熔断器注册表
     * @param meterRegistry 指标注册表
     * @return LoadBalancedChatModel 实例
     */
    @Bean
    public LoadBalancedChatModel loadBalancedChatModel(
            ModelProperties modelProperties,
            CircuitBreakerRegistry circuitBreakerRegistry,
            MeterRegistry meterRegistry) {

        var providers = modelProperties.getEnabledProviders();

        if (providers.isEmpty()) {
            LOG.error("没有可用的模型提供者配置");
            throw new IllegalStateException("没有配置任何可用的 AI 模型");
        }

        LOG.info("初始化负载均衡模型，共 {} 个提供者", providers.size());

        return new LoadBalancedChatModel(providers, circuitBreakerRegistry, meterRegistry);
    }

    /**
     * 构建负载均衡流式聊天模型
     *
     * @param modelProperties 模型配置属性
     * @param circuitBreakerRegistry 熔断器注册表
     * @param meterRegistry 指标注册表
     * @return LoadBalancedStreamingChatModel 实例
     */
    @Bean
    public LoadBalancedStreamingChatModel loadBalancedStreamingChatModel(
            ModelProperties modelProperties,
            CircuitBreakerRegistry circuitBreakerRegistry,
            MeterRegistry meterRegistry) {

        var providers = modelProperties.getEnabledProviders();

        if (providers.isEmpty()) {
            LOG.error("没有可用的模型提供者配置");
            throw new IllegalStateException("没有配置任何可用的 AI 模型");
        }

        LOG.info("初始化负载均衡流式模型，共 {} 个提供者", providers.size());

        return new LoadBalancedStreamingChatModel(providers, circuitBreakerRegistry, meterRegistry);
    }

    /**
     * 构建 ChatAssistant Bean
     *
     * <p>使用负载均衡模型替代单一模型，实现高可用。
     *
     * @param loadBalancedChatModel 负载均衡同步模型
     * @param loadBalancedStreamingChatModel 负载均衡流式模型
     * @return ChatAssistant 实例
     */
    @Bean
    public ChatAssistant chatAssistant(
            LoadBalancedChatModel loadBalancedChatModel,
            LoadBalancedStreamingChatModel loadBalancedStreamingChatModel) {

        LOG.info("构建 ChatAssistant，使用负载均衡模型");

        return AiServices.builder(ChatAssistant.class)
                // 使用负载均衡模型
                .chatModel(loadBalancedChatModel)
                .streamingChatModel(loadBalancedStreamingChatModel)
                // 对话记忆配置
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();
    }
}
