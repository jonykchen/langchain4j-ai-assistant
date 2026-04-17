package com.jonychen.exception;

import com.jonychen.model.ErrorCode;

/**
 * 业务异常类
 *
 * 用于业务逻辑中抛出可预期的异常，由全局异常处理器捕获并返回友好提示
 *
 * 使用示例：
 * if (message == null || message.isBlank()) {
 *     throw new BusinessException(ErrorCode.MESSAGE_EMPTY);
 * }
 *
 * // 或者自定义消息
 * throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "模型服务暂时不可用");
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * 使用错误码构造
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /**
     * 使用错误码 + 自定义消息构造
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 使用错误码 + 原因构造（用于异常链）
     */
    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    /**
     * 使用错误码 + 自定义消息 + 原因构造
     */
    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public int getCode() {
        return errorCode.getCode();
    }
}
