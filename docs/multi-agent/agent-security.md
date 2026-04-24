# Agent 安全校验设计

> 多 Agent 生产级系统技术方案 - 子文档

---

## 1. 安全威胁模型

### 1.1 威胁分类

| 威胁 | 风险等级 | 说明 |
|------|----------|------|
| 未授权访问 | 高 | 未登录用户调用 Agent |
| 权限提升 | 高 | 普通用户执行管理员操作 |
| 工具滥用 | 高 | Agent 调用敏感工具 |
| SQL 注入 | 高 | DataAgent 执行恶意 SQL |
| 命令注入 | 高 | TestAgent 执行恶意命令 |
| 数据泄露 | 中 | 工具返回敏感数据 |
| 资源耗尽 | 中 | Agent 无限循环消耗资源 |
| Prompt 注入 | 中 | 用户输入操纵 Agent 行为 |

### 1.2 安全边界

```
┌─────────────────────────────────────────────────────────┐
│                      用户请求                             │
└─────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│ 1. 身份认证 (JWT)                                        │
│    - 验证 Token 有效性                                   │
│    - 提取用户 ID 和角色                                  │
└─────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│ 2. 权限校验 (RBAC)                                       │
│    - 检查用户对 Agent 的访问权限                         │
│    - 检查用户对工具的执行权限                            │
└─────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│ 3. 输入校验                                              │
│    - 敏感词过滤                                          │
│    - 参数类型校验                                        │
│    - 长度限制                                            │
└─────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│ 4. 执行控制                                              │
│    - 超时限制                                            │
│    - 迭代次数限制                                        │
│    - Token 消耗限制                                      │
│    - 敏感操作人工确认                                    │
└─────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│ 5. 输出过滤                                              │
│    - 敏感数据脱敏                                        │
│    - SQL 结果过滤                                        │
└─────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│ 6. 审计日志                                              │
│    - 记录所有操作                                        │
│    - 记录敏感操作详情                                    │
│    - 记录操作时间、结果                                  │
└─────────────────────────────────────────────────────────┘
```

---

## 2. 权限模型

### 2.1 RBAC 权限设计

```java
package com.jonychen.agent.security;

/**
 * Agent 权限枚举
 */
public enum AgentPermission {
    // Agent 访问权限
    AGENT_OPS_EXECUTE("agent:ops:execute", "执行运维 Agent"),
    AGENT_DATA_EXECUTE("agent:data:execute", "执行数据 Agent"),
    AGENT_PROMPT_EXECUTE("agent:prompt:execute", "执行 Prompt Agent"),
    AGENT_TEST_EXECUTE("agent:test:execute", "执行测试 Agent"),
    AGENT_ROUTER_EXECUTE("agent:router:execute", "执行路由 Agent"),
    
    // 工具权限
    MODEL_READ("model:read", "读取模型状态"),
    MODEL_WRITE("model:write", "修改模型配置"),
    CIRCUIT_BREAKER_READ("circuit-breaker:read", "读取熔断器状态"),
    CIRCUIT_BREAKER_WRITE("circuit-breaker:write", "修改熔断器状态"),
    DATABASE_READ("database:read", "读取数据库"),
    DATABASE_WRITE("database:write", "写入数据库"),
    PROMPT_READ("prompt:read", "读取 Prompt"),
    PROMPT_WRITE("prompt:write", "修改 Prompt"),
    TEST_CREATE("test:create", "创建测试"),
    TEST_RUN("test:run", "运行测试"),
    CODE_READ("code:read", "读取源代码");
    
    private final String code;
    private final String description;
    
    AgentPermission(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public String getCode() { return code; }
    public String getDescription() { return description; }
}

/**
 * 角色定义
 */
public enum AgentRole {
    USER("USER", "普通用户", Set.of(
        AGENT_DATA_EXECUTE,
        AGENT_ROUTER_EXECUTE,
        DATABASE_READ
    )),
    
    ADMIN("ADMIN", "管理员", Set.of(
        // 包含 USER 所有权限
        AGENT_DATA_EXECUTE,
        AGENT_ROUTER_EXECUTE,
        DATABASE_READ,
        // ADMIN 专属权限
        AGENT_OPS_EXECUTE,
        AGENT_PROMPT_EXECUTE,
        AGENT_TEST_EXECUTE,
        MODEL_READ, MODEL_WRITE,
        CIRCUIT_BREAKER_READ, CIRCUIT_BREAKER_WRITE,
        DATABASE_WRITE,
        PROMPT_READ, PROMPT_WRITE,
        TEST_CREATE, TEST_RUN,
        CODE_READ
    )),
    
    SYSTEM("SYSTEM", "系统", Set.of(
        // 所有权限
    ));
    
    private final String code;
    private final String displayName;
    private final Set<AgentPermission> permissions;
    
    public boolean hasPermission(AgentPermission permission) {
        return permissions.contains(permission);
    }
}
```

