package com.jonychen.tool;

import java.util.Map;

/**
 * 工具执行结果
 *
 * @param success       是否成功
 * @param data          返回数据
 * @param error         错误信息
 * @param executionTimeMs 执行时间（毫秒）
 * @param metadata      元数据
 * @param pending       是否等待确认
 * @param confirmationId 确认ID（等待确认时使用）
 * @author jonychen
 */
public record ToolResult(
        boolean success,
        Object data,
        String error,
        long executionTimeMs,
        Map<String, Object> metadata,
        boolean pending,
        String confirmationId
) {
    /**
     * 创建成功结果
     */
    public static ToolResult success(Object data) {
        return new ToolResult(true, data, null, 0, Map.of(), false, null);
    }

    /**
     * 创建成功结果（带元数据）
     */
    public static ToolResult success(Object data, Map<String, Object> metadata) {
        return new ToolResult(true, data, null, 0, metadata, false, null);
    }

    /**
     * 创建失败结果
     */
    public static ToolResult failure(String error) {
        return new ToolResult(false, null, error, 0, Map.of(), false, null);
    }

    /**
     * 创建失败结果（带元数据）
     */
    public static ToolResult failure(String error, Map<String, Object> metadata) {
        return new ToolResult(false, null, error, 0, metadata, false, null);
    }

    /**
     * 创建待确认结果
     */
    public static ToolResult pendingConfirmation(String confirmationId, String message) {
        return new ToolResult(false, message, null, 0, Map.of(), true, confirmationId);
    }

    /**
     * 创建带执行时间的结果
     */
    public ToolResult withExecutionTime(long executionTimeMs) {
        return new ToolResult(success, data, error, executionTimeMs, metadata, pending, confirmationId);
    }
}
