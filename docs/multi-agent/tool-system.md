# 工具系统设计

> 多 Agent 生产级系统技术方案 - 子文档

---

## 1. 设计目标

### 1.1 核心目标

| 目标 | 说明 |
|------|------|
| **统一工具体系** | 整合现有 `@Tool` (LangChain4j) 和 `@AgentTool` (自定义)，一套体系 |
| **安全边界** | 只读白名单、敏感操作确认、权限校验 |
| **可观测** | 工具调用记录、执行耗时、成功/失败统计 |
| **可扩展** | 新增工具只需注解 + 注册，不改框架代码 |

### 1.2 工具分类

| 类别 | 说明 | 示例工具 |
|------|------|----------|
| **只读工具** | 无副作用，无需确认 | `get_model_health`, `list_tables`, `execute_readonly_query` |
| **写入工具** | 有副作用，需要确认 | `adjust_model_weight`, `create_prompt_version`, `run_tests` |
| **外部调用** | 调用外部服务，有超时风险 | `call_external_api`, `send_notification` |
| **危险工具** | 高风险操作，需要 ADMIN 确认 | `toggle_model_enabled`, `delete_user_data` |

---

## 2. 工具注解体系

### 2.1 @AgentTool 注解（统一）

```java
package com.jonychen.tool;

import java.lang.annotation.*;
import java.lang.reflect.Method;

/**
 * Agent 工具注解
 * 
 * 统一的工具定义注解，兼容 LangChain4j AiServices。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AgentTool {
    
    /**
     * 工具名称（默认使用方法名）
     */
    String name() default "";
    
    /**
     * 工具描述（必需）
     */
    String description();
    
    /**
     * 工具分类
     */
    ToolCategory category() default ToolCategory.CUSTOM;
    
    /**
     * 风险等级
     */
    RiskLevel riskLevel() default RiskLevel.LOW;
    
    /**
     * 是否需要用户确认
     */
    boolean requiresConfirmation() default false;
    
    /**
     * 需要的权限列表
     */
    String[] requiredPermissions() default {};
    
    /**
     * 超时时间（毫秒）
     */
    long timeoutMs() default 30000;
    
    /**
     * 最大重试次数
     */
    int maxRetries() default 2;

    /**
     * 允许执行该工具的角色列表（空数组表示不限制）
     */
    String[] allowedRoles() default {};
}

/**
 * 工具参数注解
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolParam {
    /**
     * 参数名称
     */
    String name() default "";
    
    /**
     * 参数描述
     */
    String description() default "";
    
    /**
     * 是否必需
     */
    boolean required() default true;
    
    /**
     * 默认值
     */
    String defaultValue() default "";
    
    /**
     * 枚举值（用于参数校验）
     */
    String[] enumValues() default {};
}

/**
 * 工具分类枚举
 */
public enum ToolCategory {
    MODEL_STATE("模型状态", "查询和调整模型状态"),
    DATABASE("数据库", "数据库查询和操作"),
    PROMPT("Prompt", "Prompt 版本管理"),
    TEST("测试", "测试生成和执行"),
    SYSTEM("系统", "系统信息和日志"),
    EXTERNAL("外部服务", "调用外部 API"),
    CUSTOM("自定义", "自定义工具");
    
    private final String displayName;
    private final String description;
}

/**
 * 风险等级枚举
 */
public enum RiskLevel {
    LOW("低风险", "只读操作，无副作用"),
    MEDIUM("中风险", "有副作用，可恢复"),
    HIGH("高风险", "不可逆操作，需谨慎"),
    CRITICAL("极高风险", "危险操作，需要 ADMIN 确认");
    
    private final String displayName;
    private final String description;
}
```

### 2.2 工具定义（增强版）

```java
package com.jonychen.tool;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 工具定义（增强版）
 * 
 * 相比现有 ToolDefinition，增加以下字段：
 * - category: 工具分类
 * - riskLevel: 风险等级
 * - requiresConfirmation: 是否需要确认
 * - allowedRoles: 允许调用的角色
 */
public record ToolDefinition(
    String name,
    String description,
    ToolCategory category,
    RiskLevel riskLevel,
    ToolParameterSchema parameters,
    ToolExecutor executor,
    List<String> requiredPermissions,
    List<String> allowedRoles,    // 新增：允许的角色
    boolean requiresConfirmation, // 新增：是否需要确认
    Duration timeout,
    int maxRetries
) {
    // 静态工厂方法
    public static ToolDefinition of(String name, String description, ToolExecutor executor) {
        return new ToolDefinition(
            name, description,
            ToolCategory.CUSTOM, RiskLevel.LOW,
            ToolParameterSchema.empty(),
            executor,
            List.of(), List.of(),
            false,
            Duration.ofSeconds(30), 2
        );
    }
    
    public static ToolDefinition readOnly(String name, String description, ToolExecutor executor) {
        return new ToolDefinition(
            name, description,
            ToolCategory.CUSTOM, RiskLevel.LOW,
            ToolParameterSchema.empty(),
            executor,
            List.of(), List.of(),
            false,  // 只读不需要确认
            Duration.ofSeconds(30), 2
        );
    }
    
    public static ToolDefinition writeOperation(
            String name, 
            String description, 
            ToolExecutor executor,
            RiskLevel riskLevel) {
        return new ToolDefinition(
            name, description,
            ToolCategory.CUSTOM, riskLevel,
            ToolParameterSchema.empty(),
            executor,
            List.of(), List.of(),
            true,  // 写入操作需要确认
            Duration.ofSeconds(30), 2
        );
    }
    
    // Builder 模式
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String name;
        private String description;
        private ToolCategory category = ToolCategory.CUSTOM;
        private RiskLevel riskLevel = RiskLevel.LOW;
        private ToolParameterSchema parameters = ToolParameterSchema.empty();
        private ToolExecutor executor;
        private List<String> requiredPermissions = List.of();
        private List<String> allowedRoles = List.of();
        private boolean requiresConfirmation = false;
        private Duration timeout = Duration.ofSeconds(30);
        private int maxRetries = 2;
        
        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder category(ToolCategory category) { this.category = category; return this; }
        public Builder riskLevel(RiskLevel riskLevel) { this.riskLevel = riskLevel; return this; }
        public Builder parameters(ToolParameterSchema parameters) { this.parameters = parameters; return this; }
        public Builder executor(ToolExecutor executor) { this.executor = executor; return this; }
        public Builder requiredPermissions(List<String> permissions) { this.requiredPermissions = permissions; return this; }
        public Builder allowedRoles(List<String> roles) { this.allowedRoles = roles; return this; }
        public Builder requiresConfirmation(boolean requires) { this.requiresConfirmation = requires; return this; }
        public Builder timeout(Duration timeout) { this.timeout = timeout; return this; }
        public Builder maxRetries(int maxRetries) { this.maxRetries = maxRetries; return this; }
        
        public ToolDefinition build() {
            Objects.requireNonNull(name, "name is required");
            Objects.requireNonNull(description, "description is required");
            Objects.requireNonNull(executor, "executor is required");
            
            // 高风险操作自动设置为需要确认
            // CRITICAL：无任何角色豁免，必须确认
            // HIGH：ADMIN 可豁免确认（由 AgentPermissionService.requiresConfirmation() 控制），但标记 requiresConfirmation=true
            if (riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.CRITICAL) {
                this.requiresConfirmation = true;
            }
            
            return new ToolDefinition(
                name, description, category, riskLevel,
                parameters, executor,
                requiredPermissions, allowedRoles,
                requiresConfirmation, timeout, maxRetries
            );
        }
    }
}
```

