package com.jonychen.model;

/**
 * 错误码枚举
 *
 * 错误码命名规范：
 * - 2xx: 成功
 * - 4xx: 客户端错误
 * - 5xx: 服务端错误
 *
 * 细分：
 * - 400xx: 通用请求错误
 * - 401xx: 认证相关
 * - 403xx: 权限相关
 * - 404xx: 资源不存在
 * - 500xx: 服务端内部错误
 * - 502xx: 第三方服务错误（如 AI API）
 */
public enum ErrorCode {

    // ==================== 成功 ====================
    SUCCESS(200, "操作成功"),

    // ==================== 客户端错误 4xx ====================
    BAD_REQUEST(400, "请求参数错误"),
    PARAM_MISSING(40001, "缺少必需参数"),
    PARAM_INVALID(40002, "参数格式不正确"),
    MESSAGE_EMPTY(40003, "消息内容不能为空"),

    UNAUTHORIZED(401, "未授权访问"),
    TOKEN_EXPIRED(40101, "登录已过期，请重新登录"),
    TOKEN_INVALID(40102, "无效的访问令牌"),

    FORBIDDEN(403, "没有操作权限"),

    NOT_FOUND(404, "资源不存在"),
    CONVERSATION_NOT_FOUND(40401, "对话不存在"),

    METHOD_NOT_ALLOWED(405, "请求方法不允许"),
    CONFLICT(409, "资源冲突"),

    RATE_LIMITED(429, "请求过于频繁，请稍后再试"),

    // ==================== 服务端错误 5xx ====================
    INTERNAL_ERROR(500, "服务器内部错误"),
    DATABASE_ERROR(50001, "数据库操作失败"),

    // ==================== AI 服务错误 502xx ====================
    AI_SERVICE_ERROR(50200, "AI 服务异常"),
    AI_API_KEY_INVALID(50201, "API Key 无效或未配置"),
    AI_MODEL_NOT_AVAILABLE(50202, "AI 模型暂不可用"),
    AI_REQUEST_TIMEOUT(50203, "AI 请求超时"),
    AI_RESPONSE_ERROR(50204, "AI 响应解析失败"),
    AI_QUOTA_EXCEEDED(50205, "API 调用额度已用尽"),
    ALL_MODELS_UNAVAILABLE(50206, "所有 AI 模型均不可用"),
    MODEL_FAILOVER(50207, "模型切换中，请稍后重试");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}