### 2.2 权限检查服务

```java
package com.jonychen.agent.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

/**
 * Agent 权限服务
 */
@Service
public class AgentPermissionService {
    
    /**
     * 检查用户是否有 Agent 执行权限
     */
    public boolean canExecuteAgent(Authentication auth, String agentName) {
        AgentRole role = getRole(auth);
        AgentPermission required = getAgentExecutePermission(agentName);
        return role.hasPermission(required);
    }
    
    /**
     * 检查用户是否有工具执行权限
     */
    public boolean canExecuteTool(Authentication auth, ToolDefinition tool) {
        AgentRole role = getRole(auth);
        
        // 检查工具需要的所有权限
        for (String permission : tool.requiredPermissions()) {
            AgentPermission perm = findByCode(permission);
            if (perm == null) {
                LOG.warn("工具 {} 配置了非法权限字符串: {}", tool.name(), permission);
                return false; // 非法权限视为无权限，拒绝执行
            }
            if (!role.hasPermission(perm)) {
                return false;
            }
        }
        
        // 检查角色限制
        if (!tool.allowedRoles().isEmpty()) {
            return tool.allowedRoles().contains(role.getCode());
        }
        
        return true;
    }
    
    /**
     * 检查是否需要确认敏感操作
     * 
     * 注意：即使是 ADMIN，CRITICAL 级别操作也必须确认，防止误操作。
     */
    public boolean requiresConfirmation(Authentication auth, ToolDefinition tool) {
        AgentRole role = getRole(auth);
        
        // CRITICAL 级别强制确认（无任何角色豁免）
        if (tool.riskLevel() == RiskLevel.CRITICAL) {
            return true;
        }
        
        // HIGH 级别 ADMIN 可豁免，其他角色必须确认
        if (tool.riskLevel() == RiskLevel.HIGH) {
            return role != AgentRole.ADMIN;
        }
        
        // 显式标记为需要确认的工具
        return tool.requiresConfirmation();
    }
    
    private AgentRole getRole(Authentication auth) {
        return auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(a -> a.startsWith("ROLE_"))
            .map(a -> a.substring(5))
            .map(AgentRole::valueOf)
            .findFirst()
            .orElse(AgentRole.USER);
    }
    
    /**
     * 通过 code 字符串查找 AgentPermission（替代 valueOf，因为 enum 成员名不允许冒号）
     */
    private static final Map<String, AgentPermission> CODE_MAP = Arrays.stream(AgentPermission.values())
        .collect(Collectors.toMap(AgentPermission::getCode, p -> p));
    
    private AgentPermission findByCode(String code) {
        return CODE_MAP.get(code);
    }
    
    private AgentPermission getAgentExecutePermission(String agentName) {
        return switch (agentName.toLowerCase()) {
            case "ops" -> AGENT_OPS_EXECUTE;
            case "data" -> AGENT_DATA_EXECUTE;
            case "prompt" -> AGENT_PROMPT_EXECUTE;
            case "test" -> AGENT_TEST_EXECUTE;
            default -> AGENT_ROUTER_EXECUTE;
        };
    }
}
```

