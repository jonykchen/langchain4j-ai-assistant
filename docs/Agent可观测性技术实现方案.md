# Agent 可观测性与评测框架技术实现方案

## 1. 概述

本文档定义了为 LangChain4j-Demo 项目构建 Agent 可观测性系统的技术方案，包括执行追踪、Prompt 管理、评测框架、状态持久化和多 Agent 协作等核心能力。

### 1.1 当前状态

| 组件 | 当前状态 | 位置 |
|------|---------|------|
| ReAct Agent | 已实现 | `planning/agent/ReActAgent.java` |
| Plan-Execute Agent | 已实现 | `planning/agent/PlanExecuteAgent.java` |
| 工具执行审计 | 已实现 | `tool/audit/ToolExecutionAudit.java` |
| 工具确认机制 | 已实现 | `tool/confirmation/` |
| Prompt 模板管理器 | 接口已定义 | `prompt/PromptTemplateManager.java` |
| Agent 执行追踪 | 缺失 | - |
| Prompt 版本管理 | 缺失 | - |
| Agent 评测框架 | 缺失 | - |
| 状态持久化 | 缺失 | - |
| 多 Agent 协作 | 缺失 | - |

### 1.2 目标架构

```
┌─────────────────────────────────────────────────────────────┐
│                   Agent 可观测性系统                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐  │
│  │  执行追踪系统   │  │ Prompt 管理   │  │  评测框架     │  │
│  │  - Trace ID   │  │  - 版本控制    │  │  - 自动评测   │  │
│  │  - Span 记录  │  │  - A/B 测试   │  │  - 指标收集   │  │
│  │  - 调用链可视化│  │  - 回滚机制   │  │  - 报告生成   │  │
│  └───────────────┘  └───────────────┘  └───────────────┘  │
│                                                             │
│  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐  │
│  │  状态持久化   │  │  Token 监控   │  │ 多 Agent 协作 │  │
│  │  - 断点续传   │  │  - 消耗统计   │  │  - 任务分发   │  │
│  │  - 恢复机制   │  │  - 成本追踪   │  │  - 结果聚合   │  │
│  │  - 状态查询   │  │  - 预算控制   │  │  - 通信机制   │  │
│  └───────────────┘  └───────────────┘  └───────────────┘  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Agent 执行追踪系统

### 2.1 核心数据模型

```java
// src/main/java/com/jonychen/observability/trace/AgentTrace.java
package com.jonychen.observability.trace;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent 执行追踪记录
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "agent_traces", indexes = {
    @Index(name = "idx_trace_session", columnList = "sessionId"),
    @Index(name = "idx_trace_status", columnList = "status"),
    @Index(name = "idx_trace_start_time", columnList = "startTime")
})
public class AgentTrace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 追踪 ID（全局唯一）
     */
    @Column(unique = true, nullable = false)
    private String traceId;

    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * 用户 ID
     */
    private String userId;

    /**
     * Agent 类型：REACT, PLAN_EXECUTE, MULTI_AGENT
     */
    @Column(nullable = false, length = 50)
    private String agentType;

    /**
     * 任务目标/问题
     */
    @Column(columnDefinition = "TEXT")
    private String goal;

    /**
     * 执行状态：RUNNING, COMPLETED, FAILED, CANCELLED
     */
    @Column(nullable = false, length = 20)
    private String status;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 总执行时间（毫秒）
     */
    private Long executionTimeMs;

    /**
     * 迭代次数
     */
    private Integer iterations;

    /**
     * 最终输出
     */
    @Column(columnDefinition = "TEXT")
    private String finalOutput;

    /**
     * 错误信息
     */
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Token 使用统计
     */
    @Embedded
    private TokenUsage tokenUsage;

    /**
     * 执行步骤详情（JSON）
     */
    @Column(columnDefinition = "JSONB")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private List<AgentSpan> spans = new ArrayList<>();

    /**
     * 元数据（标签、配置等）
     */
    @Column(columnDefinition = "JSONB")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private java.util.Map<String, Object> metadata;

    @PrePersist
    void prePersist() {
        if (startTime == null) {
            startTime = LocalDateTime.now();
        }
        if (traceId == null) {
            traceId = java.util.UUID.randomUUID().toString();
        }
    }

    /**
     * Token 使用统计嵌入对象
     */
    @Data
    @Embeddable
    public static class TokenUsage {
        private Long promptTokens;
        private Long completionTokens;
        private Long totalTokens;

        public static TokenUsage of(long prompt, long completion) {
            TokenUsage usage = new TokenUsage();
            usage.setPromptTokens(prompt);
            usage.setCompletionTokens(completion);
            usage.setTotalTokens(prompt + completion);
            return usage;
        }
    }
}
```

### 2.2 Span 数据模型

```java
// src/main/java/com/jonychen/observability/trace/AgentSpan.java
package com.jonychen.observability.trace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Agent 执行步骤 Span
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentSpan implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Span ID
     */
    private String spanId;

    /**
     * 父 Span ID
     */
    private String parentSpanId;

    /**
     * Span 类型
     */
    private SpanType type;

    /**
     * Span 名称
     */
    private String name;

    /**
     * 输入内容
     */
    private String input;

    /**
     * 输出内容
     */
    private String output;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 执行时间（毫秒）
     */
    private Long durationMs;

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 错误信息
     */
    private String error;

    /**
     * Token 消耗
     */
    private TokenInfo tokenInfo;

    /**
     * 扩展属性
     */
    private Map<String, Object> attributes;

    /**
     * Span 类型枚举
     */
    public enum SpanType {
        THOUGHT,        // 思考过程
        ACTION,         // 工具调用
        OBSERVATION,    // 观察结果
        LLM_CALL,       // LLM 调用
        TOOL_EXECUTE,   // 工具执行
        PLANNING,       // 规划步骤
        AGENT_CALL,     // 子 Agent 调用
        STATE_UPDATE    // 状态更新
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenInfo implements Serializable {
        private Long promptTokens;
        private Long completionTokens;
    }
}
```

### 2.3 追踪服务

```java
// src/main/java/com/jonychen/observability/trace/AgentTraceService.java
package com.jonychen.observability.trace;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 追踪服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTraceService {

    private final AgentTraceRepository traceRepository;
    private final AgentTraceSpanRepository spanRepository;

    // 当前活跃的 Trace（用于实时追踪）
    private final Map<String, AgentTrace> activeTraces = new ConcurrentHashMap<>();

    /**
     * 开始追踪
     */
    public AgentTrace startTrace(String sessionId, String userId, String agentType, String goal) {
        AgentTrace trace = new AgentTrace();
        trace.setTraceId(UUID.randomUUID().toString());
        trace.setSessionId(sessionId);
        trace.setUserId(userId);
        trace.setAgentType(agentType);
        trace.setGoal(goal);
        trace.setStatus("RUNNING");
        trace.setStartTime(LocalDateTime.now());
        trace.setIterations(0);

        activeTraces.put(trace.getTraceId(), trace);
        log.info("Started agent trace: {}", trace.getTraceId());
        return trace;
    }

    /**
     * 添加 Span
     */
    public void addSpan(String traceId, AgentSpan span) {
        AgentTrace trace = activeTraces.get(traceId);
        if (trace == null) {
            log.warn("Trace not found: {}", traceId);
            return;
        }

        trace.getSpans().add(span);
        trace.setIterations(trace.getIterations() + 1);

        // 持久化 Span
        AgentTraceSpan spanEntity = toEntity(traceId, span);
        spanRepository.save(spanEntity);
    }

    /**
     * 记录思考过程
     */
    public AgentSpan recordThought(String traceId, String thought) {
        AgentSpan span = AgentSpan.builder()
            .spanId(UUID.randomUUID().toString())
            .type(AgentSpan.SpanType.THOUGHT)
            .name("Thought")
            .input(thought)
            .startTime(LocalDateTime.now())
            .success(true)
            .build();

        addSpan(traceId, span);
        return span;
    }

    /**
     * 记录工具调用
     */
    public AgentSpan recordToolCall(String traceId, String toolName,
                                     Map<String, Object> params,
                                     String result,
                                     long durationMs,
                                     boolean success,
                                     String error) {
        AgentSpan span = AgentSpan.builder()
            .spanId(UUID.randomUUID().toString())
            .type(AgentSpan.SpanType.TOOL_EXECUTE)
            .name("Tool: " + toolName)
            .input(toJson(params))
            .output(result)
            .durationMs(durationMs)
            .success(success)
            .error(error)
            .startTime(LocalDateTime.now().minusNanos(durationMs * 1_000_000))
            .endTime(LocalDateTime.now())
            .build();

        addSpan(traceId, span);
        return span;
    }

    /**
     * 记录 LLM 调用
     */
    public AgentSpan recordLLMCall(String traceId, String prompt, String response,
                                    AgentTrace.TokenUsage tokenUsage, long durationMs) {
        AgentSpan span = AgentSpan.builder()
            .spanId(UUID.randomUUID().toString())
            .type(AgentSpan.SpanType.LLM_CALL)
            .name("LLM Call")
            .input(prompt)
            .output(response)
            .durationMs(durationMs)
            .success(true)
            .tokenInfo(AgentSpan.TokenInfo.builder()
                .promptTokens(tokenUsage.getPromptTokens())
                .completionTokens(tokenUsage.getCompletionTokens())
                .build())
            .build();

        addSpan(traceId, span);
        return span;
    }

    /**
     * 结束追踪（成功）
     */
    @Transactional
    public void endTraceSuccess(String traceId, String finalOutput, AgentTrace.TokenUsage totalTokens) {
        AgentTrace trace = activeTraces.remove(traceId);
        if (trace == null) {
            log.warn("Trace not found: {}", traceId);
            return;
        }

        trace.setStatus("COMPLETED");
        trace.setEndTime(LocalDateTime.now());
        trace.setExecutionTimeMs(calculateDuration(trace.getStartTime(), trace.getEndTime()));
        trace.setFinalOutput(finalOutput);
        trace.setTokenUsage(totalTokens);

        traceRepository.save(trace);
        log.info("Agent trace completed: {}, iterations: {}, time: {}ms",
            traceId, trace.getIterations(), trace.getExecutionTimeMs());
    }

    /**
     * 结束追踪（失败）
     */
    @Transactional
    public void endTraceFailed(String traceId, String errorMessage) {
        AgentTrace trace = activeTraces.remove(traceId);
        if (trace == null) {
            log.warn("Trace not found: {}", traceId);
            return;
        }

        trace.setStatus("FAILED");
        trace.setEndTime(LocalDateTime.now());
        trace.setExecutionTimeMs(calculateDuration(trace.getStartTime(), trace.getEndTime()));
        trace.setErrorMessage(errorMessage);

        traceRepository.save(trace);
        log.error("Agent trace failed: {}, error: {}", traceId, errorMessage);
    }

    /**
     * 获取活跃追踪
     */
    public List<AgentTrace> getActiveTraces(String userId) {
        return activeTraces.values().stream()
            .filter(t -> userId == null || userId.equals(t.getUserId()))
            .toList();
    }

    /**
     * 查询历史追踪
     */
    public List<AgentTrace> queryTraces(AgentTraceQuery query) {
        return traceRepository.findByConditions(query);
    }

    /**
     * 获取追踪详情
     */
    public AgentTrace getTrace(String traceId) {
        return traceRepository.findByTraceId(traceId)
            .orElseThrow(() -> new IllegalArgumentException("Trace not found: " + traceId));
    }

    /**
     * 获取追踪的完整 Span 列表
     */
    public List<AgentTraceSpan> getTraceSpans(String traceId) {
        return spanRepository.findByTraceIdOrderByStartTimeAsc(traceId);
    }

    // ==================== 私有方法 ====================

    private long calculateDuration(LocalDateTime start, LocalDateTime end) {
        return java.time.Duration.between(start, end).toMillis();
    }

    private String toJson(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }

    private AgentTraceSpan toEntity(String traceId, AgentSpan span) {
        AgentTraceSpan entity = new AgentTraceSpan();
        entity.setTraceId(traceId);
        entity.setSpanId(span.getSpanId());
        entity.setParentSpanId(span.getParentSpanId());
        entity.setType(span.getType().name());
        entity.setName(span.getName());
        entity.setInput(span.getInput());
        entity.setOutput(span.getOutput());
        entity.setStartTime(span.getStartTime());
        entity.setEndTime(span.getEndTime());
        entity.setDurationMs(span.getDurationMs());
        entity.setSuccess(span.isSuccess());
        entity.setError(span.getError());
        entity.setAttributes(span.getAttributes());
        return entity;
    }
}
```

### 2.4 追踪切面（自动追踪）

```java
// src/main/java/com/jonychen/observability/trace/AgentTraceAspect.java
package com.jonychen.observability.trace;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Agent 执行追踪切面
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AgentTraceAspect {

    private final AgentTraceService traceService;
    private final TraceContext traceContext;

    /**
     * 追踪 ReAct Agent 执行
     */
    @Around("execution(* com.jonychen.planning.agent.ReActAgent.execute(..))")
    public Object traceReActExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        String question = (String) args[0];
        com.jonychen.planning.TaskContext context = (com.jonychen.planning.TaskContext) args[1];

        // 开始追踪
        AgentTrace trace = traceService.startTrace(
            context != null ? context.getSessionId() : null,
            getCurrentUserId(),
            "REACT",
            question
        );

        traceContext.setCurrentTraceId(trace.getTraceId());

        try {
            // 执行原方法
            Object result = joinPoint.proceed();

            // 记录成功
            if (result instanceof com.jonychen.planning.agent.ReActResult reactResult) {
                traceService.endTraceSuccess(
                    trace.getTraceId(),
                    reactResult.finalAnswer(),
                    extractTokenUsage(reactResult)
                );
            }

            return result;
        } catch (Throwable e) {
            traceService.endTraceFailed(trace.getTraceId(), e.getMessage());
            throw e;
        } finally {
            traceContext.clear();
        }
    }

    /**
     * 追踪工具执行
     */
    @Around("execution(* com.jonychen.tool.ToolRegistry.execute(..))")
    public Object traceToolExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        String traceId = traceContext.getCurrentTraceId();
        if (traceId == null) {
            return joinPoint.proceed();
        }

        Object[] args = joinPoint.getArgs();
        String toolName = (String) args[0];
        java.util.Map<String, Object> params = (java.util.Map<String, Object>) args[1];

        long startTime = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;

            traceService.recordToolCall(traceId, toolName, params,
                String.valueOf(result), duration, true, null);

            return result;
        } catch (Throwable e) {
            long duration = System.currentTimeMillis() - startTime;
            traceService.recordToolCall(traceId, toolName, params,
                null, duration, false, e.getMessage());
            throw e;
        }
    }

    private String getCurrentUserId() {
        // 从 SecurityContext 获取当前用户 ID
        return "system";
    }

    private AgentTrace.TokenUsage extractTokenUsage(Object result) {
        // 从结果中提取 Token 使用信息
        return AgentTrace.TokenUsage.of(0, 0);
    }
}
```

### 2.5 追踪上下文

```java
// src/main/java/com/jonychen/observability/trace/TraceContext.java
package com.jonychen.observability.trace;

