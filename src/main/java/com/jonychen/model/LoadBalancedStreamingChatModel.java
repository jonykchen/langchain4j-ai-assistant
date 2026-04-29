package com.jonychen.model;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jonychen.exception.AllModelsUnavailableException;
import com.jonychen.observability.trace.RequestTraceService;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 负载均衡流式聊天模型
 *
 * <p>核心功能： 1. 多模型负载均衡（按权重分配请求） 2. 故障自动转移（主模型故障时切换备用） 3. 模型级熔断（每个模型独立熔断器） 4. 健康状态监控
 */
public class LoadBalancedStreamingChatModel implements StreamingChatModel {

    private static final Logger LOG = LoggerFactory.getLogger(LoadBalancedStreamingChatModel.class);

    private final List<StreamingModelInstance> models;
    private final Map<String, CircuitBreaker> circuitBreakers;
    private final Map<String, ModelHealthStatus> healthStatuses;
    private final Counter failoverCounter;
    private final RequestTraceService traceService;

    public LoadBalancedStreamingChatModel(
            List<ModelProvider> providers,
            CircuitBreakerRegistry registry,
            MeterRegistry meterRegistry,
            RequestTraceService traceService) {
        this.models = new ArrayList<>();
        this.circuitBreakers = new ConcurrentHashMap<>();
        this.healthStatuses = new ConcurrentHashMap<>();
        this.traceService = traceService;

        for (ModelProvider provider : providers) {
            if (!provider.isValid()) {
                LOG.warn("流式模型提供者 {} 配置无效，跳过", provider.name());
                continue;
            }

            OpenAiStreamingChatModel streamingModel =
                    OpenAiStreamingChatModel.builder()
                            .baseUrl(provider.baseUrl())
                            .apiKey(provider.apiKey())
                            .modelName(provider.modelName())
                            .timeout(Duration.ofSeconds(60))
                            .build();

            CircuitBreaker circuitBreaker =
                    registry.circuitBreaker(
                            "model-" + provider.name(),
                            CircuitBreakerConfig.custom()
                                    .slidingWindowType(
                                            CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                                    .slidingWindowSize(5)
                                    .failureRateThreshold(50)
                                    .waitDurationInOpenState(Duration.ofSeconds(30))
                                    .permittedNumberOfCallsInHalfOpenState(2)
                                    .build());

            models.add(new StreamingModelInstance(provider, streamingModel));
            circuitBreakers.put(provider.name(), circuitBreaker);
            healthStatuses.put(provider.name(), new ModelHealthStatus(provider.name(), true));
        }

        this.failoverCounter =
                meterRegistry != null
                        ? Counter.builder("model_stream_failover_total")
                                .description("流式模型切换次数")
                                .register(meterRegistry)
                        : null;

        LOG.info("负载均衡流式模型初始化完成，共 {} 个可用模型", models.size());
    }

    @Override
    public void chat(ChatRequest request, StreamingChatResponseHandler handler) {
        List<StreamingModelInstance> availableModels = getAvailableModels();

        if (availableModels.isEmpty()) {
            LOG.error("所有流式模型均不可用");
            handler.onError(new AllModelsUnavailableException("所有 AI 模型均不可用"));
            return;
        }

        StreamingModelInstance selected = selectByWeight(availableModels);
        String selectedName = selected.provider.name();

        // 记录模型选择
        if (traceService != null) {
            List<String> candidates = availableModels.stream().map(m -> m.provider.name()).toList();
            traceService.logModelSelect(
                    candidates, selectedName, selected.provider.weight(), "weight-random");
            traceService.logApiCallStart(
                    selectedName, selected.provider.baseUrl(), estimateTokens(request));
        }

        try {
            CircuitBreaker cb = circuitBreakers.get(selectedName);
            selected.streamingModel.chat(
                    request, new FaultTolerantHandler(handler, request, selectedName, cb));
        } catch (Exception e) {
            LOG.warn("流式模型 {} 启动失败: {}", selectedName, e.getMessage());
            markFailure(selectedName);
            if (traceService != null) {
                traceService.logApiCallEnd(selectedName, false, e.getMessage());
            }
            handleFailover(request, handler, selectedName);
        }
    }

    private void handleFailover(
            ChatRequest request, StreamingChatResponseHandler handler, String failedModel) {
        recordFailover();

        List<StreamingModelInstance> fallbackModels =
                models.stream()
                        .filter(m -> !m.provider.name().equals(failedModel))
                        .filter(m -> !isCircuitBreakerOpen(m.provider.name()))
                        .sorted(Comparator.comparingInt(m -> m.provider.priority()))
                        .toList();

        for (StreamingModelInstance model : fallbackModels) {
            String modelName = model.provider.name();
            LOG.info("流式故障转移: 尝试模型 {}", modelName);

            // 记录故障转移
            if (traceService != null) {
                traceService.logFailover(failedModel, modelName, "startup-failed");
                traceService.logApiCallStart(
                        modelName, model.provider.baseUrl(), estimateTokens(request));
            }

            try {
                CircuitBreaker cb = circuitBreakers.get(modelName);
                model.streamingModel.chat(
                        request, new FaultTolerantHandler(handler, request, modelName, cb));
                return;
            } catch (Exception e) {
                LOG.warn("流式模型 {} 启动失败: {}", modelName, e.getMessage());
                markFailure(modelName);
                if (traceService != null) {
                    traceService.logApiCallEnd(modelName, false, e.getMessage());
                }
            }
        }

        LOG.error("所有备用流式模型均不可用");
        handler.onError(new AllModelsUnavailableException("所有 AI 模型均不可用，请稍后重试"));
    }

    private StreamingModelInstance selectByWeight(List<StreamingModelInstance> availableModels) {
        int totalWeight = availableModels.stream().mapToInt(m -> m.provider.weight()).sum();

        if (totalWeight <= 0) {
            return availableModels.get(ThreadLocalRandom.current().nextInt(availableModels.size()));
        }

        int random = ThreadLocalRandom.current().nextInt(totalWeight);
        int accumulated = 0;

        for (StreamingModelInstance model : availableModels) {
            accumulated += model.provider.weight();
            if (random < accumulated) {
                return model;
            }
        }

        return availableModels.get(availableModels.size() - 1);
    }

    private List<StreamingModelInstance> getAvailableModels() {
        return models.stream().filter(m -> !isCircuitBreakerOpen(m.provider.name())).toList();
    }

    private boolean isCircuitBreakerOpen(String modelName) {
        CircuitBreaker cb = circuitBreakers.get(modelName);
        return cb != null && cb.getState() == CircuitBreaker.State.OPEN;
    }

    private void recordFailover() {
        if (failoverCounter != null) {
            failoverCounter.increment();
        }
    }

    public List<ModelHealthStatus> getModelStatuses() {
        return new ArrayList<>(healthStatuses.values());
    }

    private void markSuccess(String modelName) {
        ModelHealthStatus status = healthStatuses.get(modelName);
        if (status != null) {
            status.setHealthy(true);
            status.setLastError(null);
            status.setSuccessCount(status.getSuccessCount() + 1);
        }
    }

    private void markFailure(String modelName) {
        ModelHealthStatus status = healthStatuses.get(modelName);
        if (status != null) {
            status.setHealthy(false);
            status.setFailureCount(status.getFailureCount() + 1);
        }
    }

    /** 估算请求 Token 数量（粗略估算） */
    private int estimateTokens(ChatRequest request) {
        if (request == null || request.messages() == null) {
            return 0;
        }
        // 简单估算：每条消息平均约 100 个 token
        return request.messages().size() * 100;
    }

    private static class StreamingModelInstance {
        final ModelProvider provider;
        final OpenAiStreamingChatModel streamingModel;

        StreamingModelInstance(ModelProvider provider, OpenAiStreamingChatModel streamingModel) {
            this.provider = provider;
            this.streamingModel = streamingModel;
        }
    }

    private class FaultTolerantHandler implements StreamingChatResponseHandler {
        private final StreamingChatResponseHandler delegate;
        private final ChatRequest request;
        private final String modelName;
        private final CircuitBreaker circuitBreaker;
        private final long startTimeNanos;
        private volatile boolean completed = false;

        FaultTolerantHandler(
                StreamingChatResponseHandler delegate,
                ChatRequest request,
                String modelName,
                CircuitBreaker circuitBreaker) {
            this.delegate = delegate;
            this.request = request;
            this.modelName = modelName;
            this.circuitBreaker = circuitBreaker;
            this.startTimeNanos = System.nanoTime();
        }

        @Override
        public void onPartialResponse(String partialResponse) {
            delegate.onPartialResponse(partialResponse);
        }

        @Override
        public void onCompleteResponse(ChatResponse completeResponse) {
            completed = true;
            long durationMs =
                    java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                            System.nanoTime() - startTimeNanos);
            circuitBreaker.onSuccess(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            markSuccess(modelName);

            // 记录 API 调用成功
            if (traceService != null) {
                traceService.logApiCallEnd(modelName, true, null);
            }

            delegate.onCompleteResponse(completeResponse);
        }

        @Override
        public void onError(Throwable error) {
            if (!completed) {
                long durationMs =
                        java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                                System.nanoTime() - startTimeNanos);
                circuitBreaker.onError(
                        durationMs, java.util.concurrent.TimeUnit.MILLISECONDS, error);
                markFailure(modelName);
                LOG.warn("流式模型 {} 失败: {}", modelName, error.getMessage());

                // 记录 API 调用失败
                if (traceService != null) {
                    traceService.logApiCallEnd(modelName, false, error.getMessage());
                }

                handleFailover(request, delegate, modelName);
            }
        }
    }
}
