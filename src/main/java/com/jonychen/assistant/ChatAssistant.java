package com.jonychen.assistant;

import dev.langchain4j.service.SystemMessage;
import reactor.core.publisher.Flux;

/**
 * AI 对话助手接口
 * LangChain4j 会自动实现此接口
 *
 * @author 30240
 */
public interface ChatAssistant {

    /**
     * 系统提示词，定义 AI 的角色和行为
     */
    @SystemMessage("""
            你是一个友好的 AI 助手。
            请用简洁、准确的语言回答用户的问题。
            如果不确定答案，请诚实告知。
            """)
    String chat(String userMessage);

    /**
     * 流式响应方法 (响应式)
     * 返回 Flux<String> 配合 WebFlux 使用
     */
    Flux<String> chatFlux(String userMessage);
}
