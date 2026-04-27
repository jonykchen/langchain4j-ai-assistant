package com.jonychen.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.jonychen.admin.service.TokenUsageService;
import com.jonychen.exception.BusinessException;
import com.jonychen.metrics.BusinessMetricsService;
import com.jonychen.model.ErrorCode;
import com.jonychen.model.LoadBalancedChatModel;
import com.jonychen.model.LoadBalancedStreamingChatModel;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

/**
 * AI 服务层
 *
 * <p>职责： 1. 封装聊天模型调用 2. 记录 Token 使用量 3. 管理对话记忆 4. 处理业务逻辑 5. 记录业务指标
 */
@Service
public class AiService {

    private static final Logger LOG = LoggerFactory.getLogger(AiService.class);

    private final LoadBalancedChatModel chatModel;
    private final LoadBalancedStreamingChatModel streamingChatModel;
    private final TokenUsageService tokenUsageService;
    private final BusinessMetricsService businessMetricsService;

    /** 会话记忆存储（按用户隔离，避免线程安全问题） */
    private final Map<String, ChatMemory> userChatMemories = new ConcurrentHashMap<>();

    /** 每个用户的最大消息数 */
    private static final int MAX_MESSAGES_PER_USER = 20;

    /** 系统提示词 */
    private static final String SYSTEM_PROMPT =
            """
            你是一个友好的 AI 助手，专门帮助 Java 开发者学习 AI Agent 开发。

            ## 输出格式要求
            在回答问题之前，请先用 <thinking></thinking> 标签展示你的思考过程。
            思考过程应该简洁明了，展示你分析问题的思路。
            然后再给出最终回答。

            ## 回答风格
            - 使用 Markdown 格式，代码块指定语言
            - 分点阐述，条理清晰
            - 提供可运行的代码示例
            """;

    public AiService(
            LoadBalancedChatModel chatModel,
            LoadBalancedStreamingChatModel streamingChatModel,
            TokenUsageService tokenUsageService,
            BusinessMetricsService businessMetricsService) {
        this.chatModel = chatModel;
        this.streamingChatModel = streamingChatModel;
        this.tokenUsageService = tokenUsageService;
        this.businessMetricsService = businessMetricsService;
    }

    /**
     * 同步对话
     *
     * @param message 用户消息
     * @return AI 完整回复
     */
    public String chat(String message) {
        String userId = getCurrentUserId();
        long startTime = System.currentTimeMillis();

        try {
            LOG.trace("收到聊天请求: {}", truncateForLog(message));

            // 记录用户消息
            businessMetricsService.recordUserMessage(userId);

            // 获取用户专属的对话记忆
            ChatMemory chatMemory = getOrCreateChatMemory(userId);

            // 构建消息列表
            List<ChatMessage> messages = buildMessages(message, chatMemory);

            // 调用模型
            ChatRequest request = ChatRequest.builder().messages(messages).build();

            ChatResponse response = chatModel.chat(request);

            // 记录 Token 使用
            recordTokenUsage(response);

            // 获取回复内容
            String reply = response.aiMessage().text();

            // 保存到对话记忆
            chatMemory.add(UserMessage.from(message));
            chatMemory.add(AiMessage.from(reply));

            // 记录 AI 响应
            businessMetricsService.recordAiResponse(getModelName(response));

            LOG.trace("聊天完成, 回复长度: {}", reply != null ? reply.length() : 0);
            return reply;

        } catch (Exception e) {
            LOG.error("聊天请求失败", e);
            throw handleAiException(e);
        }
    }

