package com.jonychen.controller;

import com.jonychen.model.ApiResponse;
import com.jonychen.model.ChatRequest;
import com.jonychen.model.ChatResponse;
import com.jonychen.model.ErrorCode;
import com.jonychen.service.AiService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * 聊天控制器
 *
 * Resilience4j 容错能力：
 * - @RateLimiter: 限流，防止 API 滥用
 * - @CircuitBreaker: 熔断，AI 服务异常时快速失败
 * - @Retry: 重试，网络抖动时自动重试
 *
 * @author 30240
 */
@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
@SuppressWarnings("unused")
public class ChatController {

    private final AiService aiService;

    public ChatController(AiService aiService) {
        this.aiService = aiService;
    }

    /**
     * 同步对话接口
     *
     * 容错配置：
     * - 限流：每分钟 20 次（开发环境 100 次）
     * - 熔断：失败率 30% 触发熔断，等待 30 秒后半开
     * - 重试：失败后最多重试 2 次
     */
    @PostMapping
    @RateLimiter(name = "chat", fallbackMethod = "chatRateLimitFallback")
    @CircuitBreaker(name = "chat", fallbackMethod = "chatCircuitBreakerFallback")
    @Retry(name = "chat", fallbackMethod = "chatRetryFallback")
    public ApiResponse<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        String reply = aiService.chat(request.message());
        return ApiResponse.success(new ChatResponse(reply));
    }

    /**
     * 流式对话接口（SSE - Server-Sent Events）
     *
     * 容错配置：
     * - 限流：每分钟 30 次（开发环境 150 次）
     * - 熔断：失败率 30% 触发熔断
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimiter(name = "chatStream", fallbackMethod = "chatStreamFallback")
    @CircuitBreaker(name = "chat", fallbackMethod = "chatStreamCircuitBreakerFallback")
    public Flux<ServerSentEvent<String>> chatStream(@Valid @RequestBody ChatRequest request) {
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

    // ==================== Fallback 方法 ====================

    /**
     * 限流降级：请求过于频繁
     */
    public ApiResponse<ChatResponse> chatRateLimitFallback(
            ChatRequest request, io.github.resilience4j.ratelimiter.RequestNotPermitted e) {
        return ApiResponse.error(ErrorCode.RATE_LIMITED, "请求过于频繁，请稍后再试");
    }

    /**
     * 熔断降级：AI 服务不可用
     */
    public ApiResponse<ChatResponse> chatCircuitBreakerFallback(
            ChatRequest request, io.github.resilience4j.circuitbreaker.CallNotPermittedException e) {
        return ApiResponse.error(ErrorCode.AI_SERVICE_ERROR, "AI 服务暂时不可用，请稍后再试");
    }

    /**
     * 重试降级：重试次数用尽
     */
    public ApiResponse<ChatResponse> chatRetryFallback(ChatRequest request, Throwable t) {
        return ApiResponse.error(ErrorCode.AI_SERVICE_ERROR, "AI 服务暂时不可用: " + t.getMessage());
    }

    /**
     * 流式接口限流降级
     */
    public Flux<ServerSentEvent<String>> chatStreamFallback(
            ChatRequest request, io.github.resilience4j.ratelimiter.RequestNotPermitted e) {
        return Flux.just(ServerSentEvent.<String>builder()
                .event("error")
                .data("请求过于频繁，请稍后再试")
                .build());
    }

    /**
     * 流式接口熔断降级
     */
    public Flux<ServerSentEvent<String>> chatStreamCircuitBreakerFallback(
            ChatRequest request, io.github.resilience4j.circuitbreaker.CallNotPermittedException e) {
        return Flux.just(ServerSentEvent.<String>builder()
                .event("error")
                .data("AI 服务暂时不可用，请稍后再试")
                .build());
    }
}