---

## 3. 工具执行器

### 3.1 ToolExecutor 接口

```java
package com.jonychen.tool;

/**
 * 工具执行器接口
 */
@FunctionalInterface
public interface ToolExecutor {
    
    /**
     * 执行工具
     * 
     * @param params 输入参数
     * @return 执行结果
     * @throws ToolExecutionException 执行异常
     */
    ToolResult execute(Map<String, Object> params) throws ToolExecutionException;
    
    /**
     * 参数校验（可选）
     */
    default ValidationResult validate(Map<String, Object> params) {
        return ValidationResult.success();
    }
}

/**
 * 校验结果
 */
public record ValidationResult(boolean valid, List<String> errors) {
    public static ValidationResult success() {
        return new ValidationResult(true, List.of());
    }
    
    public static ValidationResult failure(List<String> errors) {
        return new ValidationResult(false, errors);
    }
}
```

### 3.2 工具执行结果

```java
package com.jonychen.tool;

/**
 * 工具执行结果（增强版）
 */
public record ToolResult(
    boolean success,
    Object data,
    String error,
    long executionTimeMs,
    Map<String, Object> metadata,    // 额外元数据
    boolean pending,                 // 是否等待确认
    String confirmationId,           // 确认 ID
    RiskLevel riskLevel,             // 风险等级（用于前端展示）
    String confirmationMessage       // 确认提示消息
) {
    // 成功工厂方法
    public static ToolResult success(Object data) {
        return new ToolResult(true, data, null, 0, Map.of(), false, null, RiskLevel.LOW, null);
    }
    
    public static ToolResult success(Object data, Map<String, Object> metadata) {
        return new ToolResult(true, data, null, 0, metadata, false, null, RiskLevel.LOW, null);
    }
    
    // 失败工厂方法
    public static ToolResult failure(String error) {
        return new ToolResult(false, null, error, 0, Map.of(), false, null, RiskLevel.LOW, null);
    }
    
    public static ToolResult failure(String error, Map<String, Object> metadata) {
        return new ToolResult(false, null, error, 0, metadata, false, null, RiskLevel.LOW, null);
    }
    
    // 等待确认工厂方法
    public static ToolResult pendingConfirmation(
            String confirmationId, 
            String message,
            RiskLevel riskLevel) {
        return new ToolResult(
            false, null, null, 0, Map.of(),
            true, confirmationId, riskLevel, message
        );
    }
    
    // 设置执行时间
    public ToolResult withExecutionTime(long executionTimeMs) {
        return new ToolResult(
            success, data, error, executionTimeMs,
            metadata, pending, confirmationId, riskLevel, confirmationMessage
        );
    }
}
```

---

## 4. 工具注册中心

### 4.1 ToolRegistry 接口

```java
package com.jonychen.tool;

import java.util.*;

/**
 * 工具注册中心接口
 */
public interface ToolRegistry {
    
    // ===== 注册管理 =====
    
    /**
     * 注册工具
     */
    void register(ToolDefinition tool);
    
    /**
     * 注销工具
     */
    void unregister(String toolName);
    
    /**
     * 扫描并注册带 @AgentTool 注解的类
     */
    void registerAnnotatedTools(Object toolProvider);
    
    // ===== 查询 =====
    
    /**
     * 获取工具定义
     */
    Optional<ToolDefinition> getTool(String toolName);
    
    /**
     * 检查工具是否存在
     */
    boolean hasTool(String toolName);
    
    /**
     * 获取所有工具名称
     */
    Set<String> getToolNames();
    
    /**
     * 获取所有工具定义
     */
    Collection<ToolDefinition> getAllTools();
    
    /**
     * 按分类获取工具
     */
    List<ToolDefinition> getToolsByCategory(ToolCategory category);
    
    /**
     * 按风险等级获取工具
     */
    List<ToolDefinition> getToolsByRiskLevel(RiskLevel riskLevel);
    
    /**
     * 按权限获取可用工具
     */
    List<ToolDefinition> getToolsByPermissions(List<String> permissions);
    
    /**
     * 按角色获取可用工具
     */
    List<ToolDefinition> getToolsByRole(String role);
    
    // ===== 执行 =====
    
    /**
     * 执行工具
     */
    ToolResult execute(String toolName, Map<String, Object> params);
    
    /**
     * 执行工具（带上下文）
     */
    ToolResult execute(String toolName, Map<String, Object> params, ToolExecutionContext context);
    
    // ===== 统计 =====
    
    /**
     * 获取工具调用统计
     */
    ToolStatistics getStatistics(String toolName);
    
    /**
     * 获取所有工具统计
     */
    Map<String, ToolStatistics> getAllStatistics();
}

/**
 * 工具执行上下文
 */
public record ToolExecutionContext(
    String traceId,
    String sessionId,
    String userId,
    Set<String> userRoles,
    Map<String, Object> extra
) {}

/**
 * 工具调用统计
 */
public record ToolStatistics(
    String toolName,
    long totalCalls,
    long successCalls,
    long failedCalls,
    double avgExecutionTimeMs,
    long lastCallTime
) {}
```

### 4.2 DefaultToolRegistry 实现