import org.springframework.stereotype.Component;

/**
 * 追踪上下文（ThreadLocal 存储）
 */
@Component
public class TraceContext {

    private static final ThreadLocal<String> CURRENT_TRACE = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_SPAN = new ThreadLocal<>();

    public String getCurrentTraceId() {
        return CURRENT_TRACE.get();
    }

    public void setCurrentTraceId(String traceId) {
        CURRENT_TRACE.set(traceId);
    }

    public String getCurrentSpanId() {
        return CURRENT_SPAN.get();
    }

    public void setCurrentSpanId(String spanId) {
        CURRENT_SPAN.set(spanId);
    }

    public void clear() {
        CURRENT_TRACE.remove();
        CURRENT_SPAN.remove();
    }
}
```

---

## 3. Prompt 版本管理系统

### 3.1 数据模型

```java
// src/main/java/com/jonychen/observability/prompt/PromptTemplateEntity.java
package com.jonychen.observability.prompt;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Prompt 模板实体
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "prompt_templates", indexes = {
    @Index(name = "idx_prompt_name", columnList = "name"),
    @Index(name = "idx_prompt_name_version", columnList = "name,version", unique = true)
})
public class PromptTemplateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 模板名称
     */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * 版本号（语义化版本）
     */
    @Column(nullable = false, length = 20)
    private String version;

    /**
     * 描述
     */
    @Column(length = 500)
    private String description;

    /**
     * 模板内容
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * 变量定义（JSON）
     */
    @Column(columnDefinition = "JSONB")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private List<PromptVariable> variables;

    /**
     * 标签
     */
    @Column(length = 500)
    private String tags;

    /**
     * 是否为当前激活版本
     */
    private boolean active;

    /**
     * 是否为生产环境版本
     */
    private boolean production;

    /**
     * 创建者
     */
    private String createdBy;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * A/B 测试配置
     */
    @Embedded
    private ABTestConfig abTestConfig;

    /**
     * 使用统计
     */
    @Embedded
    private UsageStats usageStats;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Data
    @Embeddable
    public static class PromptVariable {
        private String name;
        private String description;
        private String type;
        private boolean required;
        private String defaultValue;
    }

    @Data
    @Embeddable
    public static class ABTestConfig {
        private boolean enabled;
        private String variantName;
        private Double trafficPercentage;
        private String baselineVersion;
    }

    @Data
    @Embeddable
    public static class UsageStats {
        private Long totalUses;
        private Long successCount;
        private Long failureCount;
        private Double avgResponseTime;
        private Double avgTokenUsage;
    }
}
```

### 3.2 Prompt 版本管理服务

```java
// src/main/java/com/jonychen/observability/prompt/PromptVersionService.java
package com.jonychen.observability.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prompt 版本管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptVersionService {

    private final PromptTemplateRepository templateRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

    /**
     * 创建新模板
     */
    @Transactional
    public PromptTemplateEntity createTemplate(CreatePromptRequest request) {
        // 解析模板变量
        List<PromptTemplateEntity.PromptVariable> variables = parseVariables(request.content());

        PromptTemplateEntity template = new PromptTemplateEntity();
        template.setName(request.name());
        template.setVersion(request.version() != null ? request.version() : "1.0.0");
        template.setDescription(request.description());
        template.setContent(request.content());
        template.setVariables(variables);
        template.setTags(request.tags());
        template.setActive(true);
        template.setCreatedBy(request.createdBy());

        templateRepository.save(template);
        log.info("Created prompt template: {}@{}", template.getName(), template.getVersion());
        return template;
    }

    /**
     * 创建新版本
     */
    @Transactional
    public PromptTemplateEntity createVersion(String name, String newContent, String changeDescription) {
        PromptTemplateEntity latest = templateRepository.findLatestByName(name)
            .orElseThrow(() -> new IllegalArgumentException("Template not found: " + name));

        // 递增版本号
        String newVersion = incrementVersion(latest.getVersion());

        PromptTemplateEntity newTemplate = new PromptTemplateEntity();
        newTemplate.setName(name);
        newTemplate.setVersion(newVersion);
        newTemplate.setDescription(changeDescription);
        newTemplate.setContent(newContent);
        newTemplate.setVariables(parseVariables(newContent));
        newTemplate.setTags(latest.getTags());
        newTemplate.setCreatedBy(latest.getCreatedBy());
        newTemplate.setActive(false); // 新版本默认不激活

        templateRepository.save(newTemplate);
        log.info("Created new version: {}@{}", name, newVersion);
        return newTemplate;
    }

    /**
     * 激活版本
     */
    @Transactional
    public void activateVersion(String name, String version) {
        // 停用当前所有版本
        templateRepository.deactivateAllVersions(name);

        // 激活指定版本
        PromptTemplateEntity template = templateRepository.findByNameAndVersion(name, version)
            .orElseThrow(() -> new IllegalArgumentException("Version not found: " + name + "@" + version));
        template.setActive(true);

        templateRepository.save(template);
        log.info("Activated version: {}@{}", name, version);
    }

    /**
     * 推送到生产环境
     */
    @Transactional
    public void promoteToProduction(String name, String version) {
        // 移除当前生产版本标记
        templateRepository.removeProductionFlag(name);

        PromptTemplateEntity template = templateRepository.findByNameAndVersion(name, version)
            .orElseThrow(() -> new IllegalArgumentException("Version not found"));

        template.setProduction(true);
        templateRepository.save(template);

        log.info("Promoted to production: {}@{}", name, version);
    }

    /**
     * 回滚到指定版本
     */
    @Transactional
    public void rollback(String name, String targetVersion) {
        activateVersion(name, targetVersion);
        log.warn("Rolled back template {} to version {}", name, targetVersion);
    }

    /**
     * 获取激活版本
     */
    public Optional<PromptTemplateEntity> getActiveVersion(String name) {
        return templateRepository.findActiveByName(name);
    }

    /**
     * 获取生产版本
     */
    public Optional<PromptTemplateEntity> getProductionVersion(String name) {
        return templateRepository.findProductionByName(name);
    }

    /**
     * 渲染模板
     */
    public String render(String name, Map<String, Object> variables) {
        PromptTemplateEntity template = getActiveVersion(name)
            .orElseThrow(() -> new IllegalArgumentException("Template not found: " + name));

        return renderTemplate(template.getContent(), variables);
    }

    /**
     * 配置 A/B 测试
     */
    @Transactional
    public void configureABTest(String name, ABTestConfigRequest config) {
        PromptTemplateEntity baseline = templateRepository.findByNameAndVersion(name, config.baselineVersion())
            .orElseThrow(() -> new IllegalArgumentException("Baseline version not found"));

        PromptTemplateEntity variant = templateRepository.findByNameAndVersion(name, config.variantVersion())
            .orElseThrow(() -> new IllegalArgumentException("Variant version not found"));

        // 配置基线
        baseline.setAbTestConfig(new PromptTemplateEntity.ABTestConfig());
        baseline.getAbTestConfig().setEnabled(true);
        baseline.getAbTestConfig().setTrafficPercentage(100 - config.trafficPercentage());
        baseline.getAbTestConfig().setBaselineVersion(null);
        baseline.getAbTestConfig().setVariantName("baseline");

        // 配置变体
        variant.setAbTestConfig(new PromptTemplateEntity.ABTestConfig());
        variant.getAbTestConfig().setEnabled(true);
        variant.getAbTestConfig().setTrafficPercentage(config.trafficPercentage());
        variant.getAbTestConfig().setBaselineVersion(config.baselineVersion());
        variant.getAbTestConfig().setVariantName(config.variantName());

        templateRepository.saveAll(List.of(baseline, variant));
        log.info("Configured A/B test for {}: baseline={}, variant={}, split={}/{}",
            name, config.baselineVersion(), config.variantVersion(),
            100 - config.trafficPercentage(), config.trafficPercentage());
    }

    /**
     * 获取 A/B 测试版本
     */
    public PromptTemplateEntity getABTestVersion(String name, String userId) {
        List<PromptTemplateEntity> abTestTemplates = templateRepository.findABTestTemplates(name);

        if (abTestTemplates.isEmpty()) {
            return getActiveVersion(name).orElse(null);
        }

        // 根据用户 ID 确定分配哪个版本
        int hash = Math.abs(userId.hashCode());
        double percentage = hash % 100;

        for (PromptTemplateEntity template : abTestTemplates) {
            if (template.getAbTestConfig() != null && template.getAbTestConfig().isEnabled()) {
                if (percentage < template.getAbTestConfig().getTrafficPercentage()) {
                    return template;
                }
                percentage -= template.getAbTestConfig().getTrafficPercentage();
            }
        }

        return abTestTemplates.get(0);
    }

    /**
     * 获取版本历史
     */
    public List<PromptTemplateEntity> getVersionHistory(String name) {
        return templateRepository.findByNameOrderByVersionDesc(name);
    }

    /**
     * 比较两个版本
     */
    public PromptDiff compareVersions(String name, String version1, String version2) {
        PromptTemplateEntity t1 = templateRepository.findByNameAndVersion(name, version1)
            .orElseThrow(() -> new IllegalArgumentException("Version not found: " + version1));
        PromptTemplateEntity t2 = templateRepository.findByNameAndVersion(name, version2)
            .orElseThrow(() -> new IllegalArgumentException("Version not found: " + version2));

        return new PromptDiff(
            name,
            version1,
            version2,
            t1.getContent(),
            t2.getContent(),
            computeDiff(t1.getContent(), t2.getContent())
        );
    }

    // ==================== 私有方法 ====================

    private List<PromptTemplateEntity.PromptVariable> parseVariables(String content) {
        Set<String> varNames = new HashSet<>();
        Matcher matcher = VARIABLE_PATTERN.matcher(content);
        while (matcher.find()) {
            varNames.add(matcher.group(1));
        }

        return varNames.stream()
            .map(name -> {
                PromptTemplateEntity.PromptVariable var = new PromptTemplateEntity.PromptVariable();
                var.setName(name);
                var.setRequired(true);
                return var;
            })
            .toList();
    }

    private String renderTemplate(String template, Map<String, Object> variables) {
        String result = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
        }
        return result;
    }

    private String incrementVersion(String version) {
        String[] parts = version.split("\\.");
        int minor = Integer.parseInt(parts[1]) + 1;
        return parts[0] + "." + minor + "." + parts[2];
    }

    private List<String> computeDiff(String content1, String content2) {
        // 简单的行级别差异比较
        String[] lines1 = content1.split("\n");
        String[] lines2 = content2.split("\n");
        List<String> diff = new ArrayList<>();

        int max = Math.max(lines1.length, lines2.length);
        for (int i = 0; i < max; i++) {
            String l1 = i < lines1.length ? lines1[i] : "";
            String l2 = i < lines2.length ? lines2[i] : "";
            if (!l1.equals(l2)) {
                diff.add("Line " + (i + 1) + ":");
                if (!l1.isEmpty()) diff.add("- " + l1);
                if (!l2.isEmpty()) diff.add("+ " + l2);
            }
        }
        return diff;
    }

    // DTOs
    public record CreatePromptRequest(String name, String version, String description,
                                       String content, String tags, String createdBy) {}
    public record ABTestConfigRequest(String baselineVersion, String variantVersion,
                                       String variantName, double trafficPercentage) {}
    public record PromptDiff(String name, String version1, String version2,
                             String content1, String content2, List<String> changes) {}
}
```

---

## 4. Agent 评测框架

### 4.1 评测指标模型

```java
// src/main/java/com/jonychen/observability/evaluation/EvaluationMetrics.java
package com.jonychen.observability.evaluation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 评测指标
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationMetrics {

    /**
     * 任务完成率
     */
    private double taskCompletionRate;

    /**
     * 平均迭代次数
     */
    private double avgIterations;

    /**
     * 工具调用成功率
     */
    private double toolSuccessRate;

    /**
     * 平均响应时间（毫秒）
     */
    private double avgResponseTimeMs;

    /**
     * P95 响应时间
     */
    private double p95ResponseTimeMs;

    /**
     * 平均 Token 消耗
     */
    private double avgTokenUsage;

    /**
     * 输出质量得分（0-1）
     */
    private double qualityScore;

    /**
     * 思考过程质量
     */
    private double thoughtQualityScore;

    /**
     * 最终答案相关性
     */
    private double answerRelevanceScore;

    /**
     * 错误率
     */
    private double errorRate;

    /**
     * 端到端成功率
     */
    private double endToEndSuccessRate;

    /**
     * 计算综合得分
     */
    public double calculateOverallScore() {
        return (taskCompletionRate * 0.2 +
                toolSuccessRate * 0.15 +
                qualityScore * 0.25 +
                answerRelevanceScore * 0.2 +
                (1 - errorRate) * 0.1 +
                endToEndSuccessRate * 0.1);
    }
}
```

### 4.2 评测器接口

```java
// src/main/java/com/jonychen/observability/evaluation/AgentEvaluator.java
package com.jonychen.observability.evaluation;

