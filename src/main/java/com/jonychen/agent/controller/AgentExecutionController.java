package com.jonychen.agent.controller;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jonychen.agent.core.AgentMetadata;
import com.jonychen.agent.core.AgentOrchestrator;
import com.jonychen.agent.core.AgentRequest;
import com.jonychen.agent.core.AgentRequestOptions;

import reactor.core.publisher.Flux;

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
            @RequestBody ExecuteRequest request, HttpServletRequest httpRequest) {

        // 从认证上下文获取当前用户 ID
        String userId = getCurrentUserId();

        log.info(
                "[AgentExecutionController] 收到执行请求: userId={}, input={}",
                userId,
                truncate(request.userInput(), 100));

        // 从 HttpServletRequest 提取客户端信息
        String clientIp = extractClientIp(httpRequest);
        String userAgent = extractUserAgent(httpRequest);

        // 构建 AgentRequest
        AgentRequest agentRequest =
                AgentRequest.of(
                        request.sessionId(),
                        userId,
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
    public ResponseEntity<ConfirmResponse> confirm(@RequestBody ConfirmRequest request) {
        String userId = getCurrentUserId();
        log.info(
                "[AgentExecutionController] 收到确认请求: traceId={}, confirmationId={}, approved={}, userId={}",
                request.traceId(),
                request.confirmationId(),
                request.approved(),
                userId);

        boolean success =
                orchestrator.confirmOperation(
                        request.traceId(), request.confirmationId(), request.approved(), userId);

        return ResponseEntity.ok(new ConfirmResponse(success, success ? "确认成功" : "确认失败"));
    }

    /** 获取可用 Agent 列表 */
    @GetMapping("/list")
    public ResponseEntity<List<AgentMetadata>> listAgents() {
        return ResponseEntity.ok(orchestrator.getAvailableAgents());
    }

    /** 取消执行 */
    @PostMapping("/cancel/{traceId}")
    public ResponseEntity<CancelResponse> cancel(@PathVariable String traceId) {
        log.info("[AgentExecutionController] 收到取消请求: traceId={}", traceId);

        boolean success = orchestrator.cancelExecution(traceId);
        return ResponseEntity.ok(new CancelResponse(success, success ? "取消成功" : "取消失败"));
    }

    // ===== 辅助方法 =====

    private String extractClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    private String extractUserAgent(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }

    /** 从 SecurityContext 获取当前认证用户的 ID */
    private String getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof String userId) {
            return userId;
        }
        return null;
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
            String userInput,
            Map<String, Object> params,
            AgentRequestOptions options) {}

    /** 确认请求 */
    public record ConfirmRequest(String traceId, String confirmationId, boolean approved) {}

    /** 确认响应 */
    public record ConfirmResponse(boolean success, String message) {}

    /** 取消响应 */
    public record CancelResponse(boolean success, String message) {}
}
