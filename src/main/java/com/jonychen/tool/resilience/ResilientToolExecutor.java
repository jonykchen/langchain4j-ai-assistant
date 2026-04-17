package com.jonychen.tool.resilience;

import com.jonychen.tool.*;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * 弹性工具执行器
 *
 * 提供：超时控制、重试机制、熔断保护、降级处理
 *
 * @author jonychen
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResilientToolExecutor {

    private final ToolRegistry toolRegistry;
    private final ToolExecutionConfigResolver configResolver;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final MeterRegistry meterRegistry;

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    /**
     * 存储工具配置的本地缓存
     */
    private final ConcurrentHashMap<String, ToolExecutionConfig> configCache = new ConcurrentHashMap<>();

    /**
     * 弹性执行工具（超时 + 重试 + 熔断 + 降级）
     *
     * @param toolName 工具名称
     * @param params   参数
     * @param sessionId 会话ID（可选）
     * @return 执行结果
     */
    public ToolResult execute(String toolName, Map<String, Object> params, String sessionId) {
        ToolDefinition tool = toolRegistry.getTool(toolName)
                .orElseThrow(() -> new ToolNotFoundException("Tool not found: " + toolName));

        ToolExecutionConfig config = configCache.computeIfAbsent(toolName,
                k -> configResolver.resolve(tool));

        return execute(tool, params, config);
    }

    /**
     * 使用指定配置执行工具
     */
    public ToolResult execute(String toolName, Map<String, Object> params, ToolExecutionConfig config) {
        ToolDefinition tool = toolRegistry.getTool(toolName)
                .orElseThrow(() -> new ToolNotFoundException("Tool not found: " + toolName));

        return execute(tool, params, config);
    }

    /**
     * 内部执行方法
     */
    private ToolResult execute(ToolDefinition tool, Map<String, Object> params, ToolExecutionConfig config) {
        // 获取或创建熔断器
        CircuitBreaker circuitBreaker = getOrCreateCircuitBreaker(tool.name(), config);

        // 记录开始时间
        long startTime = System.currentTimeMillis();

        try {
            // 构建执行链
            Supplier<ToolResult> supplier = () -> {
                try {
                    return executeWithTimeout(tool, params, config);
                } catch (TimeoutException e) {
                    throw new CompletionException(e);
                }
            };

            // 添加重试
            supplier = decorateWithRetry(supplier, tool.name(), config);

            // 添加熔断
            supplier = CircuitBreaker.decorateSupplier(circuitBreaker, supplier);

            // 执行
            ToolResult result = supplier.get();
            return result.withExecutionTime(System.currentTimeMillis() - startTime);

        } catch (CallNotPermittedException e) {
            // 熔断器打开，返回降级结果
            log.warn("Tool '{}' circuit breaker is open", tool.name());
            recordCircuitBreakerOpen(tool.name());
            return getFallbackResult(tool, config, "服务熔断中");

        } catch (CompletionException e) {
            if (e.getCause() instanceof TimeoutException) {
                // 执行超时
                log.warn("Tool '{}' execution timeout after {}ms", tool.name(), config.timeout().toMillis());
                recordTimeout(tool.name());
                return getFallbackResult(tool, config, "执行超时");
            }
            // 其他 CompletionException，按执行异常处理
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("Tool '{}' execution failed: {}", tool.name(), cause.getMessage(), cause);
            return ToolResult.failure("执行失败: " + cause.getMessage())
                    .withExecutionTime(System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            log.error("Tool '{}' execution error: {}", tool.name(), e.getMessage(), e);
            return ToolResult.failure("执行失败: " + e.getMessage())
                    .withExecutionTime(System.currentTimeMillis() - startTime);
        }
    }

    /**
     * 带超时控制的执行
     */
    private ToolResult executeWithTimeout(ToolDefinition tool, Map<String, Object> params,
                                           ToolExecutionConfig config) throws TimeoutException {
        Future<ToolResult> future = executorService.submit(() -> {
            try {
                return toolRegistry.execute(tool.name(), params);
            } catch (Exception e) {
                return ToolResult.failure(e.getMessage());
            }
        });

        try {
            return future.get(config.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.failure("执行被中断");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return ToolResult.failure(cause.getMessage());
        }
    }

    /**
     * 添加重试装饰
     */
    private Supplier<ToolResult> decorateWithRetry(Supplier<ToolResult> supplier,
                                                    String toolName,
                                                    ToolExecutionConfig config) {
        Retry retry = Retry.of("tool-retry-" + toolName,
                RetryConfig.<ToolResult>custom()
                        .maxAttempts(config.maxRetries())
                        .waitDuration(config.retryDelay())
                        .retryOnResult(this::shouldRetry)
                        .retryOnException(this::shouldRetryOnException)
                        .build());

        return Retry.decorateSupplier(retry, supplier);
    }

    /**
     * 获取或创建熔断器
     */
    private CircuitBreaker getOrCreateCircuitBreaker(String toolName, ToolExecutionConfig config) {
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold((float)(config.circuitBreakerThreshold() * 100))
                .slowCallRateThreshold(80)
                .slowCallDurationThreshold(Duration.ofSeconds(10))
                .waitDurationInOpenState(config.circuitBreakerWait())
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        return circuitBreakerRegistry.circuitBreaker("tool-" + toolName, cbConfig);
    }

    /**
     * 判断是否应该重试（基于结果）
     */
    private boolean shouldRetry(ToolResult result) {
        // 失败结果可以重试
        if (!result.success() && result.error() != null) {
            String error = result.error().toLowerCase();
            return error.contains("timeout") || error.contains("temporarily unavailable")
                    || error.contains("rate limit") || error.contains("connection refused");
        }
        return false;
    }

    /**
     * 判断异常是否应该重试
     */
    private boolean shouldRetryOnException(Throwable throwable) {
        return throwable instanceof TimeoutException
                || throwable instanceof java.net.SocketTimeoutException
                || throwable instanceof java.net.ConnectException
                || (throwable.getMessage() != null
                && throwable.getMessage().toLowerCase().contains("timeout"));
    }

    /**
     * 获取降级结果
     */
    private ToolResult getFallbackResult(ToolDefinition tool, ToolExecutionConfig config, String reason) {
        if (config.fallbackResult() != null) {
            return ToolResult.success(config.fallbackResult())
                    .withExecutionTime(0);
        }
        return ToolResult.failure(reason + "，工具: " + tool.name());
    }

    /**
     * 记录熔断器打开事件
     */
    private void recordCircuitBreakerOpen(String toolName) {
        meterRegistry.counter("tool.circuit_breaker.open", "tool", toolName).increment();
    }

    /**
     * 记录超时事件
     */
    private void recordTimeout(String toolName) {
        meterRegistry.counter("tool.timeout", "tool", toolName).increment();
    }

    /**
     * 获取熔断器状态
     */
    public CircuitBreakerStatus getCircuitBreakerStatus(String toolName) {
        try {
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("tool-" + toolName);
            return new CircuitBreakerStatus(
                    toolName,
                    cb.getState().name(),
                    cb.getMetrics().getFailureRate(),
                    cb.getMetrics().getNumberOfBufferedCalls(),
                    cb.getMetrics().getNumberOfFailedCalls()
            );
        } catch (Exception e) {
            return new CircuitBreakerStatus(toolName, "NOT_FOUND", 0, 0, 0);
        }
    }

    /**
     * 重置熔断器
     */
    public void resetCircuitBreaker(String toolName) {
        try {
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("tool-" + toolName);
            cb.reset();
            log.info("Circuit breaker for tool '{}' has been reset", toolName);
        } catch (Exception e) {
            log.warn("Failed to reset circuit breaker for tool '{}': {}", toolName, e.getMessage());
        }
    }

    /**
     * 熔断器状态
     */
    public record CircuitBreakerStatus(
            String toolName,
            String state,
            double failureRate,
            int bufferedCalls,
            int failedCalls
    ) {}
}