import com.jonychen.observability.trace.AgentTrace;

import java.util.List;
import java.util.Map;

/**
 * Agent 评测器接口
 */
public interface AgentEvaluator {

    /**
     * 评测名称
     */
    String getName();

    /**
     * 评测描述
     */
    String getDescription();

    /**
     * 执行评测
     */
    EvaluationResult evaluate(EvaluationContext context);

    /**
     * 评测一批追踪记录
     */
    BatchEvaluationResult evaluateBatch(List<AgentTrace> traces);

    /**
     * 评测上下文
     */
    record EvaluationContext(
        AgentTrace trace,
        String expectedOutput,
        List<String> expectedTools,
        Map<String, Object> constraints,
        String evaluationMode
    ) {}

    /**
     * 评测结果
     */
    record EvaluationResult(
        String evaluatorId,
        boolean passed,
        double score,
        Map<String, Object> details,
        List<String> issues,
        String recommendation
    ) {}

    /**
     * 批量评测结果
     */
    record BatchEvaluationResult(
        String batchId,
        int totalCount,
        int passedCount,
        int failedCount,
        EvaluationMetrics aggregateMetrics,
        List<EvaluationResult> individualResults,
        Map<String, Object> summary
    ) {}
}
```

### 4.3 基础评测器实现

```java
// src/main/java/com/jonychen/observability/evaluation/impl/TaskCompletionEvaluator.java
package com.jonychen.observability.evaluation.impl;

import com.jonychen.observability.evaluation.AgentEvaluator;
import com.jonychen.observability.evaluation.EvaluationMetrics;
import com.jonychen.observability.trace.AgentTrace;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 任务完成度评测器
 */
@Slf4j
@Component
public class TaskCompletionEvaluator implements AgentEvaluator {

    @Override
    public String getName() {
        return "task-completion";
    }

    @Override
    public String getDescription() {
        return "评估 Agent 是否成功完成任务目标";
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext context) {
        AgentTrace trace = context.trace();

        // 基本完成度检查
        boolean statusSuccess = "COMPLETED".equals(trace.getStatus());

        // 输出存在性检查
        boolean hasOutput = trace.getFinalOutput() != null && !trace.getFinalOutput().isEmpty();

        // 预期输出匹配（如果提供）
        boolean outputMatches = true;
        if (context.expectedOutput() != null) {
            outputMatches = trace.getFinalOutput() != null &&
                trace.getFinalOutput().toLowerCase().contains(context.expectedOutput().toLowerCase());
        }

        // 计算得分
        double score = 0;
        if (statusSuccess) score += 0.4;
        if (hasOutput) score += 0.3;
        if (outputMatches) score += 0.3;

        // 收集问题
        List<String> issues = new ArrayList<>();
        if (!statusSuccess) issues.add("任务状态非 COMPLETED: " + trace.getStatus());
        if (!hasOutput) issues.add("缺少最终输出");
        if (!outputMatches && context.expectedOutput() != null) {
            issues.add("输出与预期不匹配");
        }

        return new EvaluationResult(
            getName(),
            score >= 0.7,
            score,
            Map.of(
                "statusSuccess", statusSuccess,
                "hasOutput", hasOutput,
                "outputMatches", outputMatches
            ),
            issues,
            generateRecommendation(issues)
        );
    }

