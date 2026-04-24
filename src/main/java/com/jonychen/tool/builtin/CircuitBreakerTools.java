package com.jonychen.tool.builtin;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.jonychen.model.LoadBalancedChatModel;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;

/**
 * 熔断器工具集
 *
 * <p>提供熔断器状态查询和重置工具，供 OpsAgent 调用。 熔断器状态包括 CLOSED（正常）、OPEN（熔断中）、HALF_OPEN（半开探测）。
 *
 * @author jonychen
 */
@Component
public class CircuitBreakerTools {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerTools.class);

    private final LoadBalancedChatModel loadBalancedChatModel;

    public CircuitBreakerTools(LoadBalancedChatModel loadBalancedChatModel) {
        this.loadBalancedChatModel = loadBalancedChatModel;
    }

    /**
     * 获取所有熔断器状态
     *
     * @return 熔断器状态报告
     */
    @Tool("获取所有模型的熔断器状态，包括是否熔断、当前状态等")
    public String getStatus() {
        log.info("[CircuitBreakerTools] 获取熔断器状态");

        Map<String, CircuitBreaker.State> states = loadBalancedChatModel.getCircuitBreakerStates();

        StringBuilder sb = new StringBuilder();
        sb.append("熔断器状态报告:\n");
        sb.append("-".repeat(50)).append("\n");

        states.forEach(
                (modelName, state) -> {
                    String statusEmoji = formatState(state);
                    sb.append(String.format("模型: %-20s | 状态: %s%n", modelName, statusEmoji));
                });

        sb.append("-".repeat(50)).append("\n");
        long openCount =
                states.values().stream().filter(s -> s == CircuitBreaker.State.OPEN).count();
        sb.append(String.format("总计: %d 个熔断器, %d 个熔断中%n", states.size(), openCount));

        if (openCount > 0) {
            sb.append("建议: 熔断中的模型暂时不可用，可等待自动恢复或手动重置");
        }

        return sb.toString();
    }

    private String formatState(CircuitBreaker.State state) {
        return switch (state) {
            case CLOSED -> "✓ 正常";
            case OPEN -> "✗ 熔断中";
            case HALF_OPEN -> "⚠ 半开探测";
            case FORCED_OPEN -> "✗ 强制熔断";
            case DISABLED -> "○ 已禁用";
            case METRICS_ONLY -> "📊 仅监控";
        };
    }

    /**
     * 重置熔断器
     *
     * <p>将指定模型的熔断器从 OPEN 状态强制重置为 CLOSED。 此操作需要谨慎使用，仅在确认模型已恢复后执行。
     *
     * @param modelName 模型名称
     * @return 操作结果
     */
    @Tool("重置指定模型的熔断器，将其从熔断状态恢复为正常状态。敏感操作，需要确认。")
    public String reset(@P("模型名称，如 dashscope") String modelName) {
        log.info("[CircuitBreakerTools] 重置熔断器: model={}", modelName);

        boolean success = loadBalancedChatModel.resetCircuitBreaker(modelName);
        if (success) {
            return String.format("已重置模型 %s 的熔断器，状态恢复为正常", modelName);
        }
        return String.format("重置失败: 未找到模型 %s 的熔断器，请检查模型名称是否正确", modelName);
    }
}