```java
package com.jonychen.tool.impl;

import com.jonychen.tool.*;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecificationConverter;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * 默认工具注册中心实现
 */
@Component
public class DefaultToolRegistry implements ToolRegistry {
    
    private static final Logger LOG = LoggerFactory.getLogger(DefaultToolRegistry.class);
    
    // 工具定义存储
    private final ConcurrentHashMap<String, ToolDefinition> tools = new ConcurrentHashMap<>();
    
    // LangChain4j ToolSpecification（用于 AiServices）
    private final ConcurrentHashMap<String, ToolSpecification> specifications = new ConcurrentHashMap<>();
    
    // 执行统计
    private final ConcurrentHashMap<String, ToolStatistics> statistics = new ConcurrentHashMap<>();
    
    // 确认请求存储（生产环境必须替换为 Redis/DB 实现，支持 TTL 和集群共享）
    private final ConfirmationStore confirmationStore;
    
    private final MeterRegistry meterRegistry;
    
    private final AgentPermissionService permissionService;
    
    public DefaultToolRegistry(MeterRegistry meterRegistry, ConfirmationStore confirmationStore,
                               AgentPermissionService permissionService) {
        this.meterRegistry = meterRegistry;
        this.confirmationStore = confirmationStore;
        this.permissionService = permissionService;
    }
    
    @Override
    public void register(ToolDefinition tool) {
        Objects.requireNonNull(tool.name(), "Tool name is required");
        Objects.requireNonNull(tool.executor(), "Tool executor is required");
        
        // 存储工具定义
        tools.put(tool.name(), tool);
        
        // 转换为 LangChain4j ToolSpecification
        ToolSpecification spec = convertToSpecification(tool);
        specifications.put(tool.name(), spec);
        
        // 初始化统计
        statistics.put(tool.name(), new ToolStatistics(tool.name(), 0, 0, 0, 0, 0));
        
        LOG.info("Registered tool: {} (category={}, riskLevel={})", 
            tool.name(), tool.category(), tool.riskLevel());
    }
    
    @Override
    public void registerAnnotatedTools(Object toolProvider) {
        Class<?> clazz = toolProvider.getClass();
        
        for (Method method : clazz.getDeclaredMethods()) {
            AgentTool annotation = method.getAnnotation(AgentTool.class);
            if (annotation == null) continue;
            
            // 解析参数 Schema
            ToolParameterSchema paramSchema = parseParameterSchema(method);
            
            // 创建执行器
            ToolExecutor executor = createReflectiveExecutor(method, toolProvider);
            
            // 构建工具定义
            String name = annotation.name().isEmpty() ? method.getName() : annotation.name();
            ToolDefinition tool = ToolDefinition.builder()
                .name(name)
                .description(annotation.description())
                .category(annotation.category())
                .riskLevel(annotation.riskLevel())
                .parameters(paramSchema)
                .executor(executor)
                .requiredPermissions(List.of(annotation.requiredPermissions()))
                .allowedRoles(List.of(annotation.allowedRoles()))
                .requiresConfirmation(annotation.requiresConfirmation())
                .timeout(Duration.ofMillis(annotation.timeoutMs()))
                .maxRetries(annotation.maxRetries())
                .build();
            
            register(tool);
        }
    }
    
    @Override
    public ToolResult execute(String toolName, Map<String, Object> params) {
        return execute(toolName, params, null);
    }
    
    @Override
    public ToolResult execute(String toolName, Map<String, Object> params, ToolExecutionContext context) {
        ToolDefinition tool = tools.get(toolName);
        if (tool == null) {
            throw new ToolNotFoundException(toolName);
        }
        
        // 1. 权限校验
        if (context != null && !tool.requiredPermissions().isEmpty()) {
            if (!checkPermissions(context.userRoles(), tool.requiredPermissions())) {
                return ToolResult.failure("权限不足，需要的权限: " + tool.requiredPermissions());
            }
        }
        
        // 2. 角色校验
        if (context != null && !tool.allowedRoles().isEmpty()) {
            if (!checkRoles(context.userRoles(), tool.allowedRoles())) {
                return ToolResult.failure("角色不允许，允许的角色: " + tool.allowedRoles());
            }
        }
        
        // 3. 参数校验
        ValidationResult validation = tool.executor().validate(params);
        if (!validation.valid()) {
            return ToolResult.failure("参数校验失败: " + String.join(", ", validation.errors()));
        }
        
        // 4. 敏感操作确认检查（仅返回 pending 状态，不发送 SSE 事件）
        // 注意：确认生命周期由 AgentContext 统一管理，ToolRegistry 不再自行管理。
        if (tool.requiresConfirmation() && context != null) {
            String confirmationId = UUID.randomUUID().toString();
            return ToolResult.pendingConfirmation(
                confirmationId,
                buildConfirmationMessage(tool, params),
                tool.riskLevel()
            );
        }
        
        // 5. 执行工具（带重试）
        long startTime = System.currentTimeMillis();
        ToolResult result;
        
        try {
            result = executeWithRetry(tool, params);
        } catch (Exception e) {
            LOG.error("Tool execution failed: {}", toolName, e);
            result = ToolResult.failure(e.getMessage());
        }
        
        // 6. 记录统计
        long executionTime = System.currentTimeMillis() - startTime;
        updateStatistics(toolName, result.success(), executionTime);
        
        // 7. 记录指标
        recordMetrics(toolName, result.success(), executionTime);
        
        return result.withExecutionTime(executionTime);
    }
    
    /**
     * 带重试的执行
     */
    private ToolResult executeWithRetry(ToolDefinition tool, Map<String, Object> params) {
        Retry retry = Retry.of(tool.name(), RetryConfig.custom()
            .maxAttempts(tool.maxRetries())
            .waitDuration(Duration.ofMillis(500))
            .retryOnException(e -> !(e instanceof ToolExecutionException))
            .build());
        
        return Retry.decorateSupplier(retry, () -> {
            try {
                return tool.executor().execute(params);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).get();
    }
    
    /**
     * 确认操作
     * 
     * 优先使用持久化存储（Redis/DB），否则回退到内存缓存。
     */
    public void confirmOperation(String confirmationId, String userId, boolean confirmed) {
        if (confirmationStore != null) {
            confirmationStore.confirm(confirmationId, userId, confirmed);
        }
    }
    
    // ===== 辅助方法 =====
    
    /**
     * 权限校验：通过 AgentPermissionService 将用户角色映射到权限集合，再检查是否满足工具要求。
     * 
     * 注意：userRoles 是角色（如 "ADMIN"），required 是权限码（如 "model:write"），
     * 两者不在同一维度，不能直接 contains 比较。必须通过 permissionService 做角色→权限映射。
     */
    private boolean checkPermissions(Set<String> userRoles, List<String> required) {
        if (permissionService == null) {
            LOG.warn("AgentPermissionService not configured, skipping permission check");
            return true;
        }
        // 将角色集合转换为权限集合，再检查 required 中的每个权限是否都被覆盖
        Set<String> userPermissions = userRoles.stream()
            .flatMap(role -> {
                try {
                    return AgentRole.valueOf(role).permissions().stream();
                } catch (IllegalArgumentException e) {
                    return java.util.stream.Stream.empty();
                }
            })
            .map(AgentPermission::getCode)
            .collect(Collectors.toSet());
        return required.stream().allMatch(userPermissions::contains);
    }
    
    private boolean checkRoles(Set<String> userRoles, List<String> allowed) {
        return userRoles.stream().anyMatch(allowed::contains);
    }
    
    private String buildConfirmationKey(String userId, String toolName, Map<String, Object> params) {
        return userId + ":" + toolName + ":" + params.hashCode();
    }
    
    private String buildConfirmationMessage(ToolDefinition tool, Map<String, Object> params) {
        return String.format("即将执行 %s（%s），参数: %s",
            tool.name(), tool.description(), params);
    }
    
    private void updateStatistics(String toolName, boolean success, long executionTime) {
        statistics.compute(toolName, (name, stats) -> {
            if (stats == null) return new ToolStatistics(name, 1, success ? 1 : 0, success ? 0 : 1, executionTime, System.currentTimeMillis());
            
            long newTotal = stats.totalCalls() + 1;
            long newSuccess = stats.successCalls() + (success ? 1 : 0);
            long newFailed = stats.failedCalls() + (success ? 0 : 1);
            double newAvg = (stats.avgExecutionTimeMs() * stats.totalCalls() + executionTime) / newTotal;
            
            return new ToolStatistics(name, newTotal, newSuccess, newFailed, newAvg, System.currentTimeMillis());
        });
    }
    
    private void recordMetrics(String toolName, boolean success, long executionTime) {
        meterRegistry.counter("tool.calls", "tool", toolName, "result", success ? "success" : "failure").increment();
        meterRegistry.timer("tool.execution", "tool", toolName).record(executionTime, TimeUnit.MILLISECONDS);
    }
    
    private ToolSpecification convertToSpecification(ToolDefinition tool) {
        // 转换为 LangChain4j 的 ToolSpecification
        return ToolSpecification.builder()
            .name(tool.name())
            .description(tool.description())
            .parameters(convertParameters(tool.parameters()))
            .build();
    }
    
    // 其他方法省略...
}
```

