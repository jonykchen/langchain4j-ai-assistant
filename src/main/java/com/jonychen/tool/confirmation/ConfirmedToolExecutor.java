package com.jonychen.tool.confirmation;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.jonychen.tool.ToolDefinition;
import com.jonychen.tool.ToolNotFoundException;
import com.jonychen.tool.ToolRegistry;
import com.jonychen.tool.ToolResult;
import com.jonychen.tool.resilience.ResilientToolExecutor;
import com.jonychen.tool.resilience.ToolExecutionConfig;
import com.jonychen.tool.resilience.ToolExecutionConfigResolver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 带确认流程的工具执行器
 *
 * @author jonychen
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfirmedToolExecutor {

    private final ToolRegistry toolRegistry;
    private final ToolRiskEvaluator riskEvaluator;
    private final ToolConfirmationManager confirmationManager;
    private final ResilientToolExecutor resilientExecutor;
    private final ToolExecutionConfigResolver configResolver;

    /**
     * 执行工具（带确认流程）
     *
     * @param toolName 工具名称
     * @param params 参数
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @return 执行结果
     */
    public ToolResult execute(
            String toolName, Map<String, Object> params, String sessionId, String userId) {
        // 1. 获取工具定义
        ToolDefinition tool =
                toolRegistry
                        .getTool(toolName)
                        .orElseThrow(
                                () -> new ToolNotFoundException("Tool not found: " + toolName));

        // 2. 评估风险
        ToolRiskLevel riskLevel = riskEvaluator.evaluateRisk(tool, params);

        // 3. 高风险操作需要确认
        if (riskLevel.requiresConfirmation()) {
            String confirmationId =
                    confirmationManager.createConfirmationRequest(
                            toolName, params, sessionId, userId, riskLevel);

            // 返回待确认状态
            return ToolResult.pendingConfirmation(
                    confirmationId, "高风险操作需要确认。风险等级: " + riskLevel.getDisplayName());
        }

        // 4. 直接执行
        ToolExecutionConfig config = configResolver.resolveByRiskLevel(riskLevel.name());
        return resilientExecutor.execute(toolName, params, config);
    }

    /**
     * 确认后继续执行
     *
     * @param confirmationId 确认ID
     * @param approved 是否批准
     * @param userId 确认人ID
     * @return 执行结果
     */
    public ToolResult executeAfterConfirmation(
            String confirmationId, boolean approved, String userId) {
        // 1. 获取确认结果
        ConfirmationResult result = confirmationManager.confirm(confirmationId, approved, userId);

        if (!result.approved()) {
            return ToolResult.failure(result.message());
        }

        // 2. 获取确认请求信息
        PendingConfirmation confirmation = result.confirmation();
        if (confirmation == null) {
            return ToolResult.failure("确认请求不存在");
        }

        // 3. 执行工具
        ToolExecutionConfig config =
                configResolver.resolveByRiskLevel(confirmation.riskLevel().name());

        return resilientExecutor.execute(confirmation.toolName(), confirmation.params(), config);
    }

    /**
     * 获取确认请求状态
     *
     * @param confirmationId 确认ID
     * @return 确认请求信息
     */
    public PendingConfirmation getConfirmationStatus(String confirmationId) {
        return confirmationManager.getConfirmation(confirmationId).orElse(null);
    }

    /**
     * 取消确认请求
     *
     * @param confirmationId 确认ID
     */
    public void cancelConfirmation(String confirmationId) {
        confirmationManager.cancelConfirmation(confirmationId);
    }
}
