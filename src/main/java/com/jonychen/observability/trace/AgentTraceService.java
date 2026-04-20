package com.jonychen.observability.trace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 追踪服务
 *
 * @author jonychen
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTraceService {

    private final AgentTraceRepository traceRepository;
    private final AgentTraceSpanRepository spanRepository;
    private final ObjectMapper objectMapper;

    // 当前活跃的 Trace（用于实时追踪）
    private final Map<String, AgentTrace> activeTraces = new ConcurrentHashMap<>();

    /**
     * 开始追踪
     *
     * @param sessionId 会话 ID
     * @param userId 用户 ID
     * @param agentType Agent 类型
     * @param goal 任务目标
     * @return 追踪记录
     */
    public AgentTrace startTrace(String sessionId, String userId, String agentType, String goal) {
        AgentTrace trace = AgentTrace.create(sessionId, userId, agentType, goal);
        trace.setTraceId(UUID.randomUUID().toString());
        trace.setStartTime(LocalDateTime.now());
        trace.setStatus("RUNNING");
        trace.setIterations(0);

        // 先持久化到数据库
        traceRepository.save(trace);

        // 加入活跃追踪
        activeTraces.put(trace.getTraceId(), trace);

        log.info("Started agent trace: {} for agent: {}", trace.getTraceId(), agentType);
        return trace;
    }

    /**
     * 添加 Span
     *
     * @param traceId Trace ID
     * @param span Span 对象
     */
    @Transactional
    public void addSpan(String traceId, AgentTraceSpan span) {
        AgentTrace trace = activeTraces.get(traceId);
        if (trace == null) {
            trace = traceRepository.findByTraceId(traceId).orElse(null);
            if (trace == null) {
                log.warn("Trace not found: {}", traceId);
                return;
            }
        }

        // 确保 Span 关联到 Trace
        span.setTraceId(traceId);

        // 持久化 Span
        spanRepository.save(span);

        // 更新 Trace 的迭代次数
        trace.setIterations(trace.getIterations() + 1);

        // 添加到 Trace 的 spans 列表（用于 JSON 存储）
        Map<String, Object> spanMap = convertSpanToMap(span);
        trace.getSpans().add(spanMap);

        // 持久化 Trace 更新
        traceRepository.save(trace);

        log.debug("Added span {} to trace {}", span.getType(), traceId);
    }

    /**
     * 记录思考过程
     *
     * @param traceId Trace ID
     * @param thought 思考内容
     * @return Span 对象
     */
    public AgentTraceSpan recordThought(String traceId, String thought) {
        AgentTraceSpan span = AgentTraceSpan.thought(traceId, thought);
        addSpan(traceId, span);
        return span;
    }

    /**
     * 记录工具调用
     *
     * @param traceId Trace ID
     * @param toolName 工具名称
     * @param params 参数
     * @param result 结果
     * @param durationMs 执行时间
     * @param success 是否成功
     * @param error 错误信息
     * @return Span 对象
     */
    public AgentTraceSpan recordToolCall(String traceId, String toolName,
                                          Map<String, Object> params,
                                          String result,
                                          long durationMs,
                                          boolean success,
                                          String error) {
        String paramsJson = toJson(params);
        AgentTraceSpan span = AgentTraceSpan.toolCall(traceId, toolName, paramsJson, result, durationMs, success, error);
        addSpan(traceId, span);
        return span;
    }

    /**
     * 记录 LLM 调用
     *
     * @param traceId Trace ID
     * @param prompt 提示词
     * @param response 响应
     * @param promptTokens Prompt Token 数
     * @param completionTokens Completion Token 数
     * @param durationMs 执行时间
     * @return Span 对象
     */
    public AgentTraceSpan recordLLMCall(String traceId, String prompt, String response,
                                         long promptTokens, long completionTokens, long durationMs) {
        AgentTraceSpan span = AgentTraceSpan.llmCall(traceId, prompt, response, promptTokens, completionTokens, durationMs);
        addSpan(traceId, span);
        return span;
    }

    /**
     * 结束追踪（成功）
     *
     * @param traceId Trace ID
     * @param finalOutput 最终输出
     * @param promptTokens Prompt Token 数
     * @param completionTokens Completion Token 数
     */
    @Transactional
    public void endTraceSuccess(String traceId, String finalOutput, long promptTokens, long completionTokens) {
        AgentTrace trace = activeTraces.remove(traceId);
        if (trace == null) {
            trace = traceRepository.findByTraceId(traceId).orElse(null);
            if (trace == null) {
                log.warn("Trace not found: {}", traceId);
                return;
            }
        }

        trace.setStatus("COMPLETED");
        trace.setEndTime(LocalDateTime.now());
        trace.setExecutionTimeMs(calculateDuration(trace.getStartTime(), trace.getEndTime()));
        trace.setFinalOutput(finalOutput);
        trace.setTokenUsage(AgentTrace.TokenUsage.of(promptTokens, completionTokens));

        traceRepository.save(trace);
        log.info("Agent trace completed: {}, iterations: {}, time: {}ms",
                traceId, trace.getIterations(), trace.getExecutionTimeMs());
    }

    /**
     * 结束追踪（失败）
     *
     * @param traceId Trace ID
     * @param errorMessage 错误信息
     */
    @Transactional
    public void endTraceFailed(String traceId, String errorMessage) {
        AgentTrace trace = activeTraces.remove(traceId);
        if (trace == null) {
            trace = traceRepository.findByTraceId(traceId).orElse(null);
            if (trace == null) {
                log.warn("Trace not found: {}", traceId);
                return;
            }
        }

        trace.setStatus("FAILED");
        trace.setEndTime(LocalDateTime.now());
        trace.setExecutionTimeMs(calculateDuration(trace.getStartTime(), trace.getEndTime()));
        trace.setErrorMessage(errorMessage);

        traceRepository.save(trace);
        log.error("Agent trace failed: {}, error: {}", traceId, errorMessage);
    }

    /**
     * 取消追踪
     *
     * @param traceId Trace ID
     * @param reason 取消原因
     */
    @Transactional
    public void endTraceCancelled(String traceId, String reason) {
        AgentTrace trace = activeTraces.remove(traceId);
        if (trace == null) {
            trace = traceRepository.findByTraceId(traceId).orElse(null);
            if (trace == null) {
                log.warn("Trace not found: {}", traceId);
                return;
            }
        }

        trace.setStatus("CANCELLED");
        trace.setEndTime(LocalDateTime.now());
        trace.setExecutionTimeMs(calculateDuration(trace.getStartTime(), trace.getEndTime()));
        trace.setErrorMessage(reason);

        traceRepository.save(trace);
        log.info("Agent trace cancelled: {}, reason: {}", traceId, reason);
    }

    /**
     * 获取活跃追踪列表
     *
     * @param userId 用户 ID（可选）
     * @return 活跃追踪列表
     */
    public List<AgentTrace> getActiveTraces(String userId) {
        return activeTraces.values().stream()
                .filter(t -> userId == null || userId.equals(t.getUserId()))
                .sorted(Comparator.comparing(AgentTrace::getStartTime).reversed())
                .toList();
    }

    /**
     * 获取所有活跃追踪数量
     */
    public int getActiveTracesCount() {
        return activeTraces.size();
    }

    /**
     * 查询历史追踪
     *
     * @param userId 用户 ID
     * @param status 状态
     * @param agentType Agent 类型
     * @param limit 限制数量
     * @return 追踪列表
     */
    public List<AgentTrace> queryTraces(String userId, String status, String agentType, int limit) {
        return traceRepository.findByConditionsLimit(userId, status, agentType, limit);
    }

    /**
     * 获取追踪详情
     *
     * @param traceId Trace ID
     * @return 追踪对象
     */
    public AgentTrace getTrace(String traceId) {
        AgentTrace trace = activeTraces.get(traceId);
        if (trace != null) {
            return trace;
        }
        return traceRepository.findByTraceId(traceId)
                .orElseThrow(() -> new IllegalArgumentException("Trace not found: " + traceId));
    }

    /**
     * 获取追踪的完整 Span 列表
     *
     * @param traceId Trace ID
     * @return Span 列表
     */
    public List<AgentTraceSpan> getTraceSpans(String traceId) {
        return spanRepository.findByTraceIdOrderByStartTimeAsc(traceId);
    }

    /**
     * 获取用户最近的追踪
     *
     * @param userId 用户 ID
     * @param limit 限制数量
     * @return 追踪列表
     */
    public List<AgentTrace> getUserRecentTraces(String userId, int limit) {
        List<AgentTrace> traces = traceRepository.findByUserIdOrderByStartTimeDesc(userId);
        return traces.stream().limit(limit).toList();
    }

    /**
     * 获取统计信息
     *
     * @return 统计信息
     */
    public TraceStatistics getStatistics() {
        long completed = traceRepository.countCompleted();
        long failed = traceRepository.countFailed();
        long active = activeTraces.size();
        long total = completed + failed + active;

        return new TraceStatistics(total, completed, failed, active,
                total > 0 ? (double) completed / total : 0.0);
    }

    // ==================== 私有方法 ====================

    private long calculateDuration(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return 0;
        }
        return Duration.between(start, end).toMillis();
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return String.valueOf(obj);
        }
    }

    private Map<String, Object> convertSpanToMap(AgentTraceSpan span) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("spanId", span.getSpanId());
        map.put("type", span.getType().name());
        map.put("name", span.getName());
        map.put("input", span.getInput());
        map.put("output", span.getOutput());
        map.put("startTime", span.getStartTime() != null ? span.getStartTime().toString() : null);
        map.put("endTime", span.getEndTime() != null ? span.getEndTime().toString() : null);
        map.put("durationMs", span.getDurationMs());
        map.put("success", span.getSuccess());
        map.put("error", span.getError());
        map.put("promptTokens", span.getPromptTokens());
        map.put("completionTokens", span.getCompletionTokens());
        return map;
    }

    /**
     * 追踪统计信息
     */
    public record TraceStatistics(long total, long completed, long failed, long active, double successRate) {}
}