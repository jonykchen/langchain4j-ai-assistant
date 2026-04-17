package com.jonychen.model;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.jonychen.exception.AllModelsUnavailableException;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 负载均衡聊天模型
 *
 * 核心功能：
 * 1. 多模型负载均衡（按权重分配请求）
 * 2. 故障自动转移（主模型故障时切换备用）
 * 3. 模型级熔断（每个模型独立熔断器）
 * 4. 健康状态监控
 *
 * 混合模式策略：
 * - 正常情况：按权重随机选择模型
 * - 故障情况：按优先级顺序切换模型
 */
public class LoadBalancedChatModel implements ChatModel {

    private static final Logger LOG = LoggerFactory.getLogger(LoadBalancedChatModel.class);

    private final List<ModelInstance> models;
    private final Map<String, CircuitBreaker> circuitBreakers;
    private final Map<String, ModelHealthStatus> healthStatuses;
    private final Counter failoverCounter;

    public LoadBalancedChatModel(List<ModelProvider> providers,
                                  CircuitBreakerRegistry registry,
                                  MeterRegistry meterRegistry) {
        this.models = new ArrayList<>();
        this.circuitBreakers = new ConcurrentHashMap<>();
        this.healthStatuses = new ConcurrentHashMap<>();

        for (ModelProvider provider : providers) {
            if (!provider.isValid()) {
                LOG.warn("模型提供者 {} 配置无效，跳过", provider.name());
                continue;
            }

            OpenAiChatModel chatModel = OpenAiChatModel.builder()
                    .baseUrl(provider.baseUrl())
                    .apiKey(provider.apiKey())
                    .modelName(provider.modelName())
                    .timeout(Duration.ofSeconds(60))
                    .build();

            CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                    .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                    .slidingWindowSize(5)
                    .failureRateThreshold(50)
                    .waitDurationInOpenState(Duration.ofSeconds(30))
                    .permittedNumberOfCallsInHalfOpenState(2)
                    .build();
            CircuitBreaker circuitBreaker = registry.circuitBreaker("model-" + provider.name(), config);

            circuitBreaker.getEventPublisher()
                    .onStateTransition(event -> LOG.warn(
                            "模型 {} 熔断器状态变化: {} -> {}",
                            provider.name(),
                            event.getStateTransition().getFromState(),
                            event.getStateTransition().getToState()))
                    .onError(event -> LOG.warn(
                            "模型 {} 调用失败: {}",
                            provider.name(),
                            event.getThrowable().getMessage()));

            models.add(new ModelInstance(provider, chatModel));
            circuitBreakers.put(provider.name(), circuitBreaker);
            healthStatuses.put(provider.name(), new ModelHealthStatus(provider.name(), true));
        }

        this.failoverCounter = meterRegistry != null
                ? Counter.builder("model_failover_total").description("模型切换次数").register(meterRegistry)
                : null;

        LOG.info("负载均衡模型初始化完成，共 {} 个可用模型", models.size());
        for (ModelInstance model : models) {
            LOG.info("  - {} (权重={}, 优先级={})",
                    model.provider.name(), model.provider.weight(), model.provider.priority());
        }
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        List<ModelInstance> availableModels = getAvailableModels();

        if (availableModels.isEmpty()) {
            LOG.error("所有模型均不可用");
            throw new AllModelsUnavailableException("所有 AI 模型均不可用");
        }

        ModelInstance selected = selectByWeight(availableModels);
        String selectedName = selected.provider.name();

        LOG.debug("选择模型: {}", selectedName);

        try {
            ChatResponse response = executeWithCircuitBreaker(selected, request);
            markSuccess(selectedName);
            return response;
        } catch (CallNotPermittedException e) {
            LOG.warn("模型 {} 熔断器打开，切换到备用模型", selectedName);
            recordFailover();
            return fallbackChat(request, selectedName);
        } catch (Exception e) {
            LOG.warn("模型 {} 调用失败: {}, 切换到备用模型", selectedName, e.getMessage());
            markFailure(selectedName);
            recordFailover();
            return fallbackChat(request, selectedName);
        }
    }

    private ChatResponse fallbackChat(ChatRequest request, String failedModel) {
        List<ModelInstance> fallbackModels = models.stream()
                .filter(m -> !m.provider.name().equals(failedModel))
                .filter(m -> !isCircuitBreakerOpen(m.provider.name()))
                .sorted(Comparator.comparingInt(m -> m.provider.priority()))
                .toList();

        for (ModelInstance model : fallbackModels) {
            String modelName = model.provider.name();
            LOG.info("故障转移: 尝试模型 {}", modelName);

            try {
                ChatResponse response = executeWithCircuitBreaker(model, request);
                markSuccess(modelName);
                LOG.info("故障转移成功: 模型 {}", modelName);
                return response;
            } catch (CallNotPermittedException e) {
                LOG.warn("模型 {} 熔断器打开，继续尝试下一个", modelName);
            } catch (Exception e) {
                LOG.warn("模型 {} 调用失败: {}", modelName, e.getMessage());
                markFailure(modelName);
            }
        }

        LOG.error("所有备用模型均不可用");
        throw new AllModelsUnavailableException("所有 AI 模型均不可用，请稍后重试");
    }

    private ChatResponse executeWithCircuitBreaker(ModelInstance model, ChatRequest request) {
        CircuitBreaker cb = circuitBreakers.get(model.provider.name());
        Supplier<ChatResponse> supplier = () -> model.chatModel.chat(request);
        return CircuitBreaker.decorateSupplier(cb, supplier).get();
    }

    private ModelInstance selectByWeight(List<ModelInstance> availableModels) {
        int totalWeight = availableModels.stream()
                .mapToInt(m -> m.provider.weight())
                .sum();

        if (totalWeight <= 0) {
            return availableModels.get(ThreadLocalRandom.current().nextInt(availableModels.size()));
        }

        int random = ThreadLocalRandom.current().nextInt(totalWeight);
        int accumulated = 0;

        for (ModelInstance model : availableModels) {
            accumulated += model.provider.weight();
            if (random < accumulated) {
                return model;
            }
        }

        return availableModels.get(availableModels.size() - 1);
    }

    private List<ModelInstance> getAvailableModels() {
        return models.stream()
                .filter(m -> !isCircuitBreakerOpen(m.provider.name()))
                .toList();
    }

    private boolean isCircuitBreakerOpen(String modelName) {
        CircuitBreaker cb = circuitBreakers.get(modelName);
        return cb != null && cb.getState() == CircuitBreaker.State.OPEN;
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

    private void recordFailover() {
        if (failoverCounter != null) {
            failoverCounter.increment();
        }
    }

    public List<ModelHealthStatus> getModelStatuses() {
        return new ArrayList<>(healthStatuses.values());
    }

    public Map<String, CircuitBreaker.State> getCircuitBreakerStates() {
        Map<String, CircuitBreaker.State> result = new HashMap<>();
        circuitBreakers.forEach((name, cb) -> result.put(name, cb.getState()));
        return result;
    }

    private static class ModelInstance {
        final ModelProvider provider;
        final OpenAiChatModel chatModel;

        ModelInstance(ModelProvider provider, OpenAiChatModel chatModel) {
            this.provider = provider;
            this.chatModel = chatModel;
        }
    }
}
