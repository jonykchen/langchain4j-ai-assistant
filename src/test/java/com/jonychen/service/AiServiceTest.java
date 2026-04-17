package com.jonychen.service;

import com.jonychen.assistant.ChatAssistant;
import com.jonychen.exception.BusinessException;
import com.jonychen.model.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiServiceTest {

    @Mock
    private ChatAssistant chatAssistant;

    private AiService aiService;

    @BeforeEach
    void setUp() {
        aiService = new AiService(chatAssistant);
    }

    @Test
    @DisplayName("同步聊天 - 成功返回回复")
    void chat_shouldReturnReply() {
        String message = "你好";
        String expectedReply = "你好！有什么可以帮助你的？";
        when(chatAssistant.chat(message)).thenReturn(expectedReply);

        String reply = aiService.chat(message);

        assertEquals(expectedReply, reply);
        verify(chatAssistant).chat(message);
    }

    @Test
    @DisplayName("同步聊天 - API Key 无效应抛出异常")
    void chat_invalidApiKey_shouldThrowException() {
        when(chatAssistant.chat(anyString()))
                .thenThrow(new RuntimeException("Unauthorized: API key invalid"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> aiService.chat("test")
        );
        assertEquals(ErrorCode.AI_API_KEY_INVALID.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("同步聊天 - 请求超时应抛出异常")
    void chat_timeout_shouldThrowException() {
        when(chatAssistant.chat(anyString()))
                .thenThrow(new RuntimeException("Request timeout"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> aiService.chat("test")
        );
        assertEquals(ErrorCode.AI_REQUEST_TIMEOUT.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("同步聊天 - 配额超限应抛出异常")
    void chat_quotaExceeded_shouldThrowException() {
        when(chatAssistant.chat(anyString()))
                .thenThrow(new RuntimeException("rate limit exceeded"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> aiService.chat("test")
        );
        assertEquals(ErrorCode.AI_QUOTA_EXCEEDED.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("流式聊天 - 成功返回流")
    void chatFlux_shouldReturnFlux() {
        String message = "讲个笑话";
        when(chatAssistant.chatFlux(message))
                .thenReturn(Flux.just("为", "什", "么", "？"));

        Flux<String> result = aiService.chatFlux(message);

        StepVerifier.create(result)
                .expectNext("为", "什", "么", "？")
                .verifyComplete();
    }

    @Test
    @DisplayName("流式聊天 - 错误应转换为业务异常")
    void chatFlux_error_shouldConvertToBusinessException() {
        when(chatAssistant.chatFlux(anyString()))
                .thenReturn(Flux.error(new RuntimeException("API error")));

        Flux<String> result = aiService.chatFlux("test");

        StepVerifier.create(result)
                .expectError(BusinessException.class)
                .verify();
    }
}