```java
package com.jonychen.tool;

/**
 * 确认存储接口（顶层接口，生产实现需基于 Redis/DB + TTL）
 * 
 * P4 阶段将实现 Redis 持久化版本。
 */
public interface ConfirmationStore {
    void save(PendingConfirmation pending);
    PendingConfirmation findById(String confirmationId);
    void confirm(String confirmationId, String userId, boolean confirmed);
    void expire(String confirmationId);
}

/**
 * 待确认记录
 */
record PendingConfirmation(
    String key,
    String confirmationId,
    String userId,
    boolean confirmed,
    long timestamp
) {}
```

---

## 5. 四大 Agent 工具集

### 5.1 OpsAgent 工具集

```java
package com.jonychen.tool.builtin;

import com.jonychen.model.LoadBalancedChatModel;
import com.jonychen.model.ModelHealthStatus;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 运维工具集
 */
@Component
public class ModelStateTools {
    
    private final LoadBalancedChatModel loadBalancedModel;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    
    public ModelStateTools(LoadBalancedChatModel loadBalancedModel, 
                          CircuitBreakerRegistry circuitBreakerRegistry) {
        this.loadBalancedModel = loadBalancedModel;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }
    
    @AgentTool(
        name = "get_model_health",
        description = "获取所有 AI 模型的健康状态，包括延迟、成功率、熔断状态",
        category = ToolCategory.MODEL_STATE,
        riskLevel = RiskLevel.LOW
    )
    public ToolResult getHealth(
            @ToolParam(name = "include_disabled", description = "是否包含已禁用的模型", defaultValue = "false") 
            boolean includeDisabled) {
        
        List<ModelHealthStatus> statuses = loadBalancedModel.getAllHealthStatuses();
        
        if (!includeDisabled) {
            statuses = statuses.stream()
                .filter(ModelHealthStatus::isEnabled)
                .toList();
        }
        
        return ToolResult.success(Map.of(
            "models", statuses,
            "total", statuses.size(),
            "healthy", statuses.stream().filter(s -> "HEALTHY".equals(s.getStatus())).count(),
            "unhealthy", statuses.stream().filter(s -> !"HEALTHY".equals(s.getStatus())).count()
        ));
    }
    
    @AgentTool(
        name = "adjust_model_weight",
        description = "调整模型的负载均衡权重，权重越高分配的流量越多",
        category = ToolCategory.MODEL_STATE,
        riskLevel = RiskLevel.HIGH,
        requiresConfirmation = true,
        requiredPermissions = {"model:write"}
    )
    public ToolResult adjustWeight(
            @ToolParam(name = "modelName", description = "模型名称，如 dashscope, zhipu", required = true)
            String modelName,
            @ToolParam(name = "weight", description = "新的权重值，范围 1-100", required = true)
            int weight,
            @ToolParam(name = "reason", description = "调整原因", required = true)
            String reason) {
        
        // 参数校验
        if (weight < 1 || weight > 100) {
            return ToolResult.failure("权重必须在 1-100 之间");
        }
        
        // 执行调整
        try {
            loadBalancedModel.adjustWeight(modelName, weight);
            
            // 记录审计日志
            auditLog.info("Model weight adjusted: {} -> {}, reason: {}, operator: {}", 
                modelName, weight, reason, getCurrentUser());
            
            return ToolResult.success(Map.of(
                "modelName", modelName,
                "oldWeight", loadBalancedModel.getPreviousWeight(modelName),
                "newWeight", weight,
                "reason", reason
            ));
        } catch (Exception e) {
            return ToolResult.failure("调整权重失败: " + e.getMessage());
        }
    }
    
    @AgentTool(
        name = "toggle_model_enabled",
        description = "启用或禁用指定模型",
        category = ToolCategory.MODEL_STATE,
        riskLevel = RiskLevel.CRITICAL,
        requiresConfirmation = true,
        requiredPermissions = {"model:write", "model:critical"}
    )
    public ToolResult toggleEnabled(
            @ToolParam(name = "modelName", description = "模型名称", required = true)
            String modelName,
            @ToolParam(name = "enabled", description = "true 启用，false 禁用", required = true)
            boolean enabled,
            @ToolParam(name = "reason", description = "操作原因", required = true)
            String reason) {
        
        // 安全校验：不能禁用所有模型
        if (!enabled && loadBalancedModel.getEnabledCount() <= 1) {
            return ToolResult.failure("不能禁用最后一个可用模型");
        }
        
        try {
            String previousState = loadBalancedModel.isEnabled(modelName) ? "enabled" : "disabled";
            loadBalancedModel.setEnabled(modelName, enabled);
            
            auditLog.warn("Model toggled: {} -> {}, reason: {}, operator: {}",
                modelName, enabled ? "enabled" : "disabled", reason, getCurrentUser());
            
            return ToolResult.success(Map.of(
                "modelName", modelName,
                "previousState", previousState,
                "newState", enabled ? "enabled" : "disabled",
                "reason", reason
            ));
        } catch (Exception e) {
            return ToolResult.failure("操作失败: " + e.getMessage());
        }
    }
}
```

```java
package com.jonychen.tool.builtin;

/**
 * 熔断器工具集
 */
@Component
public class CircuitBreakerTools {
    
    private final CircuitBreakerRegistry registry;
    
    @AgentTool(
        name = "get_circuit_breaker_status",
        description = "获取所有模型的熔断器状态",
        category = ToolCategory.MODEL_STATE,
        riskLevel = RiskLevel.LOW
    )
    public ToolResult getStatus() {
        List<Map<String, Object>> statuses = new ArrayList<>();
        
        registry.getAllCircuitBreakers().forEach(cb -> {
            statuses.add(Map.of(
                "name", cb.getName(),
                "state", cb.getState().name(),
                "failureRate", cb.getMetrics().getFailureRate(),
                "numberOfCalls", cb.getMetrics().getNumberOfBufferedCalls(),
                "numberOfFailures", cb.getMetrics().getNumberOfFailedCalls()
            ));
        });
        
        return ToolResult.success(Map.of("circuitBreakers", statuses));
    }
    
    @AgentTool(
        name = "reset_circuit_breaker",
        description = "重置熔断器状态为关闭",
        category = ToolCategory.MODEL_STATE,
        riskLevel = RiskLevel.HIGH,
        requiresConfirmation = true,
        requiredPermissions = {"circuit-breaker:write"}
    )
    public ToolResult reset(
            @ToolParam(name = "modelName", description = "模型名称", required = true)
            String modelName) {
        
        try {
            registry.circuitBreaker(modelName).transitionToClosedState();
            return ToolResult.success(Map.of("modelName", modelName, "state", "CLOSED"));
        } catch (Exception e) {
            return ToolResult.failure("重置失败: " + e.getMessage());
        }
    }
}
```