### 2.3 Spring Security 配置

```java
package com.jonychen.agent.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

@Configuration
@EnableWebSecurity
public class AgentSecurityConfig {
    
    @Bean
    public SecurityFilterChain agentSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/agent/**")
            .authorizeHttpRequests(auth -> auth
                // Agent 列表接口所有用户可访问
                .requestMatchers("/api/agent/list").authenticated()
                
                // Agent 执行需要认证
                .requestMatchers("/api/agent/execute").authenticated()
                .requestMatchers("/api/agent/confirm").authenticated()
                .requestMatchers("/api/agent/cancel/**").authenticated()
                .requestMatchers("/api/agent/history").authenticated()
                
                // Agent 管理接口需要 ADMIN
                .requestMatchers("/api/agent/admin/**").hasRole("ADMIN")
            )
            .addFilterBefore(new JwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
}
```

---

## 3. 输入校验

### 3.1 输入过滤器

```java
package com.jonychen.agent.security;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 输入过滤器
 */
@Component
public class AgentInputValidator {
    
    // 敏感词列表
    private static final Set<String> SENSITIVE_WORDS = Set.of(
        "password", "secret", "key", "token", "credential"
    );
    
    // SQL 注入检测正则（仅作为初筛，最终安全由 DatabaseTools 的 AST 校验保证）
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
        "(?i)(union\\s+.*select|insert\\s+.*into|delete\\s+.*from|drop\\s+table|exec\\s+\\()"
    );
    
    /**
     * 校验用户输入（通用层）
     * 
     * 注意：命令注入检测不再全局拦截，仅针对实际会触发进程执行的工具参数做校验。
     */
    public ValidationResult validate(String input) {
        // 长度检查
        if (input == null || input.isEmpty()) {
            return ValidationResult.failure("输入不能为空");
        }
        if (input.length() > 10000) {
            return ValidationResult.failure("输入长度不能超过 10000 字符");
        }
        
        // 敏感词检查（仅记录日志，不阻断正常业务词）
        for (String word : SENSITIVE_WORDS) {
            if (input.toLowerCase().contains(word)) {
                securityLog.warn("Input contains sensitive word: {}, userId: {}", word, getCurrentUserId());
            }
        }
        
        // SQL 注入初筛（最终由 DatabaseTools AST 校验兜底）
        if (SQL_INJECTION_PATTERN.matcher(input).find()) {
            securityLog.warn("Possible SQL injection detected: {}", input.substring(0, Math.min(100, input.length())));
            return ValidationResult.failure("输入包含不安全内容");
        }
        
        return ValidationResult.success();
    }
    
    /**
     * 校验工具参数
     */
    public ValidationResult validateToolParams(String toolName, Map<String, Object> params) {
        // 类型检查
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String str) {
                ValidationResult result = validate(str);
                if (!result.valid()) {
                    return result;
                }
            }
        }
        
        // 工具特定校验
        return switch (toolName) {
            case "adjust_model_weight" -> validateWeightParams(params);
            case "execute_readonly_query" -> validateSqlQuery((String) params.get("sql"));
            case "run_test" -> validateTestParams(params);
            default -> ValidationResult.success();
        };
    }
    
    private ValidationResult validateWeightParams(Map<String, Object> params) {
        Object weightObj = params.get("weight");
        if (weightObj instanceof Number weight) {
            int w = weight.intValue();
            if (w < 1 || w > 100) {
                return ValidationResult.failure("权重必须在 1-100 之间");
            }
        }
        return ValidationResult.success();
    }
    
    private ValidationResult validateSqlQuery(String sql) {
        if (sql == null || sql.isBlank()) {
            return ValidationResult.failure("SQL 不能为空");
        }
        
        // 只允许 SELECT
        String normalized = sql.trim().toUpperCase();
        if (!normalized.startsWith("SELECT")) {
            return ValidationResult.failure("只允许 SELECT 查询");
        }
        
        // 禁止的关键字
        Set<String> forbidden = Set.of("INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "CREATE", "TRUNCATE", "GRANT", "REVOKE");
        for (String keyword : forbidden) {
            if (normalized.contains(keyword)) {
                return ValidationResult.failure("SQL 包含禁止的操作: " + keyword);
            }
        }
        
        return ValidationResult.success();
    }
    
    private ValidationResult validateTestParams(Map<String, Object> params) {
        String testClass = (String) params.get("testClass");
        if (testClass == null || testClass.isBlank()) {
            return ValidationResult.failure("测试类名不能为空");
        }
        
        // 只允许特定包下的测试类
        if (!testClass.startsWith("com.jonychen.")) {
            return ValidationResult.failure("只允许执行项目内的测试类");
        }
        
        return ValidationResult.success();
    }
}
```

