package com.jonychen.controller;

import com.jonychen.model.ChatRequest;
import com.jonychen.service.AiService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class ChatControllerIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private AiService aiService;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void setUp() {
        // 重置熔断器状态
        circuitBreakerRegistry.getAllCircuitBreakers()
                .forEach(cb -> cb.transitionToClosedState());
    }

    @Test
    @DisplayName("同步聊天 - 成功返回响应")
    void chat_shouldReturnResponse() {
        String userMessage = "你好";
        String aiReply = "你好！有什么可以帮助你的？";
        when(aiService.chat(userMessage)).thenReturn(aiReply);

        webTestClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ChatRequest(userMessage))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(200)
                .jsonPath("$.message").isEqualTo("success")
                .jsonPath("$.data.reply").isEqualTo(aiReply);
    }

    @Test
    @DisplayName("同步聊天 - 消息为空应返回错误")
    void chat_emptyMessage_shouldReturnError() {
        webTestClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ChatRequest(""))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").exists()
                .jsonPath("$.message").exists();
    }

    @Test
    @DisplayName("同步聊天 - 服务异常应触发 Fallback")
    void chat_serviceError_shouldTriggerFallback() {
        when(aiService.chat(anyString()))
                .thenThrow(new RuntimeException("AI service error"));

        // 由于 Fallback 处理，应该返回 200 状态码，但包含错误信息
        webTestClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ChatRequest("test"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").exists();
    }

    @Test
    @DisplayName("流式聊天 - 应返回 SSE 流")
    void chatStream_shouldReturnSseStream() {
        when(aiService.chatFlux(anyString()))
                .thenReturn(Flux.just("为", "什", "么", "？"));

        webTestClient.post()
                .uri("/api/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ChatRequest("讲个笑话"))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .expectBody()
                .consumeWith(response -> {
                    String body = new String(response.getResponseBodyContent());
                    assert body.contains("event:token");
                    assert body.contains("event:done");
                });
    }

    @Test
    @DisplayName("流式聊天 - 服务异常应返回错误响应")
    void chatStream_serviceError_shouldReturnErrorResponse() {
        when(aiService.chatFlux(anyString()))
                .thenReturn(Flux.error(new RuntimeException("AI error")));

        // 流式接口错误可能返回 500 或触发 Fallback
        webTestClient.post()
                .uri("/api/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ChatRequest("test"))
                .exchange()
                .expectStatus().is5xxServerError()
                .expectBody();
    }
}