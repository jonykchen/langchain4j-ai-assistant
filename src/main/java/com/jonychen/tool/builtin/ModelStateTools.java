package com.jonychen.tool.builtin;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.jonychen.model.LoadBalancedChatModel;
import com.jonychen.model.ModelHealthStatus;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * 模型状态工具集
 *
 * <p>提供模型健康检查、权重调整、启禁用等工具，供 OpsAgent 调用。 读取操作直接从 LoadBalancedChatModel 获取实时数据；
 * 写入操作修改运行时内存中的模型配置（重启后失效）。
 *
 * @author jonychen
 */
@Component
public class ModelStateTools {

    private static final Logger log = LoggerFactory.getLogger(ModelStateTools.class);

    private final LoadBalancedChatModel loadBalancedChatModel;

    public ModelStateTools(LoadBalancedChatModel loadBalancedChatModel) {
        this.loadBalancedChatModel = loadBalancedChatModel;
    }

    /**
     * 获取所有模型的健康状态
     *
     * @param includeDisabled 是否包含已禁用的模型
     * @return 模型健康状态列表
     */
    @Tool("获取所有 AI 模型的健康状态，包括是否可用、成功/失败次数、最近错误等")
    public String getHealth(@P("是否包含已禁用的模型") boolean includeDisabled) {
        log.info("[ModelStateTools] 获取模型健康状态: includeDisabled={}", includeDisabled);

        List<ModelHealthStatus> statuses = loadBalancedChatModel.getModelStatuses();

        StringBuilder sb = new StringBuilder();
        sb.append("模型健康状态报告:\n");
        sb.append("-".repeat(50)).append("\n");

        for (ModelHealthStatus status : statuses) {
            sb.append(
                    String.format(
                            "模型: %s | 健康: %s | 成功: %d | 失败: %d",
                            status.getModelName(),
                            status.isHealthy() ? "✓" : "✗",
                            status.getSuccessCount(),
                            status.getFailureCount()));

            if (status.getLastError() != null) {
                sb.append(String.format(" | 最近错误: %s", status.getLastError()));
            }
            sb.append("\n");
        }

        sb.append("-".repeat(50)).append("\n");
        sb.append(
                String.format(
                        "总计: %d 个模型, %d 个健康\n",
                        statuses.size(),
                        statuses.stream().filter(ModelHealthStatus::isHealthy).count()));

        return sb.toString();
    }

    /**
     * 调整模型负载均衡权重
     *
     * <p>注意：P0 阶段此操作仅在内存中生效，应用重启后恢复为配置文件中的值。
     *
     * @param modelName 模型名称
     * @param weight 新权重值（1-100）
     * @param reason 调整原因
     * @return 操作结果
     */
    @Tool("调整模型的负载均衡权重，权重越高分配的请求越多。敏感操作，需要确认。")
    public String adjustWeight(
            @P("模型名称，如 dashscope") String modelName,
            @P("新权重值，范围 1-100") int weight,
            @P("调整原因") String reason) {
        log.info(
                "[ModelStateTools] 调整模型权重: model={}, weight={}, reason={}",
                modelName,
                weight,
                reason);

        if (weight < 1 || weight > 100) {
            return "错误: 权重值必须在 1-100 之间，当前值: " + weight;
        }

        boolean success = loadBalancedChatModel.adjustModelWeight(modelName, weight);
        if (success) {
            return String.format("已调整模型 %s 的权重为 %d。原因: %s", modelName, weight, reason);
        }
        return String.format("调整失败: 未找到模型 %s，请检查模型名称是否正确", modelName);
    }

    /**
     * 启用或禁用模型
     *
     * <p>注意：P0 阶段此操作仅在内存中生效，应用重启后恢复为配置文件中的值。
     *
     * @param modelName 模型名称
     * @param enabled 是否启用
     * @param reason 操作原因
     * @return 操作结果
     */
    @Tool("启用或禁用指定模型。禁用后该模型不再接收请求。敏感操作，需要确认。")
    public String toggleEnabled(
            @P("模型名称，如 dashscope") String modelName,
            @P("是否启用，true 启用，false 禁用") boolean enabled,
            @P("操作原因") String reason) {
        log.info(
                "[ModelStateTools] 切换模型状态: model={}, enabled={}, reason={}",
                modelName,
                enabled,
                reason);

        boolean success = loadBalancedChatModel.toggleModelEnabled(modelName, enabled);
        if (success) {
            return String.format("已%s模型 %s。原因: %s", enabled ? "启用" : "禁用", modelName, reason);
        }
        return String.format("操作失败: 未找到模型 %s，请检查模型名称是否正确", modelName);
    }
}