---

## 4. 输出过滤

### 4.1 敏感数据脱敏

```java
package com.jonychen.agent.security;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 输出过滤器
 */
@Component
public class AgentOutputFilter {
    
    // 敏感字段正则
    private static final Pattern API_KEY_PATTERN = Pattern.compile("(?i)(api[_-]?key|apikey)['\"]?\\s*[:=]\\s*['\"]?[a-zA-Z0-9_-]{20,}");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("(?i)(password|passwd|pwd)['\"]?\\s*[:=]\\s*['\"]?[^'\"\\s]+");
    private static final Pattern SECRET_PATTERN = Pattern.compile("(?i)(secret|token|credential)['\"]?\\s*[:=]\\s*['\"]?[a-zA-Z0-9_-]{10,}");
    
    /**
     * 过滤工具结果
     */
    public Object filterResult(Object result, ToolDefinition tool) {
        if (result == null) return null;
        
        // 如果结果是字符串，直接过滤
        if (result instanceof String str) {
            return filterSensitiveData(str);
        }
        
        // 如果结果是 Map，递归过滤
        if (result instanceof Map<?, ?> map) {
            return filterMap((Map<String, Object>) map);
        }
        
        // 如果结果是 List，递归过滤
        if (result instanceof List<?> list) {
            return list.stream()
                .map(item -> filterResult(item, tool))
                .toList();
        }
        
        return result;
    }
    
    /**
     * 过滤 Map 中的敏感数据
     */
    private Map<String, Object> filterMap(Map<String, Object> map) {
        Map<String, Object> filtered = new LinkedHashMap<>();
        
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            // 跳过敏感字段
            if (isSensitiveField(key)) {
                filtered.put(key, "***REDACTED***");
                continue;
            }
            
            // 递归过滤
            filtered.put(key, filterResult(value, null));
        }
        
        return filtered;
    }
    
    /**
     * 过滤字符串中的敏感数据
     */
    private String filterSensitiveData(String str) {
        if (str == null) return null;
        
        str = API_KEY_PATTERN.matcher(str).replaceAll("$1=***REDACTED***");
        str = PASSWORD_PATTERN.matcher(str).replaceAll("$1=***REDACTED***");
        str = SECRET_PATTERN.matcher(str).replaceAll("$1=***REDACTED***");
        
        return str;
    }
    
    /**
     * 判断是否为敏感字段（使用精确匹配，避免误杀业务字段如 keyword、sortKey）
     */
    private boolean isSensitiveField(String fieldName) {
        if (fieldName == null) return false;
        
        String lower = fieldName.toLowerCase();
        return lower.matches("^(.*_)?password$")
            || lower.matches("^(.*_)?secret$")
            || lower.matches("^(.*_)?api[_-]?key$")
            || lower.matches("^(.*_)?access[_-]?key$")
            || lower.matches("^(.*_)?auth[_-]?token$")
            || lower.matches("^(.*_)?credential(s)?$")
            || lower.equals("token") && !lower.contains("json"); // 保留简单 token 的脱敏，但允许 json_token 等复合词
    }
}
```

