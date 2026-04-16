package com.jonychen.service;

import com.jonychen.assistant.ChatAssistant;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * AI 服务层
 *
 * 职责：
 * 1. 封装 ChatAssistant 调用
 * 2. 处理业务逻辑（如日志记录、敏感词过滤）
 * 3. 提供统一的 API 给 Controller 层
 *
 * 为什么需要 Service 层？
 * - 分离关注点：Controller 处理 HTTP，Service 处理业务
 * - 便于复用：多个 Controller 可共用同一 Service
 * - 便于测试：可独立测试业务逻辑
 *
 * @author 30240
 */
@Service
public class AiService {

    private final ChatAssistant chatAssistant;

    /**
     * 构造函数注入
     */
    public AiService(ChatAssistant chatAssistant) {
        this.chatAssistant = chatAssistant;
    }

    /**
     * 同步对话
     *
     * @param message 用户消息
     * @return AI 完整回复
     *
     * 调用流程：
     * 1. ChatAssistant.chat() 被调用
     * 2. AiServices 动态代理拦截调用
     * 3. 构造 Prompt：SystemMessage + UserMessage + 历史消息
     * 4. 调用 LLM API
     * 5. 解析响应，保存到 ChatMemory
     * 6. 返回结果
     */
    public String chat(String message) {
        return chatAssistant.chat(message);
    }

    /**
     * 流式对话
     *
     * @param message 用户消息
     * @return 响应式流，逐个 token 返回
     *
     * 调用流程：
     * 1. ChatAssistant.chatFlux() 被调用
     * 2. 构造 Prompt
     * 3. 调用 LLM Streaming API
     * 4. 每个 token 到达时，立即发射到 Flux 流
     * 5. 完成后保存完整回复到 ChatMemory
     *
     * Flux 特点：
     * - 冷流：只有订阅时才开始执行
     * - 惰性：每个 token 生成时才发送
     * - 可取消：客户端断开连接时自动取消
     */
    public Flux<String> chatFlux(String message) {
        return chatAssistant.chatFlux(message);
    }

    /*
     * ==================== 可扩展的业务逻辑 ====================
     *
     * 1. 消息预处理
     *    public String chat(String message) {
     *        // 敏感词过滤
     *        message = sensitiveWordFilter.filter(message);
     *        // 消息长度限制
     *        if (message.length() > 2000) {
     *            throw new IllegalArgumentException("消息过长");
     *        }
     *        return chatAssistant.chat(message);
     *    }
     *
     * 2. 响应后处理
     *    public String chat(String message) {
     *        String reply = chatAssistant.chat(message);
     *        // 内容审核
     *        contentModerator.check(reply);
     *        // 记录日志
     *        logService.saveChatLog(message, reply);
     *        return reply;
     *    }
     *
     * 3. 重试机制
     *    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
     *    public String chat(String message) {
     *        return chatAssistant.chat(message);
     *    }
     *
     * 4. 缓存
     *    @Cacheable(value = "chat", key = "#message")
     *    public String chat(String message) {
     *        return chatAssistant.chat(message);
     *    }
     *
     * 5. 异步执行
     *    @Async
     *    public CompletableFuture<String> chatAsync(String message) {
     *        return CompletableFuture.completedFuture(chatAssistant.chat(message));
     *    }
     */
}
