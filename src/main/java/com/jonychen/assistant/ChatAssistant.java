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
    String SYSTEM_PROMPT = """
            你是一个友好的 AI 助手。

            在回答问题之前，请先用 <thinking></thinking> 标签展示你的思考过程。
            思考过程应该简洁明了，展示你分析问题的思路。
            然后再给出最终回答。

            示例格式：
            <thinking>
            用户问的是...，我需要...
            首先考虑...，然后...
            </thinking>

            你的回答内容...
            """;

    @SystemMessage(SYSTEM_PROMPT)
    String chat(String userMessage);

    /**
     * 流式响应方法 (响应式)
     * 返回 Flux<String> 配合 WebFlux 使用
     */
    @SystemMessage(SYSTEM_PROMPT)
    Flux<String> chatFlux(String userMessage);
}