---

## 5. 执行控制

### 5.1 执行限制服务

```java
package com.jonychen.agent.security;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.*;

/**
 * 执行控制服务
 * 
 * 生产环境必须注入分布式配额提供者（如 RedisQuotaProvider），单机内存实现仅适用于开发环境。
 */
@Service
public class AgentExecutionControlService {
    
    // 默认限制
    private static final int DEFAULT_MAX_ITERATIONS = 10;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);
    private static final int DEFAULT_MAX_TOKENS = 100000;
    
    private final ExecutionQuotaProvider quotaProvider;
    
    public AgentExecutionControlService(ExecutionQuotaProvider quotaProvider) {
        this.quotaProvider = quotaProvider != null ? quotaProvider : new InMemoryQuotaProvider();
    }
    
    /**
     * 检查执行限制
     */
    public ExecutionLimitResult checkExecutionLimits(AgentRequest request, String userId) {
        // 1. 检查并发执行数
        int concurrentCount = quotaProvider.getConcurrentCount(userId);
        if (concurrentCount >= 3) {
            return ExecutionLimitResult.failure("同时最多执行 3 个 Agent 任务");
        }
        
        // 2. 检查 Token 消耗上限（每日 1M）
        long dailyTokens = quotaProvider.getDailyTokenUsage(userId);
        if (dailyTokens > 1_000_000) {
            return ExecutionLimitResult.failure("今日 Token 消耗已达上限");
        }
        
        // 3. 检查迭代次数
        AgentRequestOptions options = request.options();
        if (options.maxIterations() > 50) {
            return ExecutionLimitResult.failure("最大迭代次数不能超过 50");
        }
        
        // 4. 检查超时时间
        if (options.timeout().toMinutes() > 30) {
            return ExecutionLimitResult.failure("执行超时不能超过 30 分钟");
        }
        
        return ExecutionLimitResult.success(
            Math.min(options.maxIterations(), DEFAULT_MAX_ITERATIONS),
            options.timeout().compareTo(DEFAULT_TIMEOUT) > 0 ? DEFAULT_TIMEOUT : options.timeout(),
            DEFAULT_MAX_TOKENS
        );
    }
    
    /**
     * 开始执行
     */
    public void startExecution(String userId) {
        quotaProvider.incrementConcurrent(userId);
    }
    
    /**
     * 结束执行
     */
    public void endExecution(String userId, long tokensUsed) {
        quotaProvider.decrementConcurrent(userId);
        quotaProvider.addTokenUsage(userId, tokensUsed);
    }
    
    /**
     * 配额提供者接口（生产环境使用 Redis 实现）
     */
    public interface ExecutionQuotaProvider {
        int getConcurrentCount(String userId);
        void incrementConcurrent(String userId);
        void decrementConcurrent(String userId);
        long getDailyTokenUsage(String userId);
        void addTokenUsage(String userId, long tokens);
    }
    
    /**
     * 内存配额提供者（仅开发环境使用）
     */
    public static class InMemoryQuotaProvider implements ExecutionQuotaProvider {
        private final ConcurrentHashMap<String, AtomicInteger> userExecutionCount = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, AtomicLong> userTokenUsage = new ConcurrentHashMap<>();
        
        @Override public int getConcurrentCount(String userId) {
            return userExecutionCount.computeIfAbsent(userId, k -> new AtomicInteger(0)).get();
        }
        @Override public void incrementConcurrent(String userId) {
            userExecutionCount.computeIfAbsent(userId, k -> new AtomicInteger(0)).incrementAndGet();
        }
        @Override public void decrementConcurrent(String userId) {
            userExecutionCount.computeIfAbsent(userId, k -> new AtomicInteger(0)).decrementAndGet();
        }
        @Override public long getDailyTokenUsage(String userId) {
            return userTokenUsage.computeIfAbsent(userId, k -> new AtomicLong(0)).get();
        }
        @Override public void addTokenUsage(String userId, long tokens) {
            userTokenUsage.computeIfAbsent(userId, k -> new AtomicLong(0)).addAndGet(tokens);
        }
        
        @Scheduled(cron = "0 0 0 * * ?")
        public void dailyReset() {
            userTokenUsage.clear();
        }
    }
}

record ExecutionLimitResult(
    boolean allowed,
    String errorMessage,
    int maxIterations,
    Duration timeout,
    int maxTokens
) {
    static ExecutionLimitResult success(int maxIterations, Duration timeout, int maxTokens) {
        return new ExecutionLimitResult(true, null, maxIterations, timeout, maxTokens);
    }
    
    static ExecutionLimitResult failure(String message) {
        return new ExecutionLimitResult(false, message, 0, null, 0);
    }
}
```

