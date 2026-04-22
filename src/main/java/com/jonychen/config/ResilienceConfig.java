package com.jonychen.config;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;

/**
 * Resilience4j 配置类
 *
 * <p>配置事件监听器，用于监控限流、熔断、重试等事件
 */
@Configuration
public class ResilienceConfig {

    private static final Logger LOG = LoggerFactory.getLogger(ResilienceConfig.class);

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;

    public ResilienceConfig(
            CircuitBreakerRegistry circuitBreakerRegistry, RetryRegistry retryRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
    }

    @PostConstruct
    public void init() {
        initCircuitBreakerListeners();
        initRetryListeners();
    }

    private void initCircuitBreakerListeners() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("chat");
        circuitBreaker
                .getEventPublisher()
                .onStateTransition(
                        event ->
                                LOG.warn(
                                        "CircuitBreaker '{}' state changed: {} -> {}",
                                        event.getCircuitBreakerName(),
                                        event.getStateTransition().getFromState(),
                                        event.getStateTransition().getToState()))
                .onError(
                        event ->
                                LOG.warn(
                                        "CircuitBreaker '{}' recorded error: {}",
                                        event.getCircuitBreakerName(),
                                        event.getThrowable().getMessage()));
    }

    private void initRetryListeners() {
        Retry retry = retryRegistry.retry("chat");
        retry.getEventPublisher()
                .onRetry(
                        event ->
                                LOG.warn(
                                        "Retry '{}' attempt {}, exception: {}",
                                        event.getName(),
                                        event.getNumberOfRetryAttempts(),
                                        event.getLastThrowable() != null
                                                ? event.getLastThrowable().getMessage()
                                                : "unknown"))
                .onError(
                        event ->
                                LOG.error(
                                        "Retry '{}' exhausted after {} attempts",
                                        event.getName(),
                                        event.getNumberOfRetryAttempts()));
    }
}