    @Override
    public BatchEvaluationResult evaluateBatch(List<AgentTrace> traces) {
        List<EvaluationResult> results = new ArrayList<>();
        int passed = 0;
        int failed = 0;
        double totalScore = 0;

        for (AgentTrace trace : traces) {
            EvaluationResult result = evaluate(new EvaluationContext(trace, null, null, Map.of(), "batch"));
            results.add(result);
            totalScore += result.score();
            if (result.passed()) passed++;
            else failed++;
        }

        EvaluationMetrics metrics = EvaluationMetrics.builder()
            .taskCompletionRate((double) passed / traces.size())
            .errorRate((double) failed / traces.size())
            .build();

        return new BatchEvaluationResult(
            UUID.randomUUID().toString(),
            traces.size(),
            passed,
            failed,
            metrics,
            results,
            Map.of(
                "avgScore", totalScore / traces.size(),
                "passRate", (double) passed / traces.size()
            )
        );
    }

    private String generateRecommendation(List<String> issues) {
        if (issues.isEmpty()) return "任务执行良好";
        return "建议检查: " + String.join(", ", issues);
    }
}
```

### 4.4 工具调用评测器

```java
// src/main/java/com/jonychen/observability/evaluation/impl/ToolExecutionEvaluator.java
package com.jonychen.observability.evaluation.impl;

import com.jonychen.observability.evaluation.AgentEvaluator;
import com.jonychen.observability.trace.AgentTrace;
import com.jonychen.observability.trace.AgentSpan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 工具执行评测器
 */
@Slf4j
@Component
public class ToolExecutionEvaluator implements AgentEvaluator {

    @Override
    public String getName() {
        return "tool-execution";
    }

    @Override
    public String getDescription() {
        return "评估工具调用的正确性和效率";
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext context) {
        AgentTrace trace = context.trace();

        // 提取工具调用 Span
        List<AgentSpan> toolSpans = trace.getSpans().stream()
            .filter(s -> s.getType() == AgentSpan.SpanType.TOOL_EXECUTE)
            .toList();

        if (toolSpans.isEmpty()) {
            return new EvaluationResult(
                getName(),
                true,
                1.0,
                Map.of("toolCallCount", 0),
                List.of(),
                "无工具调用"
            );
        }

        // 统计成功/失败
        long successCount = toolSpans.stream().filter(AgentSpan::isSuccess).count();
        long failureCount = toolSpans.size() - successCount;
        double successRate = (double) successCount / toolSpans.size();

        // 检查是否调用了预期工具
        Set<String> calledTools = toolSpans.stream()
            .map(s -> s.getName().replace("Tool: ", ""))
            .collect(Collectors.toSet());

        List<String> expectedTools = context.expectedTools() != null ? context.expectedTools() : List.of();
        Set<String> missingTools = new HashSet<>(expectedTools);
        missingTools.removeAll(calledTools);

        // 计算平均执行时间
        double avgExecutionTime = toolSpans.stream()
            .filter(s -> s.getDurationMs() != null)
            .mapToLong(AgentSpan::getDurationMs)
            .average()
            .orElse(0);

        // 计算得分
        double score = successRate * 0.5 +
                       (missingTools.isEmpty() ? 0.3 : 0) +
                       (avgExecutionTime < 1000 ? 0.2 : 0.1);

        List<String> issues = new ArrayList<>();
        if (failureCount > 0) {
            issues.add(failureCount + " 次工具调用失败");
        }
        if (!missingTools.isEmpty()) {
            issues.add("缺少工具调用: " + missingTools);
        }

        return new EvaluationResult(
            getName(),
            successRate >= 0.8 && missingTools.isEmpty(),
            score,
            Map.of(
                "toolCallCount", toolSpans.size(),
                "successCount", successCount,
                "failureCount", failureCount,
                "successRate", successRate,
                "avgExecutionTimeMs", avgExecutionTime,
                "calledTools", calledTools
            ),
            issues,
            generateRecommendation(issues, failureCount)
        );
    }

    @Override
    public BatchEvaluationResult evaluateBatch(List<AgentTrace> traces) {
        List<EvaluationResult> results = new ArrayList<>();
        int passed = 0;
        int failed = 0;
        double totalSuccessRate = 0;
        long totalToolCalls = 0;

        for (AgentTrace trace : traces) {
            EvaluationResult result = evaluate(new EvaluationContext(trace, null, null, Map.of(), "batch"));
            results.add(result);

            if (result.passed()) passed++;
            else failed++;

            totalSuccessRate += (double) result.details().get("successRate");
            totalToolCalls += (long) result.details().get("toolCallCount");
        }

        EvaluationMetrics metrics = EvaluationMetrics.builder()
            .toolSuccessRate(totalSuccessRate / traces.size())
            .build();

        return new BatchEvaluationResult(
            UUID.randomUUID().toString(),
            traces.size(),
            passed,
            failed,
            metrics,
            results,
            Map.of(
                "totalToolCalls", totalToolCalls,
                "avgToolSuccessRate", totalSuccessRate / traces.size()
            )
        );
    }

    private String generateRecommendation(List<String> issues, long failureCount) {
        if (issues.isEmpty()) return "工具调用表现良好";
        if (failureCount > 0) return "建议检查工具实现和参数验证";
        return String.join("; ", issues);
    }
}
```

### 4.5 评测服务

```java
// src/main/java/com/jonychen/observability/evaluation/EvaluationService.java
package com.jonychen.observability.evaluation;

import com.jonychen.observability.trace.AgentTrace;
import com.jonychen.observability.trace.AgentTraceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 评测服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationService {

    private final AgentTraceService traceService;
    private final List<AgentEvaluator> evaluators;

    /**
     * 执行完整评测
     */
    public FullEvaluationResult evaluateFull(String traceId, EvaluationRequest request) {
        AgentTrace trace = traceService.getTrace(traceId);

        List<EvaluationResult> evaluatorResults = new ArrayList<>();
        for (AgentEvaluator evaluator : evaluators) {
            try {
                EvaluationResult result = evaluator.evaluate(
                    new AgentEvaluator.EvaluationContext(
                        trace,
                        request.expectedOutput(),
                        request.expectedTools(),
                        request.constraints(),
                        request.mode()
                    )
                );
                evaluatorResults.add(result);
            } catch (Exception e) {
                log.error("Evaluator {} failed", evaluator.getName(), e);
            }
        }

        // 计算综合指标
        EvaluationMetrics metrics = aggregateMetrics(trace, evaluatorResults);

        return new FullEvaluationResult(
            traceId,
            evaluatorResults,
            metrics,
            metrics.calculateOverallScore(),
            generateOverallRecommendation(evaluatorResults)
        );
    }

    /**
     * 批量评测
     */
    public List<FullEvaluationResult> evaluateBatch(List<String> traceIds) {
        return traceIds.stream()
            .map(id -> evaluateFull(id, new EvaluationRequest(null, null, Map.of(), "batch")))
            .toList();
    }

    /**
     * 生成评测报告
     */
    public EvaluationReport generateReport(List<String> traceIds) {
        List<FullEvaluationResult> results = evaluateBatch(traceIds);

        // 汇总统计
        double avgScore = results.stream()
            .mapToDouble(FullEvaluationResult::overallScore)
            .average()
            .orElse(0);

        int passCount = (int) results.stream()
            .filter(r -> r.overallScore() >= 0.7)
            .count();

        return new EvaluationReport(
            UUID.randomUUID().toString(),
            new Date(),
            traceIds.size(),
            results,
            EvaluationMetrics.builder()
                .taskCompletionRate((double) passCount / traceIds.size())
                .build(),
            Map.of(
                "avgOverallScore", avgScore,
                "passRate", (double) passCount / traceIds.size()
            )
        );
    }

    private EvaluationMetrics aggregateMetrics(AgentTrace trace, List<EvaluationResult> results) {
        EvaluationMetrics.EvaluationMetricsBuilder builder = EvaluationMetrics.builder();

        for (EvaluationResult result : results) {
            Map<String, Object> details = result.details();
            if (details.containsKey("successRate")) {
                builder.toolSuccessRate((double) details.get("successRate"));
            }
            if (details.containsKey("taskCompletionRate")) {
                builder.taskCompletionRate((double) details.get("taskCompletionRate"));
            }
        }

        builder.avgIterations(trace.getIterations());
        if (trace.getExecutionTimeMs() != null) {
            builder.avgResponseTimeMs(trace.getExecutionTimeMs());
        }

        return builder.build();
    }

    private String generateOverallRecommendation(List<EvaluationResult> results) {
        List<String> allIssues = results.stream()
            .flatMap(r -> r.issues().stream())
            .toList();

        if (allIssues.isEmpty()) {
            return "整体表现良好";
        }

        // 统计最常见的问题
        Map<String, Long> issueCounts = allIssues.stream()
            .collect(java.util.stream.Collectors.groupingBy(s -> s, Collectors.counting()));

        return "主要问题: " + issueCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(3)
            .map(Map.Entry::getKey)
            .reduce((a, b) -> a + ", " + b)
            .orElse("无明显问题");
    }

    // DTOs
    public record EvaluationRequest(String expectedOutput, List<String> expectedTools,
                                    Map<String, Object> constraints, String mode) {}
    public record FullEvaluationResult(String traceId, List<EvaluationResult> evaluatorResults,
                                        EvaluationMetrics metrics, double overallScore,
                                        String recommendation) {}
    public record EvaluationReport(String reportId, Date generatedAt, int totalTraces,
                                   List<FullEvaluationResult> results,
                                   EvaluationMetrics aggregateMetrics,
                                   Map<String, Object> summary) {}
}
```

---

## 5. Agent 状态持久化

### 5.1 状态快照模型

```java
// src/main/java/com/jonychen/observability/state/AgentStateSnapshot.java
package com.jonychen.observability.state;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Agent 状态快照
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "agent_state_snapshots")
public class AgentStateSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 快照 ID
     */
    @Column(unique = true, nullable = false)
    private String snapshotId;

    /**
     * 关联的 Trace ID
     */
    private String traceId;

    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * Agent 类型
     */
    private String agentType;

    /**
     * 当前步骤索引
     */
    private int currentStepIndex;

    /**
     * 总步骤数
     */
    private int totalSteps;

    /**
     * Agent 内部状态（JSON）
     */
    @Column(columnDefinition = "JSONB")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private Map<String, Object> internalState;

    /**
     * 执行历史（JSON）
     */
    @Column(columnDefinition = "JSONB")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private java.util.List<Map<String, Object>> executionHistory;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 快照类型：CHECKPOINT, ERROR, PAUSE, STEP_COMPLETE
     */
    @Column(length = 20)
    private String snapshotType;

    /**
     * 可恢复标志
     */
    private boolean resumable;

    /**
     * 过期时间
     */
    private LocalDateTime expiresAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (snapshotId == null) {
            snapshotId = java.util.UUID.randomUUID().toString();
        }
    }

    /**
     * 检查是否已过期
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
}
```

### 5.2 状态持久化服务

```java
// src/main/java/com/jonychen/observability/state/AgentStateService.java
package com.jonychen.observability.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jonychen.planning.*;
import com.jonychen.planning.agent.ReActResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Agent 状态持久化服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentStateService {

    private final AgentStateSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;

    /**
     * 保存状态快照
     */
    @Transactional
    public AgentStateSnapshot saveSnapshot(String traceId, String sessionId, String agentType,
                                            Object state, int currentStep, int totalSteps,
                                            String snapshotType) {
        AgentStateSnapshot snapshot = new AgentStateSnapshot();
        snapshot.setTraceId(traceId);
        snapshot.setSessionId(sessionId);
        snapshot.setAgentType(agentType);
        snapshot.setCurrentStepIndex(currentStep);
        snapshot.setTotalSteps(totalSteps);
        snapshot.setSnapshotType(snapshotType);
        snapshot.setResumable(true);
        snapshot.setExpiresAt(LocalDateTime.now().plusHours(24)); // 24小时过期

        // 序列化状态
        Map<String, Object> stateMap = objectMapper.convertValue(state, Map.class);
        snapshot.setInternalState(stateMap);

        snapshotRepository.save(snapshot);
        log.info("Saved agent state snapshot: {} at step {}/{}",
            snapshot.getSnapshotId(), currentStep, totalSteps);

        return snapshot;
    }

    /**
     * 从快照恢复
     */
    public AgentResumeContext resumeFromSnapshot(String snapshotId) {
        AgentStateSnapshot snapshot = snapshotRepository.findBySnapshotId(snapshotId)
            .orElseThrow(() -> new IllegalArgumentException("Snapshot not found: " + snapshotId));

        if (snapshot.isExpired()) {
            throw new IllegalStateException("Snapshot expired: " + snapshotId);
        }

        if (!snapshot.isResumable()) {
            throw new IllegalStateException("Snapshot not resumable: " + snapshotId);
        }

        return new AgentResumeContext(
            snapshot.getTraceId(),
            snapshot.getSessionId(),
            snapshot.getAgentType(),
            snapshot.getInternalState(),
            snapshot.getCurrentStepIndex(),
            snapshot.getTotalSteps()
        );
    }

    /**
     * 获取会话的最新可恢复快照
     */
    public Optional<AgentStateSnapshot> getLatestResumableSnapshot(String sessionId) {
        return snapshotRepository.findLatestResumableBySessionId(sessionId);
    }

    /**
     * 标记快照为不可恢复
     */
    @Transactional
    public void markAsNonResumable(String snapshotId) {
        snapshotRepository.findBySnapshotId(snapshotId).ifPresent(snapshot -> {
            snapshot.setResumable(false);
            snapshotRepository.save(snapshot);
        });
    }

    /**
     * 清理过期快照
     */
    @Transactional
    public int cleanupExpiredSnapshots() {
        List<AgentStateSnapshot> expired = snapshotRepository.findByExpiresAtBefore(LocalDateTime.now());
        snapshotRepository.deleteAll(expired);
        log.info("Cleaned up {} expired snapshots", expired.size());
        return expired.size();
    }

    /**
     * 获取会话的所有快照
     */
    public List<AgentStateSnapshot> getSessionSnapshots(String sessionId) {
        return snapshotRepository.findBySessionIdOrderByCreatedAtDesc(sessionId);
    }

    /**
     * 恢复上下文
     */
    public record AgentResumeContext(
        String traceId,
        String sessionId,
        String agentType,
        Map<String, Object> internalState,
        int currentStepIndex,
        int totalSteps
    ) {}
}
```

### 5.3 可恢复的 ReAct Agent

```java
// src/main/java/com/jonychen/observability/state/ResumableReActAgent.java
package com.jonychen.observability.state;