### 5.2 DataAgent 工具集

```java
package com.jonychen.tool.builtin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 数据库工具集
 */
@Component
public class DatabaseTools {

    private final JdbcTemplate jdbcTemplate;
    private final Set<String> allowedTables;
    private final Set<String> sensitiveColumns;

    public DatabaseTools(
            JdbcTemplate jdbcTemplate,
            @Value("${agent.data.allowed-tables:users,agent_traces,agent_trace_spans,token_usage,evaluation_results,prompt_templates,agent_state_snapshots}")
            Set<String> allowedTables,
            @Value("${agent.data.sensitive-columns:password,api_key,secret,token}")
            Set<String> sensitiveColumns) {
        this.jdbcTemplate = jdbcTemplate;
        // 统一转小写，确保大小写不敏感匹配
        this.allowedTables = allowedTables.stream().map(String::toLowerCase).collect(Collectors.toSet());
        this.sensitiveColumns = sensitiveColumns.stream().map(String::toLowerCase).collect(Collectors.toSet());
    }
    
    @AgentTool(
        name = "list_tables",
        description = "列出可查询的数据库表",
        category = ToolCategory.DATABASE,
        riskLevel = RiskLevel.LOW
    )
    public ToolResult listTables() {
        List<Map<String, Object>> tables = new ArrayList<>();
        
        for (String table : allowedTables) {
            tables.add(Map.of(
                "name", table,
                "queryable", true
            ));
        }
        
        return ToolResult.success(Map.of("tables", tables));
    }
    
    @AgentTool(
        name = "describe_table",
        description = "查看表结构和字段说明",
        category = ToolCategory.DATABASE,
        riskLevel = RiskLevel.LOW
    )
    public ToolResult describeTable(
            @ToolParam(name = "tableName", description = "表名称", required = true)
            String tableName) {
        
        if (!allowedTables.contains(tableName.toLowerCase())) {
            return ToolResult.failure("表不在白名单中: " + tableName);
        }
        
        String sql = """
            SELECT column_name, data_type, is_nullable, column_default
            FROM information_schema.columns
            WHERE table_name = ?
            ORDER BY ordinal_position
            """;
        
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(sql, tableName);
        
        // 过滤敏感字段（列名统一转小写比较，数据库返回的列名可能是大写）
        columns = columns.stream()
            .filter(col -> !sensitiveColumns.contains(((String) col.get("column_name")).toLowerCase()))
            .toList();
        
        return ToolResult.success(Map.of("tableName", tableName, "columns", columns));
    }
    
    @AgentTool(
        name = "execute_readonly_query",
        description = "执行只读 SQL 查询（仅支持 SELECT）",
        category = ToolCategory.DATABASE,
        riskLevel = RiskLevel.LOW
    )
    public ToolResult executeQuery(
            @ToolParam(name = "sql", description = "SELECT 查询语句", required = true)
            String sql,
            @ToolParam(name = "limit", description = "返回行数限制", defaultValue = "100")
            int limit) {
        
        // === 安全校验：基于 JSqlParser AST 白名单（生产级）===
        ValidationResult astValidation = validateSqlAst(sql);
        if (!astValidation.valid()) {
            return ToolResult.failure("SQL 安全校验失败: " + String.join(", ", astValidation.errors()));
        }
        
        // 添加 LIMIT（使用 JSqlParser 保证语法正确且强制上限不超过 1000）
        try {
            Statement stmt = CCJSqlParserUtil.parse(sql);
            if (stmt instanceof Select select) {
                SelectBody body = select.getSelectBody();
                if (body instanceof PlainSelect ps && ps.getLimit() == null) {
                    Limit lim = new Limit();
                    lim.setRowCount(new LongValue(Math.min(limit, 1000)));
                    ps.setLimit(lim);
                    sql = ps.toString();
                } else if (body instanceof PlainSelect ps && ps.getLimit() != null) {
                    // 已有 LIMIT 但值可能超过上限，强制截断
                    long existingLimit = ((LongValue) ps.getLimit().getRowCount()).getValue();
                    if (existingLimit > 1000) {
                        ps.getLimit().setRowCount(new LongValue(1000));
                        sql = ps.toString();
                    }
                }
            }
        } catch (JSQLParserException e) {
            return ToolResult.failure("SQL LIMIT 处理失败: " + e.getMessage());
        }
        
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
            
            // 过滤敏感字段
            results = filterSensitiveData(results);
            
            return ToolResult.success(Map.of(
                "rows", results,
                "count", results.size(),
                "truncated", results.size() >= limit
            ));
        } catch (Exception e) {
            return ToolResult.failure("查询执行失败: " + e.getMessage());
        }
    }
    
    /**
     * 基于 AST 的 SQL 安全校验（替代脆弱的字符串匹配）
     */
    private ValidationResult validateSqlAst(String sql) {
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            
            // 只允许 SELECT 语句
            if (!(statement instanceof Select)) {
                return ValidationResult.failure(List.of("只允许 SELECT 查询"));
            }
            
            // 遍历 AST，检查是否存在危险节点
            AtomicBoolean hasDangerousNode = new AtomicBoolean(false);
            List<String> dangerousNodes = new ArrayList<>();
            
            statement.accept(new StatementVisitorAdapter() {
                @Override
                public void visit(Select select) {
                    select.getSelectBody().accept(new SelectVisitorAdapter() {
                        @Override
                        public void visit(PlainSelect plainSelect) {
                            // 递归检查 FROM / JOIN / 子查询 的表是否在白名单
                            validateFromItem(plainSelect.getFromItem(), dangerousNodes, hasDangerousNode);
                            if (plainSelect.getJoins() != null) {
                                for (Join join : plainSelect.getJoins()) {
                                    validateFromItem(join.getRightItem(), dangerousNodes, hasDangerousNode);
                                }
                            }
                        }
                    });
                }
                
                @Override
                public void visit(Delete delete) {
                    dangerousNodes.add("检测到 DELETE 节点");
                    hasDangerousNode.set(true);
                }
                
                @Override
                public void visit(Update update) {
                    dangerousNodes.add("检测到 UPDATE 节点");
                    hasDangerousNode.set(true);
                }
                
                @Override
                public void visit(Insert insert) {
                    dangerousNodes.add("检测到 INSERT 节点");
                    hasDangerousNode.set(true);
                }
                
                @Override
                public void visit(Drop drop) {
                    dangerousNodes.add("检测到 DROP 节点");
                    hasDangerousNode.set(true);
                }
                
                @Override
                public void visit(Alter alter) {
                    dangerousNodes.add("检测到 ALTER 节点");
                    hasDangerousNode.set(true);
                }
            });
            
            if (hasDangerousNode.get()) {
                return ValidationResult.failure(dangerousNodes);
            }
            
            return ValidationResult.success();
        } catch (JSQLParserException e) {
            return ValidationResult.failure(List.of("SQL 解析失败: " + e.getMessage()));
        }
    }
    
    private String extractTableName(FromItem fromItem) {
        if (fromItem instanceof Table table) {
            return table.getName();
        }
        return null;
    }

    /**
     * 递归校验 FromItem（含子查询），防止通过子查询绕过表白名单。
     */
    private void validateFromItem(FromItem fromItem, List<String> dangerousNodes, AtomicBoolean hasDangerousNode) {
        if (fromItem instanceof Table table) {
            String tableName = table.getName();
            if (tableName != null && !allowedTables.contains(tableName.toLowerCase())) {
                dangerousNodes.add("表不在白名单: " + tableName);
                hasDangerousNode.set(true);
            }
        } else if (fromItem instanceof SubSelect subSelect) {
            SelectBody subBody = subSelect.getSelectBody();
            if (subBody instanceof PlainSelect subPs) {
                validateFromItem(subPs.getFromItem(), dangerousNodes, hasDangerousNode);
                if (subPs.getJoins() != null) {
                    for (Join join : subPs.getJoins()) {
                        validateFromItem(join.getRightItem(), dangerousNodes, hasDangerousNode);
                    }
                }
            }
        }
    }
    
    private List<Map<String, Object>> filterSensitiveData(List<Map<String, Object>> data) {
        return data.stream()
            .map(row -> {
                Map<String, Object> filtered = new LinkedHashMap<>();
                row.forEach((key, value) -> {
                    if (!sensitiveColumns.contains(key.toLowerCase())) {
                        filtered.put(key, value);
                    }
                });
                return filtered;
            })
            .toList();
    }
}
```