    /**
     * 流式对话
     *
     * @param message 用户消息
     * @return 响应式流，逐个 token 返回
     */
    public Flux<String> chatFlux(String message) {
        LOG.trace("收到流式聊天请求: {}", truncateForLog(message));

        // 获取用户 ID
        String userId = getCurrentUserId();

        // 获取用户专属的对话记忆
        ChatMemory chatMemory = getOrCreateChatMemory(userId);

        // 构建消息列表
        List<ChatMessage> messages = buildMessages(message, chatMemory);

        // 生成会话 ID
        String sessionId = UUID.randomUUID().toString();

        // 记录用户消息和流式请求开始
        businessMetricsService.recordUserMessage(userId);
        businessMetricsService.sessionStarted(userId);

        // 保存用户消息到记忆
        chatMemory.add(UserMessage.from(message));

        return Flux.<String>create(
                        emitter -> {
                            StringBuilder fullResponse = new StringBuilder();
                            long[] startTime = {System.currentTimeMillis()};
                            boolean[] firstToken = {true};

                            ChatRequest request = ChatRequest.builder().messages(messages).build();

                            streamingChatModel.chat(
                                    request,
                                    new StreamingChatResponseHandler() {
                                        @Override
                                        public void onPartialResponse(String partialResponse) {
                                            // 记录首字延迟
                                            if (firstToken[0]) {
                                                long latency =
                                                        System.currentTimeMillis() - startTime[0];
                                                businessMetricsService.recordStreamingLatency(
                                                        latency);
                                                firstToken[0] = false;
                                            }
                                            fullResponse.append(partialResponse);
                                            emitter.next(partialResponse);
                                        }

                                        @Override
                                        public void onCompleteResponse(
                                                ChatResponse completeResponse) {
                                            // 保存 AI 回复到记忆
                                            chatMemory.add(AiMessage.from(fullResponse.toString()));

                                            String modelName = getModelName(completeResponse);

                                            // 记录 Token 使用
                                            if (completeResponse.tokenUsage() != null) {
                                                tokenUsageService.recordUsage(
                                                        userId,
                                                        sessionId,
                                                        modelName,
                                                        completeResponse);
                                            } else {
                                                // 估算 Token（某些模型不返回 Token 使用信息）
                                                int estimatedPromptTokens =
                                                        estimateTokens(message + SYSTEM_PROMPT);
                                                int estimatedCompletionTokens =
                                                        estimateTokens(fullResponse.toString());
                                                tokenUsageService.recordStreamingUsage(
                                                        userId,
                                                        sessionId,
                                                        "unknown",
                                                        estimatedPromptTokens,
                                                        estimatedCompletionTokens);
                                            }

                                            // 记录 AI 响应和流式完成
                                            businessMetricsService.recordAiResponse(modelName);
                                            businessMetricsService.streamingRequestCompleted();

                                            emitter.complete();
                                        }

                                        @Override
                                        public void onError(Throwable error) {
                                            LOG.error("流式聊天失败", error);
                                            businessMetricsService.streamingRequestError(
                                                    error.getClass().getSimpleName());
                                            emitter.error(handleAiException(error));
                                        }
                                    });
                        },
                        FluxSink.OverflowStrategy.BUFFER)
                .doFinally(
                        signalType -> {
                            // 会话结束
                            businessMetricsService.sessionEnded(userId);
                        });
    }

    /** 构建消息列表（包含系统提示词和对话历史） */
    private List<ChatMessage> buildMessages(String userMessage, ChatMemory chatMemory) {
        List<ChatMessage> messages = new ArrayList<>();

        // 添加系统提示词
        messages.add(SystemMessage.from(SYSTEM_PROMPT));

        // 添加对话历史
        messages.addAll(chatMemory.messages());

        // 添加当前用户消息
        messages.add(UserMessage.from(userMessage));

        return messages;
    }

    /** 获取或创建用户的对话记忆 */
    private ChatMemory getOrCreateChatMemory(String userId) {
        return userChatMemories.computeIfAbsent(
                userId, k -> MessageWindowChatMemory.withMaxMessages(MAX_MESSAGES_PER_USER));
    }

    /** 清除指定用户的对话记忆 */
    public void clearChatMemory(String userId) {
        userChatMemories.remove(userId);
        LOG.debug("已清除用户 {} 的对话记忆", userId);
    }

    /** 记录 Token 使用 */
    private void recordTokenUsage(ChatResponse response) {
        try {
            String userId = getCurrentUserId();
            String modelName = getModelName(response);
            tokenUsageService.recordUsage(userId, null, modelName, response);
        } catch (Exception e) {
            LOG.warn("记录 Token 使用失败: {}", e.getMessage());
        }
    }

    /** 获取当前用户 ID */
    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return auth.getName();
        }
        return "anonymous";
    }

    /** 从响应中获取模型名称 */
    private String getModelName(ChatResponse response) {
        if (response.modelName() != null) {
            return response.modelName();
        }
        return "unknown";
    }

    /** 估算 Token 数量（粗略估算：1 Token ≈ 4 字符） */
    private int estimateTokens(String text) {
        if (text == null) {
            return 0;
        }
        return text.length() / 4;
    }

    /** 处理 AI 服务异常，转换为业务异常 */
    private BusinessException handleAiException(Throwable e) {
        String message = e.getMessage();
        if (message == null) {
            message = e.getClass().getSimpleName();
        }

        // 根据异常类型映射错误码
        if (message.contains("API key") || message.contains("Unauthorized")) {
            return new BusinessException(ErrorCode.AI_API_KEY_INVALID, "API Key 无效或未配置", e);
        } else if (message.contains("timeout") || message.contains("Timeout")) {
            return new BusinessException(ErrorCode.AI_REQUEST_TIMEOUT, e);
        } else if (message.contains("quota") || message.contains("rate limit")) {
            return new BusinessException(ErrorCode.AI_QUOTA_EXCEEDED, e);
        } else if (message.contains("model") || message.contains("not found")) {
            return new BusinessException(ErrorCode.AI_MODEL_NOT_AVAILABLE, e);
        }

        return new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI 服务异常: " + message, e);
    }

    /** 截断日志内容，避免日志过长 */
    private String truncateForLog(String text) {
        if (text == null) {
            return "null";
        }
        return text.length() > 100 ? text.substring(0, 100) + "..." : text;
    }
}
