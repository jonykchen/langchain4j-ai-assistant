package com.jonychen.service;

import com.jonychen.assistant.ChatAssistant;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * AI 服务层
 * 封装 AI 调用逻辑，可在此添加业务逻辑
 *
 * @author 30240
 */
@Service
public class AiService {

    private final ChatAssistant chatAssistant;

    public AiService(ChatAssistant chatAssistant) {
        this.chatAssistant = chatAssistant;
    }

    /**
     * 同步对话
     */
    public String chat(String message) {
        return chatAssistant.chat(message);
    }

    /**
     * 流式对话
     */
    public Flux<String> chatFlux(String message) {
        return chatAssistant.chatFlux(message);
    }
}
