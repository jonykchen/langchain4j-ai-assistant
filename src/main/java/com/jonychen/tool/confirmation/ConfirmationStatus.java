package com.jonychen.tool.confirmation;

/**
 * 确认状态
 *
 * @author jonychen
 */
public enum ConfirmationStatus {
    /**
     * 待确认
     */
    PENDING,

    /**
     * 已批准
     */
    APPROVED,

    /**
     * 已拒绝
     */
    REJECTED,

    /**
     * 已过期
     */
    EXPIRED,

    /**
     * 已取消
     */
    CANCELLED
}