import com.jonychen.observability.trace.AgentTraceService;
import com.jonychen.planning.TaskContext;
import com.jonychen.planning.agent.ReActAgent;
import com.jonychen.planning.agent.ReActResult;
import com.jonychen.planning.agent.ReActStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 支持断点续传的 ReAct Agent
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResumableReActAgent {

    private final ReActAgent delegate;
    private final AgentStateService stateService;
    private final AgentTraceService traceService;

    /**
     * 执行并支持恢复
     */
    public ReActResult executeWithResume(String question, TaskContext context) {
        // 检查是否有可恢复的快照
        Optional<AgentStateSnapshot> existingSnapshot =
            stateService.getLatestResumableSnapshot(context.getSessionId());

        if (existingSnapshot.isPresent()) {
            log.info("Found resumable snapshot, attempting to resume");
            return resume(existingSnapshot.get(), question, context);
        }

        return execute(question, context);
    }

    /**
     * 从快照恢复执行
     */
    public ReActResult resume(AgentStateSnapshot snapshot, String question, TaskContext context) {
        AgentStateService.AgentResumeContext resumeContext = stateService.resumeFromSnapshot(snapshot.getSnapshotId());

        // 恢复执行上下文
        // ... 解析 internalState 并恢复

        // 继续执行
        ReActResult result = delegate.execute(question, context);

        // 标记旧快照为不可恢复
        stateService.markAsNonResumable(snapshot.getSnapshotId());

        return result;
    }

    /**
     * 正常执行（带检查点）
     */
    private ReActResult execute(String question, TaskContext context) {
        // 调用原始 Agent，在关键点保存快照
        return delegate.execute(question, context);
    }

    /**
     * 暂停执行
     */
    public String pause(String sessionId, String traceId,
                        List<ReActStep> completedSteps, int currentStep, int maxSteps) {
        Map<String, Object> state = new HashMap<>();
        state.put("completedSteps", completedSteps);
        state.put("currentStep", currentStep);

        AgentStateSnapshot snapshot = stateService.saveSnapshot(
            traceId,
            sessionId,
            "REACT",
            state,
            currentStep,
            maxSteps,
            "PAUSE"
        );

        log.info("Agent execution paused, snapshot: {}", snapshot.getSnapshotId());
        return snapshot.getSnapshotId();
    }
}
```

---

## 6. Token 监控与成本追踪

### 6.1 Token 使用统计模型

```java
// src/main/java/com/jonychen/observability/token/TokenUsageRecord.java
package com.jonychen.observability.token;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Token 使用记录
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "token_usage_records", indexes = {
    @Index(name = "idx_token_user", columnList = "userId"),
    @Index(name = "idx_token_date", columnList = "usageDate"),
    @Index(name = "idx_token_model", columnList = "modelName")
})
public class TokenUsageRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;
    private String sessionId;
    private String traceId;

    @Column(nullable = false)
    private String modelName;

    @Column(nullable = false)
    private Long promptTokens;

    @Column(nullable = false)
    private Long completionTokens;

    @Column(nullable = false)
    private Long totalTokens;

    /**
     * 估算成本（美元）
     */
    private Double estimatedCostUsd;

    /**
     * 估算成本（人民币）
     */
    private Double estimatedCostCny;

    /**
     * 使用日期
     */
    private LocalDate usageDate;

    /**
     * 使用时间
     */
    private LocalDateTime usageTime;

    /**
     * 请求类型：CHAT, STREAM, TOOL, EMBEDDING
     */
    @Column(length = 20)
    private String requestType;

    /**
     * 是否成功
     */
    private boolean success;

    @PrePersist
    void prePersist() {
        if (usageTime == null) {
            usageTime = LocalDateTime.now();
        }
        if (usageDate == null) {
            usageDate = LocalDate.now();
        }
    }
}
```

### 6.2 Token 监控服务

```java
// src/main/java/com/jonychen/observability/token/TokenUsageService.java
package com.jonychen.observability.token;

