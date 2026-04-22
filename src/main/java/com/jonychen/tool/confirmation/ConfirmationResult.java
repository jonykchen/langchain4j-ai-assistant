package com.jonychen.tool.confirmation;

/**
 * 确认结果
 *
 * @param approved 是否批准
 * @param confirmation 待确认请求
 * @param message 结果消息
 * @author jonychen
 */
public record ConfirmationResult(
        boolean approved, PendingConfirmation confirmation, String message) {
    /** 创建批准结果 */
    public static ConfirmationResult approved(PendingConfirmation confirmation) {
        return new ConfirmationResult(true, confirmation, "已批准执行");
    }

    /** 创建拒绝结果 */
    public static ConfirmationResult rejected(PendingConfirmation confirmation, String reason) {
        return new ConfirmationResult(false, confirmation, "已拒绝: " + reason);
    }

    /** 创建过期结果 */
    public static ConfirmationResult expired() {
        return new ConfirmationResult(false, null, "确认请求已过期");
    }

    /** 创建已处理结果 */
    public static ConfirmationResult alreadyProcessed() {
        return new ConfirmationResult(false, null, "确认请求已处理");
    }
}
