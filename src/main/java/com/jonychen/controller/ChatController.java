package com.jonychen.controller;

import com.jonychen.model.ChatRequest;
import com.jonychen.model.ChatResponse;
import com.jonychen.service.AiService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 聊天控制器
 * 提供同步对话接口
 *
 * @author 30240
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final AiService aiService;

    public ChatController(AiService aiService) {
        this.aiService = aiService;
    }

    /**
     * 同步对话接口
     * 适合短对话，等待完整响应返回
     */
    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String reply = aiService.chat(request.message());
        return new ChatResponse(reply);
    }
}