import com.jonychen.admin.entity.User;
import com.jonychen.admin.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Token 使用监控服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenUsageService {

    private final TokenUsageRecordRepository repository;
    private final UserRepository userRepository;

    // 各模型的定价（每 1M tokens）
    private static final Map<String, ModelPricing> MODEL_PRICING = Map.of(
        "qwen-plus", new ModelPricing(0.4, 2.0, 0.004, 0.02),
        "qwen-turbo", new ModelPricing(0.3, 0.6, 0.003, 0.006),
        "glm-4", new ModelPricing(0.1, 0.1, 0.001, 0.001),
        "deepseek-chat", new ModelPricing(0.07, 0.28, 0.0007, 0.0028)
    );

    /**
     * 记录 Token 使用
     */
    @Transactional
    public TokenUsageRecord recordUsage(String userId, String sessionId, String traceId,
                                          String modelName, long promptTokens, long completionTokens,
                                          String requestType, boolean success) {
        ModelPricing pricing = MODEL_PRICING.getOrDefault(modelName,
            new ModelPricing(0.5, 1.5, 0.005, 0.015));

        double costUsd = (promptTokens * pricing.promptUsd + completionTokens * pricing.completionUsd) / 1_000_000;
        double costCny = costUsd * 7.2; // 汇率

        TokenUsageRecord record = new TokenUsageRecord();
        record.setUserId(userId);
        record.setSessionId(sessionId);
        record.setTraceId(traceId);
        record.setModelName(modelName);
        record.setPromptTokens(promptTokens);
        record.setCompletionTokens(completionTokens);
        record.setTotalTokens(promptTokens + completionTokens);
        record.setEstimatedCostUsd(costUsd);
        record.setEstimatedCostCny(costCny);
        record.setRequestType(requestType);
        record.setSuccess(success);

        repository.save(record);

        // 更新用户消费统计
        updateUserConsumption(userId, costCny);

        log.debug("Recorded token usage: user={}, model={}, tokens={}, cost={}",
            userId, modelName, promptTokens + completionTokens, costCny);

        return record;
    }

    /**
     * 获取用户今日使用统计
     */
    public TokenUsageSummary getTodaySummary(String userId) {
        LocalDate today = LocalDate.now();
        List<TokenUsageRecord> records = repository.findByUserIdAndUsageDate(userId, today);

        return aggregateSummary(records);
    }

    /**
     * 获取用户指定日期范围统计
     */
    public TokenUsageSummary getRangeSummary(String userId, LocalDate start, LocalDate end) {
        List<TokenUsageRecord> records = repository.findByUserIdAndUsageDateBetween(userId, start, end);

        return aggregateSummary(records);
    }

    /**
     * 获取全局统计
     */
    public TokenUsageSummary getGlobalSummary(LocalDate start, LocalDate end) {
        List<TokenUsageRecord> records = repository.findByUsageDateBetween(start, end);

        return aggregateSummary(records);
    }

    /**
     * 获取用户使用趋势
     */
    public List<DailyUsage> getUserTrend(String userId, int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days);

        List<TokenUsageRecord> records = repository.findByUserIdAndUsageDateBetween(userId, startDate, endDate);

        Map<LocalDate, List<TokenUsageRecord>> grouped = new TreeMap<>();
        for (TokenUsageRecord record : records) {
            grouped.computeIfAbsent(record.getUsageDate(), k -> new ArrayList<>()).add(record);
        }

        return grouped.entrySet().stream()
            .map(e -> new DailyUsage(
                e.getKey(),
                e.getValue().stream().mapToLong(TokenUsageRecord::getTotalTokens).sum(),
                e.getValue().stream().mapToDouble(TokenUsageRecord::getEstimatedCostCny).sum()
            ))
            .toList();
    }

    /**
     * 检查用户预算
     */
    public BudgetCheckResult checkBudget(String userId) {
        Optional<User> userOpt = userRepository.findByUsername(userId);
        if (userOpt.isEmpty() || userOpt.get().getQuotaLimit() == null) {
            return new BudgetCheckResult(true, 0, null, Double.MAX_VALUE);
        }

        User user = userOpt.get();
        TokenUsageSummary monthSummary = getRangeSummary(userId,
            LocalDate.now().withDayOfMonth(1),
            LocalDate.now());

        double used = monthSummary.totalCostCny();
        double limit = user.getQuotaLimit();
        double remaining = limit - used;

        boolean withinBudget = remaining > 0;

        return new BudgetCheckResult(withinBudget, used, limit, remaining);
    }

    private TokenUsageSummary aggregateSummary(List<TokenUsageRecord> records) {
        if (records.isEmpty()) {
            return new TokenUsageSummary(0, 0, 0, 0.0, 0.0, Map.of());
        }

        long totalPrompt = records.stream().mapToLong(TokenUsageRecord::getPromptTokens).sum();
        long totalCompletion = records.stream().mapToLong(TokenUsageRecord::getCompletionTokens).sum();
        double totalCostUsd = records.stream().mapToDouble(TokenUsageRecord::getEstimatedCostUsd).sum();
        double totalCostCny = records.stream().mapToDouble(TokenUsageRecord::getEstimatedCostCny).sum();

        Map<String, Long> byModel = new HashMap<>();
        for (TokenUsageRecord record : records) {
            byModel.merge(record.getModelName(), record.getTotalTokens(), Long::sum);
        }

        return new TokenUsageSummary(
            records.size(),
            totalPrompt,
            totalCompletion,
            totalCostUsd,
            totalCostCny,
            byModel
        );
    }

    private void updateUserConsumption(String userId, double costCny) {
        userRepository.findByUsername(userId).ifPresent(user -> {
            // 更新累计消费
            user.setTotalConsumption(user.getTotalConsumption() + costCny);
            user.setTokenUsage(user.getTokenUsage() + 1); // 简单计数，实际应该是 token 数
            userRepository.save(user);
        });
    }

    // DTOs
    public record ModelPricing(double promptUsd, double completionUsd,
                               double promptCny, double completionCny) {}
    public record TokenUsageSummary(long requestCount, long totalPromptTokens, long totalCompletionTokens,
                                    double totalCostUsd, double totalCostCny, Map<String, Long> byModel) {}
    public record DailyUsage(LocalDate date, long totalTokens, double totalCost) {}
    public record BudgetCheckResult(boolean withinBudget, double usedAmount,
                                    Double budgetLimit, double remaining) {}
}
```

---

## 7. 前端管理页面

### 7.1 页面结构

```
frontend/src/views/admin/
├── ObservabilityView.vue      # 可观测性总览
├── AgentTraceView.vue         # Agent 执行追踪
├── PromptManagementView.vue  # Prompt 版本管理
├── EvaluationView.vue         # Agent 评测
└── TokenUsageView.vue         # Token 使用统计
```

### 7.2 API 接口

```typescript
// frontend/src/api/observability.ts
import http from '@/utils/http';

export interface AgentTrace {
  id: number;
  traceId: string;
  sessionId: string;
  userId: string;
  agentType: string;
  goal: string;
  status: string;
  startTime: string;
  endTime: string;
  executionTimeMs: number;
  iterations: number;
  finalOutput: string;
  errorMessage: string;
  tokenUsage: {
    promptTokens: number;
    completionTokens: number;
    totalTokens: number;
  };
}

export interface AgentSpan {
  spanId: string;
  type: string;
  name: string;
  input: string;
  output: string;
  startTime: string;
  endTime: string;
  durationMs: number;
  success: boolean;
  error: string;
}

export interface PromptTemplate {
  id: number;
  name: string;
  version: string;
  description: string;
  content: string;
  active: boolean;
  production: boolean;
  createdAt: string;
  abTestConfig?: {
    enabled: boolean;
    variantName: string;
    trafficPercentage: number;
  };
}

export interface EvaluationResult {
  traceId: string;
  overallScore: number;
  metrics: {
    taskCompletionRate: number;
    toolSuccessRate: number;
    avgResponseTimeMs: number;
    qualityScore: number;
  };
  issues: string[];
  recommendation: string;
}

export interface TokenUsageSummary {
  requestCount: number;
  totalPromptTokens: number;
  totalCompletionTokens: number;
  totalCostUsd: number;
  totalCostCny: number;
  byModel: Record<string, number>;
}

