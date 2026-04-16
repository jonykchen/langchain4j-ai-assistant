package com.jonychen.config;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import com.jonychen.assistant.ChatAssistant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 配置类
 * 配置 ChatModel、Memory 等
 *
 * @author 30240
 */
@Configuration
public class AiConfig {

    /**
     * 构建对话助手 Bean
     * AiServices 是 LangChain4j 的核心，它会自动实现 AI 接口
     *
     * ChatModel 由 Spring Boot 自动配置，
     * 通过 application.properties 中的配置连接阿里云百炼
     */
    @Bean
    public ChatAssistant chatAssistant(ChatModel chatModel) {
        return AiServices.builder(ChatAssistant.class)
                .chatModel(chatModel)
                // 添加对话记忆，保留最近 10 条消息
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();
    }
}