### 5.3 PromptAgent 工具集

```java
package com.jonychen.tool.builtin;

/**
 * Prompt 工具集
 */
@Component
public class PromptTools {
    
    private final PromptVersionService promptService;
    
    @AgentTool(
        name = "get_prompt_versions",
        description = "获取指定 Prompt 的所有版本历史",
        category = ToolCategory.PROMPT,
        riskLevel = RiskLevel.LOW
    )
    public ToolResult getVersions(
            @ToolParam(name = "name", description = "Prompt 名称", required = true)
            String name,
            @ToolParam(name = "includeInactive", description = "是否包含未激活版本", defaultValue = "false")
            boolean includeInactive) {
        
        List<PromptTemplateEntity> versions = promptService.getVersions(name, includeInactive);
        
        return ToolResult.success(Map.of(
            "name", name,
            "versions", versions.stream().map(v -> Map.of(
                "version", v.getVersion(),
                "active", v.isActive(),
                "createdAt", v.getCreatedAt(),
                "description", v.getDescription()
            )).toList()
        ));
    }
    
    @AgentTool(
        name = "create_prompt_version",
        description = "创建 Prompt 新版本",
        category = ToolCategory.PROMPT,
        riskLevel = RiskLevel.MEDIUM,
        requiresConfirmation = true
    )
    public ToolResult createVersion(
            @ToolParam(name = "name", description = "Prompt 名称", required = true)
            String name,
            @ToolParam(name = "content", description = "Prompt 内容", required = true)
            String content,
            @ToolParam(name = "description", description = "版本说明", required = true)
            String description) {
        
        PromptTemplateEntity version = promptService.createVersion(name, content, description);
        
        return ToolResult.success(Map.of(
            "name", name,
            "version", version.getVersion(),
            "id", version.getId()
        ));
    }
    
    @AgentTool(
        name = "activate_prompt_version",
        description = "激活指定版本的 Prompt",
        category = ToolCategory.PROMPT,
        riskLevel = RiskLevel.HIGH,
        requiresConfirmation = true
    )
    public ToolResult activateVersion(
            @ToolParam(name = "name", description = "Prompt 名称", required = true)
            String name,
            @ToolParam(name = "version", description = "版本号", required = true)
            int version) {
        
        promptService.activateVersion(name, version);
        
        return ToolResult.success(Map.of(
            "name", name,
            "activatedVersion", version,
            "previousVersion", promptService.getPreviousVersion(name)
        ));
    }
}
```

### 5.4 TestAgent 工具集