---

## 6. 审计日志

### 6.1 审计日志实体

```java
package com.jonychen.agent.security;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Agent 审计日志
 */
@Entity
@Table(name = "agent_audit_logs", indexes = {
    @Index(name = "idx_user_id", columnList = "userId"),
    @Index(name = "idx_trace_id", columnList = "traceId"),
    @Index(name = "idx_timestamp", columnList = "timestamp")
})
public class AgentAuditLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false)
    private String traceId;
    
    @Column(nullable = false)
    private String userId;
    
    @Column(nullable = false)
    private String userName;
    
    @Enumerated(EnumType.STRING)
    private AgentRole role;
    
    @Column(nullable = false)
    private String action;  // EXECUTE_AGENT, CONFIRM_OPERATION, CANCEL_EXECUTION
    
    @Column(nullable = false)
    private String agentName;
    
    private String toolName;
    
    @Column(columnDefinition = "TEXT")
    private String input;
    
    @Column(columnDefinition = "TEXT")
    private String output;
    
    private boolean success;
    
    private String errorMessage;
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    private String ipAddress;
    
    private String userAgent;
    
    // Getters, Setters...
}
```

### 6.2 审计日志服务

```java
package com.jonychen.agent.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 审计日志服务
 * 
 * 注意：写库操作使用 @Async 异步执行，避免在 Agent 执行热路径上阻塞。
 * 如需确保审计日志完整性，可替换为消息队列（如 RabbitMQ/Kafka）写入。
 */
@Service
public class AgentAuditService {

    private final AgentAuditLogRepository repository;

    /**
     * 记录 Agent 执行（异步写库，避免阻塞执行热路径）
     *
     * 注意：IP / UA 由调用方（如 AgentExecutionController）同步提取后传入，
     * 避免异步审计线程依赖 HttpServletRequest（请求结束后该对象会被回收）。
     */
    @Async
    public void logExecution(String traceId, String userId, String userName, AgentRole role,
                            String agentName, String input,
                            String clientIp, String userAgent) {
        AgentAuditLog log = new AgentAuditLog();
        log.setTraceId(traceId);
        log.setUserId(userId);
        log.setUserName(userName);
        log.setRole(role);
        log.setAction("EXECUTE_AGENT");
        log.setAgentName(agentName);
        log.setInput(truncate(input, 2000));
        log.setTimestamp(LocalDateTime.now());
        log.setIpAddress(clientIp);
        log.setUserAgent(userAgent);

        repository.save(log);
    }

    /**
     * 记录敏感操作确认（异步写库）
     */
    @Async
    public void logConfirmation(String traceId, String userId, String toolName,
                               Map<String, Object> params, boolean approved) {
        AgentAuditLog log = new AgentAuditLog();
        log.setTraceId(traceId);
        log.setUserId(userId);
        log.setAction("CONFIRM_OPERATION");
        log.setToolName(toolName);
        log.setInput(serializeParams(params));
        log.setSuccess(approved);
        log.setTimestamp(LocalDateTime.now());

        repository.save(log);
    }

    /**
     * 记录执行结果（异步写库）
     *
     * 通过 traceId + action = EXECUTE_AGENT 精准定位执行记录，防止同一 traceId
     * 下存在多条记录（如确认记录）时更新到错误的行。
     */
    @Async
    public void logResult(String traceId, boolean success, String output, String errorMessage) {
        AgentAuditLog log = repository.findFirstByTraceIdAndActionOrderByTimestampDesc(
            traceId, "EXECUTE_AGENT"
        );
        if (log != null) {
            log.setSuccess(success);
            log.setOutput(truncate(output, 4000));
            log.setErrorMessage(errorMessage != null ? truncate(errorMessage, 1000) : null);
            repository.save(log);
        }
    }
    
    private String truncate(String s, int maxLen) {
        return s != null && s.length() > maxLen ? s.substring(0, maxLen) : s;
    }
    
    private String serializeParams(Map<String, Object> params) {
        try {
            return truncate(new ObjectMapper().writeValueAsString(params), 2000);
        } catch (Exception e) {
            return "{}";
        }
    }
}
```

