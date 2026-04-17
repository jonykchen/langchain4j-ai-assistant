package com.jonychen.service;

import com.jonychen.assistant.ChatAssistant;
import com.jonychen.exception.BusinessException;
import com.jonychen.model.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * AI 服务层
 *
 * 职责：
 * 1. 封装 ChatAssistant 调用
 * 2. 处理业务逻辑（如日志记录、敏感词过滤）
 * 3. 提供统一的 API 给 Controller 层
 */
@Service
public class AiService {

    private static final Logger LOG = LoggerFactory.getLogger(AiService.class);

    private final ChatAssistant chatAssistant;

    public AiService(ChatAssistant chatAssistant) {
        this.chatAssistant = chatAssistant;
    }

    /**
     * 同步对话
     *
     * @param message 用户消息
     * @return AI 完整回复
     */
    public String chat(String message) {
        try {
            LOG.debug("收到聊天请求: {}", truncateForLog(message));
            String reply = chatAssistant.chat(message);
            LOG.debug("聊天完成, 回复长度: {}", reply != null ? reply.length() : 0);
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
        LOG.debug("收到流式聊天请求: {}", truncateForLog(message));
        return chatAssistant.chatFlux(message)
                .doOnError(e -> LOG.error("流式聊天失败", e))
                .onErrorResume(e -> Flux.error(handleAiException(e)));
    }

    /**
     * 处理 AI 服务异常，转换为业务异常
     */
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

    /**
     * 截断日志内容，避免日志过长
     */
    private String truncateForLog(String text) {
        if (text == null) {
            return "null";
        }
        return text.length() > 100 ? text.substring(0, 100) + "..." : text;
    }
}
