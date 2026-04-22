package com.jonychen.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jonychen.model.ApiResponse;
import com.jonychen.model.LoadBalancedChatModel;
import com.jonychen.model.ModelHealthStatus;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;

/**
 * 模型健康检查控制器
 *
 * <p>提供模型状态查询 API，用于监控和运维。
 */
@RestController
@RequestMapping("/api/health")
public class ModelHealthController {

    private final LoadBalancedChatModel loadBalancedChatModel;

    public ModelHealthController(LoadBalancedChatModel loadBalancedChatModel) {
        this.loadBalancedChatModel = loadBalancedChatModel;
    }

    /**
     * 获取所有模型健康状态
     *
     * @return 模型状态列表
     */
    @GetMapping("/models")
    public ApiResponse<List<ModelHealthStatus>> getModelHealth() {
        return ApiResponse.success(loadBalancedChatModel.getModelStatuses());
    }

    /**
     * 获取所有模型熔断器状态
     *
     * @return 熔断器状态映射
     */
    @GetMapping("/circuit-breakers")
    public ApiResponse<Map<String, Object>> getCircuitBreakerStatus() {
        Map<String, CircuitBreaker.State> states = loadBalancedChatModel.getCircuitBreakerStates();

        Map<String, Object> result = new HashMap<>();
        states.forEach(
                (name, state) -> {
                    Map<String, Object> info = new HashMap<>();
                    info.put("state", state.name());
                    info.put("healthy", state != CircuitBreaker.State.OPEN);
                    result.put(name, info);
                });

        return ApiResponse.success(result);
    }

    /**
     * 获取综合健康状态
     *
     * @return 综合健康信息
     */
    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> getHealthSummary() {
        List<ModelHealthStatus> statuses = loadBalancedChatModel.getModelStatuses();
        Map<String, CircuitBreaker.State> breakerStates =
                loadBalancedChatModel.getCircuitBreakerStates();

        long healthyCount = statuses.stream().filter(ModelHealthStatus::isHealthy).count();

        long openBreakerCount =
                breakerStates.values().stream().filter(s -> s == CircuitBreaker.State.OPEN).count();

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalModels", statuses.size());
        summary.put("healthyModels", healthyCount);
        summary.put("unhealthyModels", statuses.size() - healthyCount);
        summary.put("openCircuitBreakers", openBreakerCount);
        summary.put("status", healthyCount > 0 ? "HEALTHY" : "UNHEALTHY");
        summary.put("details", statuses);

        return ApiResponse.success(summary);
    }
}