### 6.3 审计日志查询接口

```java
package com.jonychen.admin.controller;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/agent/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AgentAuditController {
    
    private final AgentAuditService auditService;
    
    @GetMapping("/logs")
    public ApiResponse<Page<AgentAuditLog>> getLogs(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String agentName,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Page<AgentAuditLog> logs = auditService.queryLogs(userId, agentName, action, startTime, endTime, page, size);
        return ApiResponse.success(logs);
    }
    
    @GetMapping("/stats")
    public ApiResponse<AgentAuditStats> getStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        
        return ApiResponse.success(auditService.getStats(startTime, endTime));
    }
    
    @GetMapping("/export")
    public void exportLogs(HttpServletResponse response,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) throws IOException {
        
        // 导出 CSV
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=agent-audit-logs.csv");
        
        List<AgentAuditLog> logs = auditService.queryAll(startTime, endTime);
        
        PrintWriter writer = response.getWriter();
        writer.println("ID,时间,用户,角色,操作,Agent,工具,成功,错误信息");
        
        for (AgentAuditLog log : logs) {
            writer.printf("%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                log.getId(), log.getTimestamp(), log.getUserName(), log.getRole(),
                log.getAction(), log.getAgentName(), log.getToolName(),
                log.isSuccess(), log.getErrorMessage());
        }
    }
}
```

---

## 7. 数据库表结构

```sql
-- Agent 审计日志表
CREATE TABLE agent_audit_logs (
    id VARCHAR(36) PRIMARY KEY,
    trace_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    user_name VARCHAR(100),
    role VARCHAR(20) NOT NULL,
    action VARCHAR(50) NOT NULL,
    agent_name VARCHAR(50),
    tool_name VARCHAR(100),
    input TEXT,
    output TEXT,
    success BOOLEAN,
    error_message VARCHAR(1000),
    timestamp TIMESTAMP NOT NULL,
    ip_address VARCHAR(50),
    user_agent VARCHAR(500)
);

CREATE INDEX idx_user_id ON agent_audit_logs(user_id);
CREATE INDEX idx_trace_id ON agent_audit_logs(trace_id);
CREATE INDEX idx_timestamp ON agent_audit_logs(timestamp);
CREATE INDEX idx_agent_name ON agent_audit_logs(agent_name);
CREATE INDEX idx_action ON agent_audit_logs(action);

-- Comment
COMMENT ON TABLE agent_audit_logs IS 'Agent 执行审计日志';
COMMENT ON COLUMN agent_audit_logs.action IS '操作类型：EXECUTE_AGENT, CONFIRM_OPERATION, CANCEL_EXECUTION';
```
