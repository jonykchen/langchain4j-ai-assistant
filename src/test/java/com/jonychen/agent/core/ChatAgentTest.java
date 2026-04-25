package com.jonychen.agent.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jonychen.agent.impl.ChatAgent;
import com.jonychen.observability.trace.AgentTraceService;
import com.jonychen.tool.ToolRegistry;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

@ExtendWith(MockitoExtension.class)
class ChatAgentTest {

    @Mock private ChatModel chatModel;

    @Mock private ToolRegistry toolRegistry;

    @Mock private AgentTraceService traceService;

    private ChatAgent chatAgent;

    @BeforeEach
    void setUp() {
        chatAgent = new ChatAgent(chatModel, toolRegistry, traceService);
    }

    @Nested
    @DisplayName("元信息")
    class Metadata {

        @Test
        @DisplayName("元信息类型应为 CHAT")
        void metadataTypeShouldBeChat() {
            AgentMetadata metadata = chatAgent.getMetadata();
            assertEquals("chat", metadata.name());
            assertEquals("CHAT", metadata.agentType().name());
        }

        @Test
        @DisplayName("应支持流式执行")
        void shouldSupportStreaming() {
            assertTrue(chatAgent.getMetadata().supportsStreaming());
        }
    }

    @Nested
    @DisplayName("能力判断")
    class CanHandle {

        @Test
        @DisplayName("canHandle 应返回最低置信度 0.1")
        void canHandleShouldReturnLowConfidence() {
            AgentRequest request =
                    AgentRequest.of(
                            "sess-1",
                            "user-1",
                            "你好",
                            Map.of(),
                            AgentRequestOptions.defaults(),
                            "127.0.0.1",
                            "test");
            double confidence = chatAgent.canHandle(request);
            assertEquals(0.1, confidence, 0.001);
        }
    }

    @Nested
    @DisplayName("同步执行")
    class Execute {

        @Test
        @DisplayName("应正确调用 LLM 并返回结果")
        void shouldCallLlmAndReturnResult() {
            // Given
            ChatResponse mockResponse =
                    ChatResponse.builder()
                            .aiMessage(AiMessage.aiMessage("你好，我是AI助手"))
                            .tokenUsage(new TokenUsage(50, 100, 150))
                            .modelName("test-model")
                            .build();
            when(chatModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class)))
                    .thenReturn(mockResponse);

            AgentRequest request =
                    AgentRequest.of(
                            "sess-1",
                            "user-1",
                            "你好",
                            Map.of(),
                            AgentRequestOptions.defaults(),
                            "127.0.0.1",
                            "test");
            AgentContext context =
                    new AgentContext(
                            "trace-1",
                            "sess-1",
                            "user-1",
                            com.jonychen.agent.core.AgentType.CHAT,
                            toolRegistry,
                            MessageWindowChatMemory.withMaxMessages(10),
                            traceService,
                            AgentRequestOptions.defaults());

            // When
            AgentResult result = chatAgent.execute(request, context);

            // Then
            assertTrue(result.isSuccess());
            assertEquals("你好，我是AI助手", result.output());
        }

        @Test
        @DisplayName("LLM 异常应返回失败结果")
        void llmExceptionShouldReturnFailure() {
            // Given
            when(chatModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class)))
                    .thenThrow(new RuntimeException("LLM 服务不可用"));

            AgentRequest request =
                    AgentRequest.of(
                            "sess-1",
                            "user-1",
                            "你好",
                            Map.of(),
                            AgentRequestOptions.defaults(),
                            "127.0.0.1",
                            "test");
            AgentContext context =
                    new AgentContext(
                            "trace-1",
                            "sess-1",
                            "user-1",
                            com.jonychen.agent.core.AgentType.CHAT,
                            toolRegistry,
                            MessageWindowChatMemory.withMaxMessages(10),
                            traceService,
                            AgentRequestOptions.defaults());

            // When
            AgentResult result = chatAgent.execute(request, context);

            // Then
            assertFalse(result.isSuccess());
            assertNotNull(result.errorMessage());
        }
    }
}
