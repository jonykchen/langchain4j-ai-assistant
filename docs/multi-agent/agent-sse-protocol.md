# SSE 协议扩展设计

> 多 Agent 生产级系统技术方案 - 子文档

---

## 1. 现有协议

### 1.1 当前 SSE 事件

现有 `/api/chat/stream` 使用三种 SSE 事件：

```
event: token
data: 你

event: token
data: 好

event: done
data: [DONE]
```

**局限性：**
- 只有 token 级别的事件，无法表达 Agent 执行步骤
- 无结构化元数据，前端无法区分"思考"和"回答"
- 缺少确认机制，敏感操作无法暂停

### 1.2 扩展目标

| 目标 | 说明 |
|------|------|
| 步骤粒度事件 | 每个 Agent 步骤（思考、工具调用、结果）都是独立事件 |
| 结构化数据 | 事件携带 JSON 元数据，前端可精确渲染 |
| 双向通信 | 支持确认流程（前端确认后继续执行） |
| 向后兼容 | 现有 `/api/chat/stream` 协议不变 |

---

## 2. 新 SSE 协议

### 2.1 事件类型总览

| 事件类型 | 方向 | 说明 |
|---------|------|------|
| `step_start` | Server → Client | 步骤开始 |
| `step_end` | Server → Client | 步骤结束 |
| `thought` | Server → Client | Agent 思考过程 |
| `tool_call` | Server → Client | 工具调用请求 |
| `tool_result` | Server → Client | 工具执行结果 |
| `agent_call` | Server → Client | 委托其他 Agent |
| `agent_result` | Server → Client | 委托 Agent 执行完成 |
| `confirmation_required` | Server → Client | 需要用户确认 |
| `agent_done` | Server → Client | Agent 执行完成 |
| `agent_error` | Server → Client | Agent 执行出错 |
| `heartbeat` | Server → Client | 心跳（每 30s，防止 Nginx 超时断开） |

> **注意**：`confirmation_response`（用户确认/拒绝）不是 SSE 事件，而是通过 REST API `POST /api/agent/confirm` 发送，详见下方"确认操作"章节。
| `token` | Server → Client | 流式 token（兼容现有） |
| `done` | Server → Client | 完成标记（兼容现有） |

### 2.2 事件详细定义

> **事件序号机制**：所有事件均包含 `sequenceNumber` 字段（递增整数），前端据此检测事件间隙并请求补发，解决弱网环境丢事件问题。同时前端使用 `Set` 对已处理的 sequenceNumber 去重，防止重连后重复渲染。

#### step_start

