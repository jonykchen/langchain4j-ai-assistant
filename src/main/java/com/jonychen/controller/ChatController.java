package com.jonychen.controller;

import com.jonychen.model.ChatRequest;
import com.jonychen.model.ChatResponse;
import com.jonychen.service.AiService;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * 聊天控制器
 * 提供同步和流式对话接口
 *
 * @author 30240
 */
@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    private final AiService aiService;

    public ChatController(AiService aiService) {
        this.aiService = aiService;
    }

    /**
     * 同步对话接口
     */
    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String reply = aiService.chat(request.message());
        return new ChatResponse(reply);
    }

    /**
     * 流式对话接口 (SSE)
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@RequestBody ChatRequest request) {
        return aiService.chatFlux(request.message())
                .map(token -> ServerSentEvent.<String>builder()
                        .event("token")
                        .data(token)
                        .build())
                .concatWith(Flux.just(
                        ServerSentEvent.<String>builder()
                                .event("done")
                                .data("[DONE]")
                                .build()
                ));
    }
}