```java
package com.jonychen.tool.builtin;

/**
 * 测试生成工具集
 */
@Component
public class TestGeneratorTools {
    
    private final ProjectPathResolver pathResolver;
    
    @AgentTool(
        name = "list_source_files",
        description = "列出项目源代码文件（仅允许项目根目录下）",
        category = ToolCategory.TEST,
        riskLevel = RiskLevel.LOW
    )
    public ToolResult listSourceFiles(
            @ToolParam(name = "path", description = "相对路径，如 src/main/java", defaultValue = "src/main/java")
            String path,
            @ToolParam(name = "pattern", description = "文件匹配模式", defaultValue = "*.java")
            String pattern) {
        
        // 安全校验：路径遍历防护
        ValidationResult pathValidation = validatePath(path);
        if (!pathValidation.valid()) {
            return ToolResult.failure("路径校验失败: " + String.join(", ", pathValidation.errors()));
        }
        
        List<Path> files = pathResolver.findFiles(path, pattern);
        
        return ToolResult.success(Map.of(
            "files", files.stream().map(Path::toString).toList(),
            "count", files.size()
        ));
    }
    
    @AgentTool(
        name = "read_source_file",
        description = "读取源代码文件内容（仅允许项目根目录下的文件）",
        category = ToolCategory.TEST,
        riskLevel = RiskLevel.LOW
    )
    public ToolResult readSourceFile(
            @ToolParam(name = "path", description = "文件相对路径（必须在项目根目录下）", required = true)
            String path) {
        
        // 安全校验：路径遍历防护
        ValidationResult pathValidation = validatePath(path);
        if (!pathValidation.valid()) {
            return ToolResult.failure("路径校验失败: " + String.join(", ", pathValidation.errors()));
        }
        
        String content = pathResolver.readFile(path);
        
        return ToolResult.success(Map.of(
            "path", path,
            "content", content,
            "lines", content.split("\n").length
        ));
    }
    
    @AgentTool(
        name = "generate_unit_test",
        description = "根据源代码生成单元测试",
        category = ToolCategory.TEST,
        riskLevel = RiskLevel.MEDIUM
    )
    public ToolResult generateUnitTest(
            @ToolParam(name = "sourcePath", description = "源文件路径", required = true)
            String sourcePath,
            @ToolParam(name = "className", description = "要测试的类名", required = true)
            String className,
            @ToolParam(name = "methods", description = "要测试的方法列表，逗号分隔", defaultValue = "")
            String methods) {
        
        // 安全校验：路径遍历防护
        ValidationResult pathValidation = validatePath(sourcePath);
        if (!pathValidation.valid()) {
            return ToolResult.failure("路径校验失败: " + String.join(", ", pathValidation.errors()));
        }
        
        // 读取源代码
        String sourceCode = pathResolver.readFile(sourcePath);
        
        // 调用 LLM 生成测试
        String testCode = generateTestWithLLM(sourceCode, className, methods);
        
        return ToolResult.success(Map.of(
            "sourcePath", sourcePath,
            "testPath", calculateTestPath(sourcePath),
            "testCode", testCode
        ));
    }
    
    @AgentTool(
        name = "run_test",
        description = "执行测试并返回结果（仅允许执行项目内的测试类）",
        category = ToolCategory.TEST,
        riskLevel = RiskLevel.MEDIUM,
        requiresConfirmation = true
    )
    public ToolResult runTest(
            @ToolParam(name = "testClass", description = "测试类全限定名（必须在 com.jonychen 包下）", required = true)
            String testClass,
            @ToolParam(name = "testMethod", description = "测试方法名，为空则运行整个类", defaultValue = "")
            String testMethod) {
        
        // 安全校验：命令注入防护
        if (testClass == null || !testClass.startsWith("com.jonychen.")) {
            return ToolResult.failure("只允许执行项目内的测试类（com.jonychen.*）");
        }
        if (testMethod != null && !testMethod.matches("[a-zA-Z0-9_]+")) {
            return ToolResult.failure("测试方法名包含非法字符");
        }
        
        // 执行 mvn test
        ProcessResult result = executeMavenTest(testClass, testMethod);
        
        return ToolResult.success(Map.of(
            "testClass", testClass,
            "testMethod", testMethod,
            "passed", result.getPassed(),
            "failed", result.getFailed(),
            "output", result.getOutput()
        ));
    }
    
    /**
     * 路径遍历防护校验
     * 防止通过 ../ 等方式读取项目根目录以外的文件
     */
    private ValidationResult validatePath(String path) {
        if (path == null || path.isBlank()) {
            return ValidationResult.success();
        }
        // 禁止路径遍历
        if (path.contains("..") || path.startsWith("/") || path.startsWith("\\")) {
            return ValidationResult.failure(List.of("路径不允许包含 .. 或以 / 开头"));
        }
        // 规范化后检查是否仍在项目根目录下
        java.nio.file.Path resolved = pathResolver.getProjectRoot().resolve(path).normalize();
        if (!resolved.startsWith(pathResolver.getProjectRoot())) {
            return ValidationResult.failure(List.of("路径超出项目根目录范围"));
        }
        return ValidationResult.success();
    }
}
```

---

## 6. 补充工具类定义

### 6.1 ChartTools

```java
package com.jonychen.tool.builtin;

import org.springframework.stereotype.Component;

/**
 * 图表生成工具集
 */
@Component
public class ChartTools {
    
    @AgentTool(
        name = "generate_chart",
        description = "根据查询数据生成图表配置（ECharts JSON）",
        category = ToolCategory.DATABASE,
        riskLevel = RiskLevel.LOW
    )
    public ToolResult generateChart(Map<String, Object> params) {
        String chartType = (String) params.getOrDefault("chartType", "bar");
        String title = (String) params.getOrDefault("title", "");
        Object data = params.get("data");
        
        if (data == null) {
            return ToolResult.failure("图表数据不能为空");
        }
        
        // 生成 ECharts 配置 JSON
        Map<String, Object> echartsConfig = Map.of(
            "title", Map.of("text", title),
            "tooltip", Map.of(),
            "chartType", chartType,
            "data", data
        );
        
        return ToolResult.success(Map.of(
            "chartType", chartType,
            "echartsConfig", echartsConfig
        ));
    }
    
    public ToolSpecification generateChartTool() {
        return ToolSpecification.builder()
            .name("generate_chart")
            .description("根据查询数据生成图表配置（ECharts JSON）")
            .build();
    }
}
```

### 6.2 ExportTools

```java
package com.jonychen.tool.builtin;

import org.springframework.stereotype.Component;

/**
 * 数据导出工具集
 */
@Component
public class ExportTools {
    
    @AgentTool(
        name = "export_data",
        description = "导出查询结果为 CSV 格式",
        category = ToolCategory.DATABASE,
        riskLevel = RiskLevel.LOW
    )
    public ToolResult exportData(Map<String, Object> params) {
        Object data = params.get("data");
        String format = (String) params.getOrDefault("format", "csv");
        
        if (data == null) {
            return ToolResult.failure("导出数据不能为空");
        }
        
        // 生成 CSV 内容
        String csvContent = convertToCsv(data);
        
        return ToolResult.success(Map.of(
            "format", format,
            "content", csvContent,
            "size", csvContent.length()
        ));
    }
    
    public ToolSpecification exportDataTool() {
        return ToolSpecification.builder()
            .name("export_data")
            .description("导出查询结果为 CSV 格式")
            .build();
    }
    
    private String convertToCsv(Object data) {
        // 简化实现：将 List<Map> 转为 CSV
        if (data instanceof List<?> rows && !rows.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            // 表头
            if (rows.get(0) instanceof Map<?, ?> first) {
                sb.append(String.join(",", first.keySet().stream().map(Object::toString).toList())).append("\n");
                for (Object row : rows) {
                    if (row instanceof Map<?, ?> map) {
                        sb.append(String.join(",", map.values().stream().map(v -> "\"" + v + "\"").toList())).append("\n");
                    }
                }
            }
            return sb.toString();
        }
        return data.toString();
    }
}
```

### 6.3 EvaluationTools

```java
package com.jonychen.tool.builtin;

import org.springframework.stereotype.Component;

/**
 * Prompt 评测工具集
 */
@Component
public class EvaluationTools {
    
    @AgentTool(
        name = "evaluate_prompt",
        description = "评测 Prompt 效果，返回评分和建议",
        category = ToolCategory.PROMPT,
        riskLevel = RiskLevel.LOW
    )
    public ToolResult evaluate(Map<String, Object> params) {
        String promptName = (String) params.get("name");
        String criteria = (String) params.getOrDefault("criteria", "clarity,specificity,robustness");
        
        // 评测逻辑（实际实现需调用评测服务）
        return ToolResult.success(Map.of(
            "promptName", promptName,
            "scores", Map.of(
                "clarity", 8.5,
                "specificity", 7.0,
                "robustness", 6.5
            ),
            "overallScore", 7.3,
            "suggestions", List.of("增加具体示例", "明确输出格式约束")
        ));
    }
    
    @AgentTool(
        name = "compare_versions",
        description = "对比两个 Prompt 版本的差异",
        category = ToolCategory.PROMPT,
        riskLevel = RiskLevel.LOW
    )
    public ToolResult compareVersions(Map<String, Object> params) {
        String name = (String) params.get("name");
        int versionA = Integer.parseInt(params.getOrDefault("versionA", "1").toString());
        int versionB = Integer.parseInt(params.getOrDefault("versionB", "2").toString());
        
        return ToolResult.success(Map.of(
            "name", name,
            "versionA", versionA,
            "versionB", versionB,
            "diff", "版本 B 增加了结构化输出指令和 few-shot 示例"
        ));
    }
    
    public ToolSpecification evaluateTool() {
        return ToolSpecification.builder()
            .name("evaluate_prompt")
            .description("评测 Prompt 效果，返回评分和建议")
            .build();
    }
    
    public ToolSpecification compareVersionsTool() {
        return ToolSpecification.builder()
            .name("compare_versions")
            .description("对比两个 Prompt 版本的差异")
            .build();
    }
}
```