```
event: step_start
data: {
  "traceId": "trace-abc123",
  "sequenceNumber": 1,
  "stepIndex": 0,
  "type": "LLM_CALL",
  "agentName": "ops",
  "timestamp": "2026-04-24T10:30:00Z"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| traceId | string | 追踪 ID |
| sequenceNumber | int | 事件序号（全局递增，用于前端检测丢事件） |
| stepIndex | int | 步骤序号（从 0 开始） |
| type | string | 步骤类型：LLM_CALL, TOOL_CALL, AGENT_CALL |
| agentName | string | 执行 Agent 名称 |
| timestamp | string | ISO 8601 时间戳 |

#### step_end

```
event: step_end
data: {
  "traceId": "trace-abc123",
  "sequenceNumber": 2,
  "stepIndex": 0,
  "success": true,
  "summary": "获取了 4 个模型的健康状态",
  "durationMs": 1230,
  "timestamp": "2026-04-24T10:30:01Z"
}
```

#### thought

```
event: thought
data: {
  "traceId": "trace-abc123",
  "sequenceNumber": 3,
  "stepIndex": 1,
  "content": "DeepSeek 模型延迟高达 1200ms 且已触发熔断，需要降低其流量分配",
  "timestamp": "2026-04-24T10:30:02Z"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| content | string | 思考内容 |

#### tool_call

```
event: tool_call
data: {
  "traceId": "trace-abc123",
  "sequenceNumber": 4,
  "stepIndex": 2,
  "toolName": "adjust_model_weight",
  "params": {
    "modelName": "deepseek",
    "weight": 5,
    "reason": "延迟过高且已熔断，降低流量分配"
  },
  "timestamp": "2026-04-24T10:30:03Z"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| toolName | string | 工具名称 |
| params | object | 工具参数 |

#### tool_result

```
event: tool_result
data: {
  "traceId": "trace-abc123",
  "sequenceNumber": 5,
  "stepIndex": 2,
  "toolName": "adjust_model_weight",
  "result": {
    "modelName": "deepseek",
    "oldWeight": 15,
    "newWeight": 5,
    "reason": "延迟过高且已熔断，降低流量分配"
  },
  "success": true,
  "error": null,
  "executionTimeMs": 150,
  "timestamp": "2026-04-24T10:30:04Z"
}
```

#### agent_call

```
event: agent_call
data: {
  "traceId": "trace-abc123",
  "sequenceNumber": 6,
  "stepIndex": 3,
  "targetAgent": "data",
  "input": "查询 deepseek 模型过去 24 小时的平均延迟",
  "timestamp": "2026-04-24T10:30:05Z"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| targetAgent | string | 目标 Agent 名称 |
| input | string | 委托输入 |

#### agent_result

```
event: agent_result
data: {
  "traceId": "trace-abc123",
  "sequenceNumber": 7,
  "stepIndex": 3,
  "agentName": "data",
  "output": "deepseek 模型过去 24 小时平均延迟为 1150ms",
  "success": true,
  "timestamp": "2026-04-24T10:30:08Z"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| agentName | string | 完成委托的 Agent 名称 |
| output | string | 委托执行结果 |
| success | boolean | 是否成功 |

#### confirmation_required

```
event: confirmation_required
data: {
  "traceId": "trace-abc123",
  "sequenceNumber": 8,
  "stepIndex": 2,
  "confirmationId": "confirm_a1b2c3",
  "operation": "adjust_model_weight",
  "description": "即将调整 deepseek 模型权重从 15 到 5",
  "riskLevel": "HIGH",
  "params": {
    "modelName": "deepseek",
    "weight": 5
  },
  "timestamp": "2026-04-24T10:30:03Z"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| confirmationId | string | 确认 ID（用于回复） |
| operation | string | 操作名称 |
| description | string | 操作描述 |
| riskLevel | string | LOW / MEDIUM / HIGH / CRITICAL |
| params | object | 操作参数 |

#### confirmation_response（客户端 → 服务端）

```
POST /api/agent/confirm
Content-Type: application/json

{
  "confirmationId": "confirm_a1b2c3",
  "traceId": "trace-abc123",
  "approved": true,
  "userId": "user-123"
}
```

#### agent_done

```
event: agent_done
data: {
  "traceId": "trace-abc123",
  "sequenceNumber": 10,
  "output": "DeepSeek 模型延迟过高已触发熔断，已将其权重从 15 降至 5。当前流量由 dashscope（71%）和 zhipu（29%）承担。建议持续观察，如果 30 分钟内恢复可回调权重。",
  "totalSteps": 4,
  "tokenUsage": {
    "promptTokens": 3500,
    "completionTokens": 800,
    "totalTokens": 4300
  },
  "durationMs": 4600,
  "agentName": "ops",
  "timestamp": "2026-04-24T10:30:06Z"
}
```

#### agent_error

```
event: agent_error
data: {
  "traceId": "trace-abc123",
  "sequenceNumber": 9,
  "errorCode": "TOOL_EXECUTION_FAILED",
  "message": "工具执行失败: adjust_model_weight",
  "details": "模型 deepseek 不存在",
  "recoverable": true,
  "timestamp": "2026-04-24T10:30:05Z"
}
```

---

## 3. 后端实现

### 3.1 AgentExecutionController

```java
package com.jonychen.agent.controller;

import com.jonychen.agent.core.*;
import com.jonychen.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import java.util.Map;

/**
 * Agent 执行控制器
 * 
 * 使用 Reactor Flux + ServerSentEvent 实现流式响应，
 * 与 langchain4j-reactor 原生集成，支持背压控制。
 * 
 * 注意：本 Controller 运行在 WebFlux 环境下（项目已引入 spring-boot-starter-webflux），
 * 使用 ServerSentEvent（WebFlux）而非 SseEmitter（Servlet）。
 * 客户端 IP 和 UA 从 WebFlux 的 ServerWebExchange 中提取。
 */
@RestController
@RequestMapping("/api/agent")
@Tag(name = "Agent 执行", description = "Agent 执行与交互接口")
public class AgentExecutionController {

    private final AgentOrchestrator orchestrator;
    private final AgentRegistry agentRegistry;

    public AgentExecutionController(
            AgentOrchestrator orchestrator,
            AgentRegistry agentRegistry) {
        this.orchestrator = orchestrator;
        this.agentRegistry = agentRegistry;
    }

    /**
     * 执行 Agent（SSE 流式）
     *
     * 请求示例：
     * POST /api/agent/execute
     * {
     *   "message": "检查模型健康状态并优化权重分配"
     * }
     *
     * 响应：SSE 事件流（Flux<ServerSentEvent>，支持背压）
     */
    @PostMapping(value = "/execute", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "执行 Agent", description = "启动 Agent 执行，返回 SSE 事件流")
    public Flux<ServerSentEvent<?>> execute(
            @RequestBody AgentExecutionRequest request,
            @AuthenticationPrincipal User user,
            ServerWebExchange exchange) {

        // 提取客户端信息（WebFlux 同步提取，避免异步线程依赖请求对象）
        String clientIp = Optional.ofNullable(exchange.getRequest().getHeaders().getFirst("X-Forwarded-For"))
                .map(ip -> ip.split(",")[0].trim())
                .orElseGet(() -> exchange.getRequest().getRemoteAddress() != null 
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown");
        String userAgent = exchange.getRequest().getHeaders().getFirst("User-Agent");

        // 构建执行请求
        AgentRequest agentRequest = new AgentRequest(
            UUID.randomUUID().toString(),
            user.getId(),
            request.message(),
            request.params(),
            request.options() != null ? request.options() : AgentRequestOptions.defaults(),
            clientIp,
            userAgent
        );

        // 直接返回 Flux<ServerSentEvent>，框架自动处理 SSE 编码和背压
        return orchestrator.executeStream(agentRequest)
            .map(event -> ServerSentEvent.builder()
                .event(event.eventType())
                .data(event)
                .build())
            .concatWithValues(
                ServerSentEvent.builder()
                    .event("done")
                    .data("[DONE]")
                    .build())
            .onErrorResume(error -> Flux.just(
                ServerSentEvent.builder()
                    .event("agent_error")
                    .data(Map.of(
                        "errorCode", "INTERNAL_ERROR",
                        "message", error.getMessage() != null ? error.getMessage() : "Unknown error"
                    ))
                    .build()));
    }
    
    /**
     * 确认敏感操作
     */
    @PostMapping("/confirm")
    @Operation(summary = "确认操作", description = "确认或拒绝 Agent 的敏感操作")
    public ApiResponse<Void> confirmOperation(
            @RequestBody ConfirmationRequest request,
            @AuthenticationPrincipal User user) {
        
        orchestrator.confirmOperation(
            request.confirmationId(),
            request.traceId(),
            user.getId(),
            request.approved()
        );
        
        return ApiResponse.success();
    }
    
    /**
     * 获取可用 Agent 列表
     */
    @GetMapping("/list")
    @Operation(summary = "Agent 列表", description = "获取所有可用 Agent")
    public ApiResponse<List<AgentMetadata>> listAgents() {
        return ApiResponse.success(agentRegistry.getAllMetadata());
    }
    
    /**
     * 获取 Agent 执行历史
     */
    @GetMapping("/history")
    @Operation(summary = "执行历史", description = "获取 Agent 执行历史")
    public ApiResponse<Page<AgentResult>> getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User user) {
        // 查询该用户的执行历史
        return ApiResponse.success(orchestrator.getHistory(user.getId(), page, size));
    }
    
    /**
     * 取消 Agent 执行
     */
    @PostMapping("/cancel/{traceId}")
    @Operation(summary = "取消执行", description = "取消正在执行的 Agent")
    public ApiResponse<Void> cancelExecution(
            @PathVariable String traceId,
            @AuthenticationPrincipal User user) {
        orchestrator.cancelExecution(traceId, user.getId());
        return ApiResponse.success();
    }
    

}

/**
 * Agent 执行请求
 */
record AgentExecutionRequest(
    String message,
    Map<String, Object> params,
    AgentRequestOptions options
) {}

/**
 * 确认请求
 */
record ConfirmationRequest(
    String confirmationId,
    String traceId,
    boolean approved
) {}
```

### 3.2 AgentOrchestrator（重构版）

```java
package com.jonychen.agent.core;

import reactor.core.publisher.Flux;
import org.springframework.stereotype.Service;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agent 编排器（重构版）
 * 
 * 替代现有 AgentOrchestrator，使用 LLM 路由替代关键词匹配。
 * 
 * 内部使用 Reactor Flux 进行事件流管理，与 langchain4j-reactor 原生集成，
 * Controller 层直接返回 Flux<ServerSentEvent>，支持背压控制。
 */
@Service
public class AgentOrchestrator {

    private static final int MAX_GLOBAL_CONCURRENT = 20; // 全局最大并发执行数

    private final RouterAgent routerAgent;
    private final AgentRegistry agentRegistry;
    private final AgentTraceService traceService;
    private final Map<String, ActiveExecution> activeExecutions;
    private final ScheduledExecutorService cleanupExecutor;
    private final ScheduledExecutorService heartbeatExecutor;
    private final AtomicInteger globalConcurrentExecutions = new AtomicInteger(0);

    public AgentOrchestrator(
            RouterAgent routerAgent,
            AgentRegistry agentRegistry,
            AgentTraceService traceService) {
        this.routerAgent = routerAgent;
        this.agentRegistry = agentRegistry;
        this.traceService = traceService;
        this.activeExecutions = new ConcurrentHashMap<>();

        // 启动定时清理任务，每 60 秒移除已完成/超时的执行记录，防止内存泄漏
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "agent-execution-cleanup");
            t.setDaemon(true);
            return t;
        });
        this.cleanupExecutor.scheduleAtFixedRate(this::cleanupStaleExecutions, 60, 60, TimeUnit.SECONDS);

        // 共享心跳线程池（避免每次执行都新建线程池导致线程泄漏）
        this.heartbeatExecutor = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "agent-heartbeat");
            t.setDaemon(true);
            return t;
        });
    }

    @PreDestroy
    public void shutdown() {
        cleanupExecutor.shutdown();
        heartbeatExecutor.shutdown();
    }

    /**
     * 流式执行 Agent（带心跳保活 + 全局并发限流）
     */
    public Flux<AgentEvent> executeStream(AgentRequest request) {
        // 全局并发限流：防止系统被大量 Agent 执行压垮
        if (globalConcurrentExecutions.incrementAndGet() > MAX_GLOBAL_CONCURRENT) {
            globalConcurrentExecutions.decrementAndGet();
            return Flux.error(new AgentException(
                "系统繁忙，当前并发执行过多，请稍后重试", "GLOBAL_RATE_LIMIT"
            ));
        }

        return Flux.create(sink -> {
            String traceId = request.sessionId();
            long startTime = System.currentTimeMillis();
            AtomicInteger seqCounter = new AtomicInteger(0);
            
            // 启动心跳（每 30 秒发送一次，防止 Nginx 代理超时断开）
            ScheduledFuture<?> heartbeatFuture = heartbeatExecutor.scheduleAtFixedRate(
                () -> {
                    try {
                        sink.next(new AgentEvent.Heartbeat(traceId, seqCounter.getAndIncrement(), Instant.now()));
                    } catch (Exception ignored) {
                        // sink 已关闭时忽略
                    }
                }, 30, 30, TimeUnit.SECONDS);
            
            try {
                // 1. 路由决策
                RoutingResult routing = routerAgent.route(request.userInput());
                
                // 发送路由事件
                sink.next(AgentEvent.stepStart(traceId, seqCounter.getAndIncrement(), 0, StepType.AGENT_CALL, "router"));
                sink.next(new AgentEvent.AgentCall(
                    traceId, seqCounter.getAndIncrement(), Instant.now(), 0, routing.selectedAgent(), request.userInput()));
                sink.next(new AgentEvent.StepEnd(
                    traceId, seqCounter.getAndIncrement(), Instant.now(), 0, true, 
                    "路由到 " + routing.selectedAgent() + ": " + routing.reason()));
                
                // 2. 获取目标 Agent
                Agent targetAgent = agentRegistry.getAgent(routing.selectedAgent())
                    .orElseThrow(() -> new AgentNotFoundException(routing.selectedAgent()));
                
                // 3. 构建上下文（必须传入 request.options，否则确认恢复和超时机制无法工作）
                //    将 Orchestrator 的 seqCounter 传入 AgentContext，确保跨组件事件序号连续
                AgentContext context = new AgentContext(
                    traceId, request.sessionId(), request.userId(),
                    targetAgent.getMetadata().agentType(),
                    null, null, traceService,
                    request.options(),
                    seqCounter  // 共享同一个 AtomicInteger，确保序号连续
                );
                
                // 记录活跃执行（带创建时间戳，用于过期清理）
                activeExecutions.put(traceId, new ActiveExecution(traceId, context, targetAgent, System.currentTimeMillis()));
                
                // 4. 执行目标 Agent
                targetAgent.executeStream(request, context)
                    .subscribe(
                        event -> sink.next(event),
                        error -> {
                            sink.next(new AgentEvent.AgentError(
                                traceId, seqCounter.getAndIncrement(), Instant.now(), 
                                "EXECUTION_ERROR", error.getMessage(), null, true));
                            traceService.endTraceFailed(traceId, error.getMessage());
                            activeExecutions.remove(traceId);
                            heartbeatFuture.cancel(false);
                            sink.complete();
                        },
                        () -> {
                            activeExecutions.remove(traceId);
                            heartbeatFuture.cancel(false);
                            sink.complete();
                        }
                    );
                
            } catch (Exception e) {
                sink.next(new AgentEvent.AgentError(
                    traceId, seqCounter.getAndIncrement(), Instant.now(),
                    "ROUTING_ERROR", e.getMessage(), null, false));
                heartbeatFuture.cancel(false);
                sink.complete();
            }
        }).doFinally(signal -> globalConcurrentExecutions.decrementAndGet());
    }

    /**
     * 确认操作（唤醒等待中的 Agent）
     * 
     * 安全校验：只有发起该执行的原始用户才能确认操作，防止越权确认。
     */
    public void confirmOperation(String confirmationId, String traceId, String userId, boolean approved) {
        ActiveExecution execution = activeExecutions.get(traceId);
        if (execution == null) {
            LOG.warn("确认操作失败：找不到活跃执行，traceId={}", traceId);
            return;
        }
        // 校验操作者必须是执行发起者
        if (!execution.context().getUserId().equals(userId)) {
            LOG.warn("确认操作越权：执行发起者={}, 当前操作者={}, traceId={}", 
                execution.context().getUserId(), userId, traceId);
            throw new AgentPermissionException("只有执行发起者才能确认该操作");
        }
        execution.context().resolveConfirmation(confirmationId, approved);
    }
    
    /**
     * 取消执行
     */
    public void cancelExecution(String traceId, String userId) {
        ActiveExecution execution = activeExecutions.get(traceId);
        if (execution != null) {
            execution.context().cancel();
        }
    }
    
    /**
     * 获取执行历史（分页）
     */
    public Page<AgentResult> getHistory(String userId, int page, int size) {
        return traceService.queryHistory(userId, page, size);
    }
    
    /**
     * 清理过期执行记录（防止内存泄漏）
     */
    private void cleanupStaleExecutions() {
        long now = System.currentTimeMillis();
        activeExecutions.entrySet().removeIf(entry -> {
            boolean stale = (now - entry.getValue().createdAt()) > TimeUnit.MINUTES.toMillis(10);
            if (stale) {
                entry.getValue().context().cancel();
            }
            return stale;
        });
    }
}

record ActiveExecution(String traceId, AgentContext context, Agent agent, long createdAt) {}
```

---

## 4. 前端 SSE 客户端

### 4.1 AgentEventStream

```typescript
// frontend/src/api/agent.ts

/**
 * Agent 执行 API
 */

import type { ApiResponse } from '@/types'

const API_BASE = '/api/agent'

// ===== 类型定义 =====

export interface AgentExecutionRequest {
  message: string
  params?: Record<string, unknown>
  options?: AgentRequestOptions
}

export interface AgentRequestOptions {
  maxIterations?: number
  timeout?: number
  requireConfirmation?: boolean
  debugMode?: boolean
}

export interface ConfirmationRequest {
  confirmationId: string
  traceId: string
  approved: boolean
}

export interface AgentMetadata {
  name: string
  displayName: string
  description: string
  version: string
  capabilities: string[]
  requiredPermissions: string[]
  maxIterations: number
  timeout: number
  supportsStreaming: boolean
}

// ===== 精确类型定义（替代 string/unknown） =====

export type StepType = 'LLM_CALL' | 'TOOL_CALL' | 'AGENT_CALL'

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

export interface SSEOptions {
  onReconnect?: (attempt: number) => void
  onConnected?: () => void
}

// ===== SSE 事件类型 =====

export interface BaseAgentEvent {
  traceId: string
  sequenceNumber: number  // 事件序号（全局递增，用于检测丢事件和重连恢复）
  timestamp: string
}

export interface StepStartEvent extends BaseAgentEvent {
  eventType: 'step_start'
  stepIndex: number
  type: StepType
  agentName: string
}

export interface StepEndEvent extends BaseAgentEvent {
  eventType: 'step_end'
  stepIndex: number
  success: boolean
  summary: string
  durationMs?: number
}

export interface ThoughtEvent extends BaseAgentEvent {
  eventType: 'thought'
  stepIndex: number
  content: string
}

export interface ToolCallEvent extends BaseAgentEvent {
  eventType: 'tool_call'
  stepIndex: number
  toolName: string
  params: Record<string, unknown>
}

export interface ToolResultEvent extends BaseAgentEvent {
  eventType: 'tool_result'
  stepIndex: number
  toolName: string
  result: unknown
  success: boolean
  error: string | null
  executionTimeMs?: number
}

export interface AgentCallEvent extends BaseAgentEvent {
  eventType: 'agent_call'
  stepIndex: number
  targetAgent: string
  input: string
}

export interface AgentResultEvent extends BaseAgentEvent {
  eventType: 'agent_result'
  stepIndex: number
  agentName: string
  output: string
  success: boolean
}

export interface ConfirmationRequiredEvent extends BaseAgentEvent {
  eventType: 'confirmation_required'
  stepIndex: number
  confirmationId: string
  operation: string
  description: string
  riskLevel: RiskLevel
  params: Record<string, unknown>
}

export interface AgentDoneEvent extends BaseAgentEvent {
  eventType: 'agent_done'
  output: string
  totalSteps: number
  tokenUsage: {
    promptTokens: number
    completionTokens: number
    totalTokens: number
  }
  durationMs: number
  agentName: string
}

export interface HeartbeatEvent extends BaseAgentEvent {
  eventType: 'heartbeat'
}

export interface AgentErrorEvent extends BaseAgentEvent {
  eventType: 'agent_error'
  errorCode: string
  message: string
  details: string | null
  recoverable: boolean
}

export type AgentEvent =
  | StepStartEvent
  | StepEndEvent
  | ThoughtEvent
  | ToolCallEvent
  | ToolResultEvent
  | AgentCallEvent
  | AgentResultEvent
  | ConfirmationRequiredEvent
  | AgentDoneEvent
  | HeartbeatEvent
  | AgentErrorEvent

// ===== API 调用 =====

/**
 * 执行 Agent（SSE 流式，生产级增强）
 *
 * 增强能力：
 * 1. 自动重连（指数退避，最多 5 次）
 * 2. 事件序号检测，去重 + 乱序处理
 * 3. 心跳超时检测（35s 无事件触发重连）
 *
 * @param request 执行请求
 * @param options SSE 事件回调
 * @returns AsyncGenerator，每次 yield 一个 AgentEvent
 */
export async function* executeAgent(
  request: AgentExecutionRequest,
  options: SSEOptions = {}
): AsyncGenerator<AgentEvent> {
  const token = localStorage.getItem('access_token')
  let lastSequenceNumber = 0
  let reconnectCount = 0
  const MAX_RECONNECT = 5
  const HEARTBEAT_TIMEOUT_MS = 35000 // 心跳间隔 30s，超时 35s

  let isDone = false
  const processedSequences = new Set<number>()

  async function connect(): Promise<ReadableStreamDefaultReader<Uint8Array> | null> {
    try {
      const response = await fetch(`${API_BASE}/execute`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify(request),
      })

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }

      if (options.onConnected) options.onConnected()
      reconnectCount = 0
      return response.body?.getReader() || null
    } catch (e) {
      if (reconnectCount >= MAX_RECONNECT) {
        throw e
      }
      return null
    }
  }

  let reader: ReadableStreamDefaultReader<Uint8Array> | null = null
  let heartbeatTimer: number | null = null

  function resetHeartbeat() {
    if (heartbeatTimer) window.clearTimeout(heartbeatTimer)
    heartbeatTimer = window.setTimeout(() => {
      // 心跳超时，触发重连
      reader?.cancel().catch(() => {})
    }, HEARTBEAT_TIMEOUT_MS)
  }

  try {
    while (!isDone) {
      reader = await connect()
      if (!reader) {
        // 连接失败，等待重连
        reconnectCount++
        if (options.onReconnect) options.onReconnect(reconnectCount)
        const delay = Math.min(1000 * Math.pow(2, reconnectCount - 1), 30000)
        await new Promise((r) => setTimeout(r, delay))
        continue
      }

      resetHeartbeat()

      const decoder = new TextDecoder()
      let buffer = ''
      let currentEvent = ''
      let currentData = ''

      try {
        while (true) {
          const { done, value } = await reader.read()
          if (done) break

          resetHeartbeat()
          buffer += decoder.decode(value, { stream: true })
          const lines = buffer.split('\n')
          buffer = lines.pop() || ''

          for (const line of lines) {
            if (line.startsWith('event:')) {
              currentEvent = line.slice(6).trim()
            } else if (line.startsWith('data:')) {
              const dataContent = line.slice(5)
              currentData += dataContent.startsWith(' ')
                ? dataContent.slice(1)
                : dataContent
            } else if (line === '' && currentEvent && currentData) {
              if (currentEvent === 'done' && currentData === '[DONE]') {
                isDone = true
                return
              }

              try {
                const event: AgentEvent = {
                  eventType: currentEvent as AgentEvent['eventType'],
                  ...JSON.parse(currentData),
                }

                // 事件序号检测 + 去重
                if (event.sequenceNumber) {
                  if (processedSequences.has(event.sequenceNumber)) {
                    continue // 重复事件，跳过
                  }
                  if (event.sequenceNumber > lastSequenceNumber + 1) {
                    console.warn(
                      `SSE event gap: expected ${lastSequenceNumber + 1}, got ${event.sequenceNumber}`
                    )
                  }
                  processedSequences.add(event.sequenceNumber)
                  lastSequenceNumber = event.sequenceNumber
                }

                yield event
              } catch (e) {
                console.error('Failed to parse agent event:', e)
                // 解析错误不中断，继续消费后续事件
              }

              currentEvent = ''
              currentData = ''
            }
          }
        }
      } catch (e) {
        // 读取异常，尝试重连
        if (!isDone && reconnectCount < MAX_RECONNECT) {
          reconnectCount++
          if (options.onReconnect) options.onReconnect(reconnectCount)
          const delay = Math.min(1000 * Math.pow(2, reconnectCount - 1), 30000)
          await new Promise((r) => setTimeout(r, delay))
          continue
        }
        throw e
      } finally {
        reader?.releaseLock()
      }
    }
  } finally {
    if (heartbeatTimer) window.clearTimeout(heartbeatTimer)
  }
}

/**
 * 确认敏感操作
 */
export async function confirmOperation(request: ConfirmationRequest): Promise<void> {
  const token = localStorage.getItem('access_token')

  const response = await fetch(`${API_BASE}/confirm`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`)
  }
}

/**
 * 获取可用 Agent 列表
 */
export async function listAgents(): Promise<AgentMetadata[]> {
  const token = localStorage.getItem('access_token')

  const response = await fetch(`${API_BASE}/list`, {
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  })

  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`)
  }

  const result: ApiResponse<AgentMetadata[]> = await response.json()
  return result.data || []
}

/**
 * 取消 Agent 执行
 */
export async function cancelExecution(traceId: string): Promise<void> {
  const token = localStorage.getItem('access_token')

  const response = await fetch(`${API_BASE}/cancel/${traceId}`, {
    method: 'POST',
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  })

  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`)
  }
}
```
