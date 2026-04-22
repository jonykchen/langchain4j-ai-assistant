package com.jonychen.model;

/**
 * 统一 API 响应格式
 *
 * <p>所有接口返回值都应使用此格式包装，确保前端有一致的响应结构： - 成功：{"code": 200, "message": "success", "data": {...}} -
 * 失败：{"code": 40001, "message": "错误描述", "data": null}
 *
 * @param <T> 响应数据类型
 */
public record ApiResponse<T>(int code, String message, T data) {
    /** 成功响应 */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data);
    }

    /** 成功响应（无数据） */
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(200, "success", null);
    }

    /** 成功响应（自定义消息） */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(200, message, data);
    }

    /** 失败响应（使用错误码） */
    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    /** 失败响应（错误码 + 自定义消息） */
    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.getCode(), message, null);
    }

    /** 失败响应（自定义错误码和消息） */
    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