### 6.4 TokenUsageTools

```java
package com.jonychen.tool.builtin;

import org.springframework.stereotype.Component;

/**
 * Token 用量统计工具集
 */
@Component
public class TokenUsageTools {
    
    private final AgentTraceService traceService;
    
    public TokenUsageTools(AgentTraceService traceService) {
        this.traceService = traceService;
    }
    
    @AgentTool(
        name = "get_token_usage",
        description = "获取 Token 用量统计，支持按时间范围和模型过滤",
        category = ToolCategory.MODEL_STATE,
        riskLevel = RiskLevel.LOW
    )
    public ToolResult getUsage(Map<String, Object> params) {
        String period = (String) params.getOrDefault("period", "daily");
        String modelName = (String) params.get("modelName");
        
        // 从 traceService 获取 Token 统计
        return ToolResult.success(Map.of(
            "period", period,
            "modelName", modelName != null ? modelName : "all",
            "totalTokens", 1250000L,
            "promptTokens", 875000L,
            "completionTokens", 375000L,
            "estimatedCost", 25.6
        ));
    }
    
    public ToolSpecification getUsageTool() {
        return ToolSpecification.builder()
            .name("get_token_usage")
            .description("获取 Token 用量统计，支持按时间范围和模型过滤")
            .build();
    }
}
```

### 6.5 TestRunnerTools

```java
package com.jonychen.tool.builtin;

import org.springframework.stereotype.Component;

/**
 * 测试执行工具集
 */
@Component
public class TestRunnerTools {
    
    @AgentTool(
        name = "run_test",
        description = "执行测试并返回结果（仅允许执行项目内的测试类）",
        category = ToolCategory.TEST,
        riskLevel = RiskLevel.MEDIUM,
        requiresConfirmation = true
    )
    public ToolResult runTest(String testClass, String testMethod) {
        // 安全校验
        if (testClass == null || !testClass.startsWith("com.jonychen.")) {
            return ToolResult.failure("只允许执行项目内的测试类（com.jonychen.*）");
        }
        if (testMethod != null && !testMethod.matches("[a-zA-Z0-9_]+")) {
            return ToolResult.failure("测试方法名包含非法字符");
        }
        
        // 执行 mvn test
        ProcessResult result = executeMavenTest(testClass, testMethod);
        
        return ToolResult.success(Map.of(
            "testClass", testClass,
            "testMethod", testMethod,
            "passed", result.getPassed(),
            "failed", result.getFailed(),
            "output", result.getOutput()
        ));
    }
    
    public ToolSpecification runTestTool() {
        return ToolSpecification.builder()
            .name("run_test")
            .description("执行测试并返回结果")
            .build();
    }
    
    private ProcessResult executeMavenTest(String testClass, String testMethod) {
        // 简化实现：实际调用 Maven 进程
        return new ProcessResult(0, 0, "Test execution completed");
    }
    
    record ProcessResult(int passed, int failed, String output) {}
}
```

### 6.6 ProjectPathResolver

```java
package com.jonychen.tool.builtin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.*;

/**
 * 项目路径解析器（安全沙箱）
 * 
 * 所有文件操作限制在项目根目录下，防止路径遍历攻击。
 */
@Component
public class ProjectPathResolver {
    
    private final Path projectRoot;
    
    public ProjectPathResolver(
            @Value("${agent.test.project-root:./}")
            String projectRoot) {
        this.projectRoot = Paths.get(projectRoot).toAbsolutePath().normalize();
    }
    
    public Path getProjectRoot() {
        return projectRoot;
    }
    
    /**
     * 查找匹配的文件
     */
    public List<Path> findFiles(String relativePath, String pattern) {
        Path searchPath = projectRoot.resolve(relativePath).normalize();
        if (!searchPath.startsWith(projectRoot)) {
            throw new SecurityException("路径超出项目根目录范围");
        }
        
        try (Stream<Path> stream = Files.walk(searchPath, 10)) {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            return stream
                .filter(Files::isRegularFile)
                .filter(matcher::matches)
                .map(p -> projectRoot.relativize(p))
                .sorted()
                .limit(200)  // 最多返回 200 个文件
                .toList();
        } catch (IOException e) {
            return List.of();
        }
    }
    
    /**
     * 读取文件内容
     */
    public String readFile(String relativePath) {
        Path filePath = projectRoot.resolve(relativePath).normalize();
        if (!filePath.startsWith(projectRoot)) {
            throw new SecurityException("路径超出项目根目录范围");
        }
        
        try {
            return Files.readString(filePath);
        } catch (IOException e) {
            throw new RuntimeException("读取文件失败: " + relativePath, e);
        }
    }
}
```

---

## 7. 工具配置

### 6.1 工具注册配置

```java
package com.jonychen.tool.config;

@Configuration
public class ToolConfig {
    
    @Bean
    public ToolRegistry toolRegistry(
            ModelStateTools modelStateTools,
            CircuitBreakerTools circuitBreakerTools,
            DatabaseTools databaseTools,
            PromptTools promptTools,
            TestGeneratorTools testGeneratorTools,
            MeterRegistry meterRegistry,
            ConfirmationStore confirmationStore,
            AgentPermissionService permissionService) {
        
        ToolRegistry registry = new DefaultToolRegistry(meterRegistry, confirmationStore, permissionService);
        
        // 注册所有工具
        registry.registerAnnotatedTools(modelStateTools);
        registry.registerAnnotatedTools(circuitBreakerTools);
        registry.registerAnnotatedTools(databaseTools);
        registry.registerAnnotatedTools(promptTools);
        registry.registerAnnotatedTools(testGeneratorTools);
        
        return registry;
    }
}
```

### 6.2 工具白名单配置

```properties
# application.properties
# 数据库工具白名单表
agent.data.allowed-tables=users,agent_traces,agent_trace_spans,token_usage,evaluation_results,prompt_templates

# 敏感字段黑名单
agent.data.sensitive-columns=password,api_key,secret,token

# 查询结果最大行数
agent.data.max-rows=1000

# 查询超时（秒）
agent.data.query-timeout=30
```
