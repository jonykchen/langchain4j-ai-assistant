package com.jonychen.tool.controller;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jonychen.model.ApiResponse;
import com.jonychen.tool.ToolResult;
import com.jonychen.tool.confirmation.ConfirmedToolExecutor;
import com.jonychen.tool.confirmation.PendingConfirmation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 工具确认 REST API
 *
 * @author jonychen
 */
@Tag(name = "工具确认", description = "高风险工具执行确认相关接口")
@RestController
@RequestMapping("/api/tools/confirmation")
@RequiredArgsConstructor
public class ToolConfirmationController {

    private final ConfirmedToolExecutor confirmedToolExecutor;

    /** 获取确认请求状态 */
    @Operation(summary = "获取确认状态", description = "获取指定确认请求的详细状态")
    @GetMapping("/{confirmationId}")
    public ApiResponse<ConfirmationInfo> getConfirmationStatus(
            @PathVariable String confirmationId) {
        PendingConfirmation confirmation =
                confirmedToolExecutor.getConfirmationStatus(confirmationId);

        if (confirmation == null) {
            return ApiResponse.error(40404, "确认请求不存在或已过期");
        }

        return ApiResponse.success(toConfirmationInfo(confirmation));
    }

    /** 确认执行 */
    @Operation(summary = "确认执行", description = "批准或拒绝高风险工具执行")
    @PostMapping("/{confirmationId}/confirm")
    public ApiResponse<ToolResult> confirmExecution(
            @PathVariable String confirmationId, @RequestBody ConfirmationRequest request) {
        String userId = getCurrentUserId();
        ToolResult result =
                confirmedToolExecutor.executeAfterConfirmation(
                        confirmationId, request.approved(), userId);

        return ApiResponse.success(result);
    }

    /** 取消确认请求 */
    @Operation(summary = "取消确认", description = "取消待确认的工具执行请求")
    @DeleteMapping("/{confirmationId}")
    public ApiResponse<Void> cancelConfirmation(@PathVariable String confirmationId) {
        confirmedToolExecutor.cancelConfirmation(confirmationId);
        return ApiResponse.success(null);
    }

    private ConfirmationInfo toConfirmationInfo(PendingConfirmation confirmation) {
        return new ConfirmationInfo(
                confirmation.confirmationId(),
                confirmation.toolName(),
                confirmation.riskLevel().getDisplayName(),
                confirmation.status().name(),
                confirmation.message(),
                confirmation.createdAt(),
                confirmation.isExpired());
    }

    /** 确认请求 */
    public record ConfirmationRequest(boolean approved) {}

    /** 从 SecurityContext 获取当前认证用户的 ID */
    private String getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof String userId) {
            return userId;
        }
        return null;
    }

    /** 确认信息 VO */
    public record ConfirmationInfo(
            String confirmationId,
            String toolName,
            String riskLevel,
            String status,
            String message,
            java.time.LocalDateTime createdAt,
            boolean expired) {}
}
