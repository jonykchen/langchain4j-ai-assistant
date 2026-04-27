package com.jonychen.agent.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jonychen.admin.service.TokenUsageService;

import dev.langchain4j.model.chat.response.ChatResponse;

@ExtendWith(MockitoExtension.class)
class TokenUsageTrackerTest {

    @Mock private TokenUsageService tokenUsageService;

    @Mock private AgentMetricsService metricsService;

    private TokenUsageTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new TokenUsageTracker(tokenUsageService, metricsService);
    }

    @Nested
    @DisplayName("记录 Token 使用")
    class RecordUsage {

        @Test
        @DisplayName("应从 ChatResponse 提取并记录 Token 使用")
        void shouldExtractAndRecordTokenUsage() {
            dev.langchain4j.model.output.TokenUsage langchainUsage =
                    new dev.langchain4j.model.output.TokenUsage(100, 200);
            ChatResponse response = mock(ChatResponse.class);
            when(response.tokenUsage()).thenReturn(langchainUsage);

            TokenUsage result =
                    tracker.extractAndRecord(response, "trace-1", "user-1", "sess-1", "gpt-4");

            assertNotNull(result);
            assertEquals(100, result.promptTokens());
            assertEquals(200, result.completionTokens());
            verify(tokenUsageService).recordUsage("user-1", "sess-1", "trace-1", "gpt-4", response);
            verify(metricsService).recordTokenUsage("gpt-4", 100, 200);
        }

        @Test
        @DisplayName("响应为空时应返回 empty")
        void shouldReturnEmptyForNullResponse() {
            TokenUsage result =
                    tracker.extractAndRecord(null, "trace-1", "user-1", "sess-1", "gpt-4");

            assertNotNull(result);
            assertEquals(0, result.promptTokens());
            assertEquals(0, result.completionTokens());
        }

        @Test
        @DisplayName("响应无 Token 使用信息时应返回 empty")
        void shouldReturnEmptyWhenNoTokenUsage() {
            ChatResponse response = mock(ChatResponse.class);
            when(response.tokenUsage()).thenReturn(null);

            TokenUsage result =
                    tracker.extractAndRecord(response, "trace-1", "user-1", "sess-1", "gpt-4");

            assertNotNull(result);
            assertEquals(0, result.promptTokens());
            assertEquals(0, result.completionTokens());
        }
    }

    @Nested
    @DisplayName("手动记录 Token")
    class RecordManual {

        @Test
        @DisplayName("应正确手动记录流式 Token 使用")
        void shouldRecordManualTokenUsage() {
            tracker.recordManual("trace-1", "user-1", "sess-1", "gpt-4", 100, 200);

            verify(tokenUsageService).recordStreamingUsage("user-1", "sess-1", "gpt-4", 100, 200);
            verify(metricsService).recordTokenUsage("gpt-4", 100, 200);
        }
    }
}
