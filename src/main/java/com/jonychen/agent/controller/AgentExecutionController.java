package com.jonychen.agent.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jonychen.agent.core.AgentMetadata;
import com.jonychen.agent.core.AgentOrchestrator;
import com.jonychen.agent.core.AgentRequest;
import com.jonychen.agent.core.AgentRequestOptions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Agent 执行控制器
 *
 * <p>提供 Agent 执行的 REST API：
 *
 * <ul>
 *   <li>POST /api/agent/execute - 执行 Agent（SSE 流式响应）
 *   <li>POST /api/agent/confirm - 确认敏感操作
 *   <li>GET /api/agent/list - 获取可用 Agent 列表
 *   <li>POST /api/agent/cancel/{traceId} - 取消执行
 * </ul>
 *
 * @author jonychen
 */
@RestController
@RequestMapping("/api/agent")
public class AgentExecutionController {

    private static final Logger log = LoggerFactory.getLogger(AgentExecutionController.class);

    private final AgentOrchestrator orchestrator;
    private final ObjectMapper objectMapper;

    public AgentExecutionController(AgentOrchestrator orchestrator, ObjectMapper objectMapper) {
        this.orchestrator = orchestrator;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行 Agent（SSE 流式响应）
     *
     * <p>返回 ServerSentEvent 流，每个事件携带 AgentEvent 的 JSON 数据。 事件类型为 AgentEvent.eventType()。
     */
    @PostMapping(value = "/execute", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> execute(
            @RequestBody ExecuteRequest request, ServerWebExchange exchange) {
        log.info(
                "[AgentExecutionController] 收到执行请求: userId={}, input={}",
                request.userId(),
                truncate(request.userInput(), 100));

        // 从 WebExchange 提取客户端信息
        String clientIp = extractClientIp(exchange);
        String userAgent = extractUserAgent(exchange);

        // 构建 AgentRequest
        AgentRequest agentRequest =
                AgentRequest.of(
                        request.sessionId(),
                        request.userId(),
                        request.userInput(),
                        request.params() != null ? request.params() : Map.of(),
                        request.options() != null
                                ? request.options()
                                : AgentRequestOptions.defaults(),
                        clientIp,
                        userAgent);

        // 执行并转换为 SSE
        return orchestrator
                .executeStream(agentRequest)
                .map(
                        event ->
                                ServerSentEvent.<String>builder()
                                        .event(event.eventType())
                                        .data(toJson(event))
                                        .build());
    }

    /** 确认敏感操作 */
    @PostMapping("/confirm")
    public Mono<ResponseEntity<ConfirmResponse>> confirm(@RequestBody ConfirmRequest request) {
        log.info(
                "[AgentExecutionController] 收到确认请求: traceId={}, confirmationId={}, approved={}",
                request.traceId(),
                request.confirmationId(),
                request.approved());

        boolean success =
                orchestrator.confirmOperation(
                        request.traceId(),
                        request.confirmationId(),
                        request.approved(),
                        request.userId());

        return Mono.just(
                ResponseEntity.ok(new ConfirmResponse(success, success ? "确认成功" : "确认失败")));
    }

    /** 获取可用 Agent 列表 */
    @GetMapping("/list")
    public Mono<ResponseEntity<List<AgentMetadata>>> listAgents() {
        return Mono.just(ResponseEntity.ok(orchestrator.getAvailableAgents()));
    }

    /** 取消执行 */
    @PostMapping("/cancel/{traceId}")
    public Mono<ResponseEntity<CancelResponse>> cancel(@PathVariable String traceId) {
        log.info("[AgentExecutionController] 收到取消请求: traceId={}", traceId);

        boolean success = orchestrator.cancelExecution(traceId);
        return Mono.just(ResponseEntity.ok(new CancelResponse(success, success ? "取消成功" : "取消失败")));
    }

    // ===== 辅助方法 =====

    private String extractClientIp(ServerWebExchange exchange) {
        String ip = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        }
        if (ip == null || ip.isEmpty()) {
            ip =
                    exchange.getRequest().getRemoteAddress() != null
                            ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                            : "unknown";
        }
        return ip;
    }

    private String extractUserAgent(ServerWebExchange exchange) {
        return exchange.getRequest().getHeaders().getFirst("User-Agent");
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("[AgentExecutionController] JSON 序列化失败: {}", e.getMessage());
            return "{}";
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return null;
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    // ===== 请求/响应 DTO =====

    /** 执行请求 */
    public record ExecuteRequest(
            String sessionId,
            String userId,
            String userInput,
            Map<String, Object> params,
            AgentRequestOptions options) {}

    /** 确认请求 */
    public record ConfirmRequest(
            String traceId, String confirmationId, boolean approved, String userId) {}

    /** 确认响应 */
    public record ConfirmResponse(boolean success, String message) {}

    /** 取消响应 */
    public record CancelResponse(boolean success, String message) {}
}
