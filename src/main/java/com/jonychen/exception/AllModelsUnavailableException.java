package com.jonychen.exception;

import com.jonychen.model.ErrorCode;

/**
 * 所有模型不可用异常
 *
 * 当所有配置的模型提供者都不可用时抛出此异常。
 */
public class AllModelsUnavailableException extends BusinessException {

    public AllModelsUnavailableException(String message) {
        super(ErrorCode.ALL_MODELS_UNAVAILABLE, message);
    }

    public AllModelsUnavailableException(String message, Throwable cause) {
        super(ErrorCode.ALL_MODELS_UNAVAILABLE, message, cause);
    }
}