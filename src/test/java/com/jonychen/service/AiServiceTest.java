package com.jonychen.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jonychen.admin.service.TokenUsageService;
import com.jonychen.exception.BusinessException;
import com.jonychen.metrics.BusinessMetricsService;
import com.jonychen.model.ErrorCode;
import com.jonychen.model.LoadBalancedChatModel;
import com.jonychen.model.LoadBalancedStreamingChatModel;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

@ExtendWith(MockitoExtension.class)
class AiServiceTest {

    @Mock private LoadBalancedChatModel chatModel;

    @Mock private LoadBalancedStreamingChatModel streamingChatModel;

    @Mock private TokenUsageService tokenUsageService;

    @Mock private BusinessMetricsService businessMetricsService;

    private AiService aiService;

    @BeforeEach
    void setUp() {
        aiService =
                new AiService(
                        chatModel, streamingChatModel, tokenUsageService, businessMetricsService);
    }

    @Test
    @DisplayName("同步聊天 - 成功返回回复")
    void chat_shouldReturnReply() {
        String message = "你好";
        String expectedReply = "你好！有什么可以帮助你的？";

        ChatResponse mockResponse =
                ChatResponse.builder()
                        .aiMessage(AiMessage.from(expectedReply))
                        .tokenUsage(new TokenUsage(100, 50, 150))
                        .modelName("test-model")
                        .build();

        when(chatModel.chat(any(ChatRequest.class))).thenReturn(mockResponse);

        String reply = aiService.chat(message);

        assertEquals(expectedReply, reply);
        verify(tokenUsageService).recordUsage(any(), any(), any(), any(ChatResponse.class));
    }

    @Test
    @DisplayName("同步聊天 - API Key 无效应抛出异常")
    void chat_invalidApiKey_shouldThrowException() {
        when(chatModel.chat(any(ChatRequest.class)))
                .thenThrow(new RuntimeException("Unauthorized: API key invalid"));

        BusinessException exception =
                assertThrows(BusinessException.class, () -> aiService.chat("test"));
        assertEquals(ErrorCode.AI_API_KEY_INVALID.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("同步聊天 - 请求超时应抛出异常")
    void chat_timeout_shouldThrowException() {
        when(chatModel.chat(any(ChatRequest.class)))
                .thenThrow(new RuntimeException("Request timeout"));

        BusinessException exception =
                assertThrows(BusinessException.class, () -> aiService.chat("test"));
        assertEquals(ErrorCode.AI_REQUEST_TIMEOUT.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("同步聊天 - 配额超限应抛出异常")
    void chat_quotaExceeded_shouldThrowException() {
        when(chatModel.chat(any(ChatRequest.class)))
                .thenThrow(new RuntimeException("rate limit exceeded"));

        BusinessException exception =
                assertThrows(BusinessException.class, () -> aiService.chat("test"));
        assertEquals(ErrorCode.AI_QUOTA_EXCEEDED.getCode(), exception.getCode());
    }
}