export const observabilityApi = {
  // Agent 追踪
  getTraces: (params: { userId?: string; status?: string; limit?: number }) =>
    http.get<AgentTrace[]>('/api/admin/observability/traces', { params }),
  getTraceDetail: (traceId: string) =>
    http.get<AgentTrace>(`/api/admin/observability/traces/${traceId}`),
  getTraceSpans: (traceId: string) =>
    http.get<AgentSpan[]>(`/api/admin/observability/traces/${traceId}/spans`),
  getActiveTraces: () =>
    http.get<AgentTrace[]>('/api/admin/observability/traces/active'),

  // Prompt 管理
  getPrompts: () =>
    http.get<PromptTemplate[]>('/api/admin/prompts'),
  getPromptVersions: (name: string) =>
    http.get<PromptTemplate[]>(`/api/admin/prompts/${name}/versions`),
  createPrompt: (data: Partial<PromptTemplate>) =>
    http.post<PromptTemplate>('/api/admin/prompts', data),
  activateVersion: (name: string, version: string) =>
    http.post(`/api/admin/prompts/${name}/versions/${version}/activate`),
  compareToProduction: (name: string, version: string) =>
    http.post(`/api/admin/prompts/${name}/versions/${version}/promote`),
  rollback: (name: string, version: string) =>
    http.post(`/api/admin/prompts/${name}/rollback/${version}`),
  configureABTest: (name: string, config: { baselineVersion: string; variantVersion: string; trafficPercentage: number }) =>
    http.post(`/api/admin/prompts/${name}/ab-test`, config),

  // 评测
  evaluateTrace: (traceId: string) =>
    http.post<EvaluationResult>(`/api/admin/evaluation/evaluate/${traceId}`),
  generateReport: (traceIds: string[]) =>
    http.post('/api/admin/evaluation/report', { traceIds }),

  // Token 使用
  getTodaySummary: (userId?: string) =>
    http.get<TokenUsageSummary>('/api/admin/tokens/summary/today', { params: { userId } }),
  getRangeSummary: (userId: string, start: string, end: string) =>
    http.get<TokenUsageSummary>('/api/admin/tokens/summary/range', { params: { userId, start, end } }),
  getUserTrend: (userId: string, days: number) =>
    http.get<{ date: string; totalTokens: number; totalCost: number }[]>('/api/admin/tokens/trend', { params: { userId, days } }),
  checkBudget: (userId: string) =>
    http.get<{ withinBudget: boolean; usedAmount: number; budgetLimit: number; remaining: number }>(
      `/api/admin/tokens/budget/${userId}`
    ),
};
```

### 7.3 Agent 追踪页面

```vue
<!-- frontend/src/views/admin/AgentTraceView.vue -->
<template>
  <div class="agent-trace-view">
    <!-- 筛选栏 -->
    <el-card class="mb-4">
      <el-row :gutter="20">
        <el-col :span="6">
          <el-select v-model="filters.status" placeholder="执行状态" clearable>
            <el-option label="全部" value="" />
            <el-option label="运行中" value="RUNNING" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="失败" value="FAILED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-col>
        <el-col :span="6">
          <el-select v-model="filters.agentType" placeholder="Agent 类型" clearable>
            <el-option label="全部" value="" />
            <el-option label="ReAct" value="REACT" />
            <el-option label="Plan-Execute" value="PLAN_EXECUTE" />
          </el-select>
        </el-col>
        <el-col :span="6">
          <el-input v-model="filters.userId" placeholder="用户 ID" clearable />
        </el-col>
        <el-col :span="6">
          <el-button type="primary" @click="loadTraces">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- 活跃追踪提示 -->
    <el-alert
      v-if="activeTraces.length > 0"
      :title="`有 ${activeTraces.length} 个 Agent 正在执行`"
      type="info"
      :closable="false"
      class="mb-4"
    >
      <template #default>
        <el-tag v-for="trace in activeTraces" :key="trace.traceId" class="mr-2">
          {{ trace.agentType }} - {{ trace.traceId.slice(0, 8) }}
        </el-tag>
      </template>
    </el-alert>

    <!-- 追踪列表 -->
    <el-card>
      <el-table :data="traces" v-loading="loading" stripe>
        <el-table-column prop="traceId" label="Trace ID" width="180">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDetail(row)">
              {{ row.traceId.slice(0, 8) }}...
            </el-button>
          </template>
        </el-table-column>
        <el-table-column prop="agentType" label="类型" width="120">
          <template #default="{ row }">
            <el-tag :type="row.agentType === 'REACT' ? 'success' : 'warning'">
              {{ row.agentType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="goal" label="任务目标" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="iterations" label="迭代次数" width="100" />
        <el-table-column prop="executionTimeMs" label="耗时(ms)" width="100">
          <template #default="{ row }">
            {{ row.executionTimeMs || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="tokenUsage.totalTokens" label="Tokens" width="100" />
        <el-table-column prop="startTime" label="开始时间" width="180">
          <template #default="{ row }">
            {{ formatTime(row.startTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="showSpanView(row)">
              追踪详情
            </el-button>
            <el-button link type="success" @click="evaluateTrace(row)">
              评测
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        class="mt-4"
        @size-change="loadTraces"
        @current-change="loadTraces"
      />
    </el-card>

    <!-- 追踪详情对话框 -->
    <el-dialog
      v-model="detailVisible"
      :title="`追踪详情 - ${selectedTrace?.traceId?.slice(0, 8)}`"
      width="80%"
      destroy-on-close
    >
      <AgentTraceDetail
        v-if="selectedTrace"
        :trace="selectedTrace"
        :spans="spans"
      />
    </el-dialog>

    <!-- 追踪可视化 -->
    <el-dialog
      v-model="spanViewVisible"
      title="执行追踪可视化"
      width="90%"
      destroy-on-close
    >
      <AgentSpanVisualization
        v-if="selectedTrace"
        :trace="selectedTrace"
        :spans="spans"
      />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue';
import { ElMessage } from 'element-plus';
import { observabilityApi, type AgentTrace, type AgentSpan } from '@/api/observability';
import AgentTraceDetail from '@/components/observability/AgentTraceDetail.vue';
import AgentSpanVisualization from '@/components/observability/AgentSpanVisualization.vue';

const loading = ref(false);
const traces = ref<AgentTrace[]>([]);
const activeTraces = ref<AgentTrace[]>([]);
const spans = ref<AgentSpan[]>([]);
const selectedTrace = ref<AgentTrace | null>(null);
const detailVisible = ref(false);
const spanViewVisible = ref(false);

const filters = reactive({
  status: '',
  agentType: '',
  userId: ''
});

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
});

const loadTraces = async () => {
  loading.value = true;
  try {
    traces.value = await observabilityApi.getTraces({
      status: filters.status,
      userId: filters.userId,
      limit: pagination.size
    });
  } catch (error) {
    ElMessage.error('加载追踪列表失败');
  } finally {
    loading.value = false;
  }
};

const loadActiveTraces = async () => {
  try {
    activeTraces.value = await observabilityApi.getActiveTraces();
  } catch (error) {
    console.error('加载活跃追踪失败', error);
  }
};

const showDetail = async (trace: AgentTrace) => {
  selectedTrace.value = trace;
  try {
    spans.value = await observabilityApi.getTraceSpans(trace.traceId);
    detailVisible.value = true;
  } catch (error) {
    ElMessage.error('加载追踪详情失败');
  }
};

const showSpanView = async (trace: AgentTrace) => {
  selectedTrace.value = trace;
  try {
    spans.value = await observabilityApi.getTraceSpans(trace.traceId);
    spanViewVisible.value = true;
  } catch (error) {
    ElMessage.error('加载追踪可视化失败');
  }
};

const evaluateTrace = async (trace: AgentTrace) => {
  try {
    const result = await observabilityApi.evaluateTrace(trace.traceId);
    ElMessage.success(`评测完成，得分: ${(result.overallScore * 100).toFixed(1)}%`);
  } catch (error) {
    ElMessage.error('评测失败');
  }
};

const resetFilters = () => {
  filters.status = '';
  filters.agentType = '';
  filters.userId = '';
  loadTraces();
};

const getStatusType = (status: string) => {
  const types: Record<string, string> = {
    'RUNNING': 'primary',
    'COMPLETED': 'success',
    'FAILED': 'danger',
    'CANCELLED': 'info'
  };
  return types[status] || '';
};

const formatTime = (time: string) => {
  return new Date(time).toLocaleString('zh-CN');
};

// 自动刷新活跃追踪
let refreshInterval: number;

onMounted(() => {
  loadTraces();
  loadActiveTraces();
  refreshInterval = window.setInterval(loadActiveTraces, 5000);
});

// 组件卸载时清除定时器
import { onUnmounted } from 'vue';
onUnmounted(() => {
  if (refreshInterval) {
    clearInterval(refreshInterval);
  }
});
</script>
```

### 7.4 Prompt 管理页面

```vue
<!-- frontend/src/views/admin/PromptManagementView.vue -->
<template>
  <div class="prompt-management">
    <!-- 顶部操作栏 -->
    <div class="mb-4 flex justify-between items-center">
      <el-button type="primary" @click="showCreateDialog">
        <el-icon><Plus /></el-icon>
        创建模板
      </el-button>
      <el-input
        v-model="searchName"
        placeholder="搜索模板名称"
        style="width: 300px"
        clearable
        @input="loadPrompts"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
    </div>

    <!-- 模板列表 -->
    <el-row :gutter="20">
      <el-col
        v-for="prompt in promptGroups"
        :key="prompt.name"
        :span="12"
        class="mb-4"
      >
        <el-card shadow="hover" class="prompt-card">
          <template #header>
            <div class="flex justify-between items-center">
              <div>
                <span class="font-bold">{{ prompt.name }}</span>
                <el-tag v-if="prompt.activeVersion" type="success" size="small" class="ml-2">
                  活跃: {{ prompt.activeVersion }}
                </el-tag>
              </div>
              <div>
                <el-button link type="primary" @click="showVersions(prompt.name)">
                  版本历史
                </el-button>
              </div>
            </div>
          </template>

          <div class="prompt-content">
            <p class="text-gray-600 mb-2">{{ prompt.description || '暂无描述' }}</p>
            <div class="text-sm text-gray-400">
              版本数: {{ prompt.versions.length }} |
              最后更新: {{ formatTime(prompt.updatedAt) }}
            </div>
          </div>

          <template #footer>
            <el-button-group>
              <el-button size="small" @click="showEditDialog(prompt)">
                <el-icon><Edit /></el-icon>
                编辑
              </el-button>
              <el-button size="small" type="warning" @click="showABTestDialog(prompt)">
                A/B 测试
              </el-button>
              <el-button size="small" type="success" @click="showComparisonDialog(prompt)">
                对比
              </el-button>
            </el-button-group>
          </template>
        </el-card>
      </el-col>
    </el-row>

    <!-- 创建/编辑对话框 -->
    <el-dialog
      v-model="createDialogVisible"
      :title="editingPrompt ? '编辑模板' : '创建模板'"
      width="60%"
      destroy-on-close
    >
      <el-form :model="promptForm" label-width="100px">
        <el-form-item label="模板名称" required>
          <el-input v-model="promptForm.name" :disabled="!!editingPrompt" />
        </el-form-item>
        <el-form-item label="版本号">
          <el-input v-model="promptForm.version" placeholder="如 1.0.0" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="promptForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="模板内容" required>
          <el-input
            v-model="promptForm.content"
            type="textarea"
            :rows="10"
            placeholder="使用 {{variable}} 定义变量"
          />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="promptForm.tags" placeholder="多个标签用逗号分隔" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="savePrompt">保存</el-button>
      </template>
    </el-dialog>

    <!-- 版本历史对话框 -->
    <el-dialog
      v-model="versionDialogVisible"
      :title="`版本历史 - ${selectedPromptName}`"
      width="80%"
      destroy-on-close
    >
      <el-table :data="versions" stripe>
        <el-table-column prop="version" label="版本" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.active" type="success">{{ row.version }}</el-tag>
            <el-tag v-else-if="row.production" type="warning">{{ row.version }}</el-tag>
            <span v-else>{{ row.version }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" />
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="A/B 测试" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.abTestConfig?.enabled" type="info">
              {{ row.abTestConfig.trafficPercentage }}%
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button-group>
              <el-button
                link
                type="primary"
                @click="viewVersionContent(row)"
              >
                查看
              </el-button>
              <el-button
                link
                type="success"
                @click="activateVersion(row)"
                :disabled="row.active"
              >
                激活
              </el-button>
              <el-button
                link
                type="warning"
                @click="promoteToProduction(row)"
                :disabled="row.production"
              >
                推送生产
              </el-button>
              <el-button
                link
                type="danger"
                @click="rollbackVersion(row)"
                :disabled="row.active"
              >
                回滚
              </el-button>
            </el-button-group>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- A/B 测试配置对话框 -->
    <el-dialog
      v-model="abTestDialogVisible"
      title="配置 A/B 测试"
      width="50%"
      destroy-on-close
    >
      <el-form :model="abTestForm" label-width="120px">
        <el-form-item label="基线版本">
          <el-select v-model="abTestForm.baselineVersion" placeholder="选择基线版本">
            <el-option
              v-for="v in versions"
              :key="v.version"
              :label="v.version"
              :value="v.version"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="实验版本">
          <el-select v-model="abTestForm.variantVersion" placeholder="选择实验版本">
            <el-option
              v-for="v in versions"
              :key="v.version"
              :label="v.version"
              :value="v.version"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="实验名称">
          <el-input v-model="abTestForm.variantName" placeholder="如 experiment-v2" />
        </el-form-item>
        <el-form-item label="流量分配 (%)">
          <el-slider v-model="abTestForm.trafficPercentage" :min="1" :max="50" show-input />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="abTestDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="configureABTest">启动 A/B 测试</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { observabilityApi, type PromptTemplate } from '@/api/observability';

const prompts = ref<PromptTemplate[]>([]);
const versions = ref<PromptTemplate[]>([]);
const searchName = ref('');
const selectedPromptName = ref('');

const createDialogVisible = ref(false);
const versionDialogVisible = ref(false);
const abTestDialogVisible = ref(false);
const editingPrompt = ref<PromptTemplate | null>(null);

const promptForm = reactive({
  name: '',
  version: '',
  description: '',
  content: '',
  tags: ''
});

const abTestForm = reactive({
  baselineVersion: '',
  variantVersion: '',
  variantName: '',
  trafficPercentage: 10
});

// 按名称分组模板
const promptGroups = computed(() => {
  const groups = new Map<string, any>();

  for (const p of prompts.value) {
    if (!groups.has(p.name)) {
      groups.set(p.name, {
        name: p.name,
        description: p.description,
        activeVersion: p.active ? p.version : null,
        versions: [],
        updatedAt: p.createdAt
      });
    }

    const group = groups.get(p.name);
    group.versions.push(p);

    if (p.active) {
      group.activeVersion = p.version;
    }
    if (new Date(p.createdAt) > new Date(group.updatedAt)) {
      group.updatedAt = p.createdAt;
    }
  }

  return Array.from(groups.values());
});

const loadPrompts = async () => {
  try {
    prompts.value = await observabilityApi.getPrompts();
  } catch (error) {
    ElMessage.error('加载模板列表失败');
  }
};

const showCreateDialog = () => {
  editingPrompt.value = null;
  Object.assign(promptForm, {
    name: '',
    version: '1.0.0',
    description: '',
    content: '',
    tags: ''
  });
  createDialogVisible.value = true;
};

const showEditDialog = (prompt: any) => {
  const latestVersion = prompt.versions[0];
  editingPrompt.value = latestVersion;
  Object.assign(promptForm, {
    name: prompt.name,
    version: latestVersion.version,
    description: latestVersion.description,
    content: latestVersion.content,
    tags: latestVersion.tags
  });
  createDialogVisible.value = true;
};

const savePrompt = async () => {
  try {
    await observabilityApi.createPrompt(promptForm);
    ElMessage.success('保存成功');
    createDialogVisible.value = false;
    loadPrompts();
  } catch (error) {
    ElMessage.error('保存失败');
  }
};

const showVersions = async (name: string) => {
  selectedPromptName.value = name;
  try {
    versions.value = await observabilityApi.getPromptVersions(name);
    versionDialogVisible.value = true;
  } catch (error) {
    ElMessage.error('加载版本历史失败');
  }
};

const activateVersion = async (version: PromptTemplate) => {
  try {
    await ElMessageBox.confirm(
      `确定要激活版本 ${version.version} 吗？`,
      '确认激活',
      { type: 'warning' }
    );
    await observabilityApi.activateVersion(selectedPromptName.value, version.version);
    ElMessage.success('版本已激活');
    showVersions(selectedPromptName.value);
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('激活失败');
    }
  }
};

const promoteToProduction = async (version: PromptTemplate) => {
  try {
    await ElMessageBox.confirm(
      `确定要将版本 ${version.version} 推送到生产环境吗？`,
      '确认推送',
      { type: 'warning' }
    );
    await observabilityApi.compareToProduction(selectedPromptName.value, version.version);
    ElMessage.success('已推送生产');
    showVersions(selectedPromptName.value);
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('推送失败');
    }
  }
};

const rollbackVersion = async (version: PromptTemplate) => {
  try {
    await ElMessageBox.confirm(
      `确定要回滚到版本 ${version.version} 吗？`,
      '确认回滚',
      { type: 'warning' }
    );
    await observabilityApi.rollback(selectedPromptName.value, version.version);
    ElMessage.success('回滚成功');
    showVersions(selectedPromptName.value);
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('回滚失败');
    }
  }
};

const showABTestDialog = async (prompt: any) => {
  selectedPromptName.value = prompt.name;
  try {
    versions.value = await observabilityApi.getPromptVersions(prompt.name);
    abTestForm.baselineVersion = prompt.activeVersion || versions.value[0]?.version;
    abTestForm.variantVersion = versions.value[1]?.version || '';
    abTestDialogVisible.value = true;
  } catch (error) {
    ElMessage.error('加载版本失败');
  }
};

const configureABTest = async () => {
  try {
    await observabilityApi.configureABTest(selectedPromptName.value, {
      baselineVersion: abTestForm.baselineVersion,
      variantVersion: abTestForm.variantVersion,
      trafficPercentage: abTestForm.trafficPercentage
    });
    ElMessage.success('A/B 测试已配置');
    abTestDialogVisible.value = false;
  } catch (error) {
    ElMessage.error('配置失败');
  }
};

const formatTime = (time: string) => {
  return new Date(time).toLocaleString('zh-CN');
};

const viewVersionContent = (version: PromptTemplate) => {
  ElMessageBox.alert(version.content, `版本 ${version.version} 内容`, {
    customClass: 'prompt-content-dialog'
  });
};

const showComparisonDialog = (prompt: any) => {
  // TODO: 实现版本对比功能
  ElMessage.info('版本对比功能开发中');
};

onMounted(() => {
  loadPrompts();
});
</script>

<style scoped>
.prompt-card {
  height: 100%;
}

.prompt-content {
  min-height: 80px;
}
</style>
```

---

## 8. 数据库 Schema

### 8.1 新增表结构

```sql
-- infra/postgres/init/02-agent-observability.sql

-- Agent 追踪表
CREATE TABLE agent_traces (
    id BIGSERIAL PRIMARY KEY,
    trace_id VARCHAR(36) UNIQUE NOT NULL,
    session_id VARCHAR(36),
    user_id VARCHAR(50),
    agent_type VARCHAR(50) NOT NULL,
    goal TEXT,
    status VARCHAR(20) NOT NULL,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    execution_time_ms BIGINT,
    iterations INTEGER DEFAULT 0,
    final_output TEXT,
    error_message TEXT,
    prompt_tokens BIGINT,
    completion_tokens BIGINT,
    total_tokens BIGINT,
    spans JSONB DEFAULT '[]'::jsonb,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_trace_session ON agent_traces(session_id);
CREATE INDEX idx_trace_status ON agent_traces(status);
CREATE INDEX idx_trace_start_time ON agent_traces(start_time);
CREATE INDEX idx_trace_user ON agent_traces(user_id);

-- Agent Span 表
CREATE TABLE agent_trace_spans (
    id BIGSERIAL PRIMARY KEY,
    trace_id VARCHAR(36) NOT NULL,
    span_id VARCHAR(36) NOT NULL,
    parent_span_id VARCHAR(36),
    type VARCHAR(50) NOT NULL,
    name VARCHAR(200),
    input TEXT,
    output TEXT,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    duration_ms BIGINT,
    success BOOLEAN DEFAULT true,
    error TEXT,
    prompt_tokens BIGINT,
    completion_tokens BIGINT,
    attributes JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_span_trace ON agent_trace_spans(trace_id);
CREATE INDEX idx_span_type ON agent_trace_spans(type);

-- Prompt 模板表
CREATE TABLE prompt_templates (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    version VARCHAR(20) NOT NULL,
    description VARCHAR(500),
    content TEXT NOT NULL,
    variables JSONB DEFAULT '[]'::jsonb,
    tags VARCHAR(500),
    active BOOLEAN DEFAULT false,
    production BOOLEAN DEFAULT false,
    created_by VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ab_test_enabled BOOLEAN DEFAULT false,
    ab_test_variant_name VARCHAR(50),
    ab_test_traffic_percentage DECIMAL(5,2) DEFAULT 0,
    ab_test_baseline_version VARCHAR(20),
    total_uses BIGINT DEFAULT 0,
    success_count BIGINT DEFAULT 0,
    failure_count BIGINT DEFAULT 0,
    avg_response_time DECIMAL(10,2),
    avg_token_usage DECIMAL(10,2),
    UNIQUE(name, version)
);

CREATE INDEX idx_prompt_name ON prompt_templates(name);
CREATE INDEX idx_prompt_active ON prompt_templates(active);
CREATE INDEX idx_prompt_production ON prompt_templates(production);

-- Agent 状态快照表
CREATE TABLE agent_state_snapshots (
    id BIGSERIAL PRIMARY KEY,
    snapshot_id VARCHAR(36) UNIQUE NOT NULL,
    trace_id VARCHAR(36),
    session_id VARCHAR(36),
    agent_type VARCHAR(50),
    current_step_index INTEGER DEFAULT 0,
    total_steps INTEGER DEFAULT 0,
    internal_state JSONB DEFAULT '{}'::jsonb,
    execution_history JSONB DEFAULT '[]'::jsonb,
    snapshot_type VARCHAR(20),
    resumable BOOLEAN DEFAULT true,
    expires_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_snapshot_session ON agent_state_snapshots(session_id);
CREATE INDEX idx_snapshot_resumable ON agent_state_snapshots(resumable);
CREATE INDEX idx_snapshot_expires ON agent_state_snapshots(expires_at);

-- Token 使用记录表
CREATE TABLE token_usage_records (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(50),
    session_id VARCHAR(36),
    trace_id VARCHAR(36),
    model_name VARCHAR(100) NOT NULL,
    prompt_tokens BIGINT NOT NULL,
    completion_tokens BIGINT NOT NULL,
    total_tokens BIGINT NOT NULL,
    estimated_cost_usd DECIMAL(10,6),
    estimated_cost_cny DECIMAL(10,6),
    usage_date DATE NOT NULL,
    usage_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    request_type VARCHAR(20),
    success BOOLEAN DEFAULT true
);

CREATE INDEX idx_token_user ON token_usage_records(user_id);
CREATE INDEX idx_token_date ON token_usage_records(usage_date);
CREATE INDEX idx_token_model ON token_usage_records(model_name);
CREATE INDEX idx_token_session ON token_usage_records(session_id);

-- 评测结果表
CREATE TABLE evaluation_results (
    id BIGSERIAL PRIMARY KEY,
    trace_id VARCHAR(36) NOT NULL,
    evaluator_name VARCHAR(50) NOT NULL,
    passed BOOLEAN NOT NULL,
    score DECIMAL(5,4) NOT NULL,
    details JSONB DEFAULT '{}'::jsonb,
    issues JSONB DEFAULT '[]'::jsonb,
    recommendation TEXT,
    evaluated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_eval_trace ON evaluation_results(trace_id);
CREATE INDEX idx_eval_name ON evaluation_results(evaluator_name);
```

---

## 9. 实施计划

| 阶段 | 内容 | 预估工时 |
|------|------|---------|
| 第一阶段 | Agent 执行追踪系统 + 数据库 | 3天 |
| 第二阶段 | Token 监控与成本追踪 | 2天 |
| 第三阶段 | Prompt 版本管理系统 | 2天 |
| 第四阶段 | Agent 评测框架 | 2天 |
| 第五阶段 | 状态持久化与恢复 | 2天 |
| 第六阶段 | 前端管理页面 | 3天 |
| 第七阶段 | 集成测试与优化 | 1天 |
| **总计** | | **15天** |

---

## 10. 后续扩展

- **多 Agent 协作框架**: 任务分发、结果聚合、Agent 间通信
- **工具沙箱隔离**: Docker 容器执行、资源限制
- **实时监控大屏**: WebSocket 推送、实时指标更新
- **告警通知**: 集成钉钉/企业微信/Slack