package com.jonychen.tool.structured;

/**
 * 响应格式异常
 *
 * @author jonychen
 */
public class ResponseFormatException extends RuntimeException {

    public ResponseFormatException(String message) {
        super(message);
    }

    public ResponseFormatException(String message, Throwable cause) {
        super(message, cause);
    }

    /** 创建解析失败异常 */
    public static ResponseFormatException parseFailed(String rawResponse, String reason) {
        return new ResponseFormatException(
                "Failed to parse structured output: "
                        + reason
                        + ". Raw response: "
                        + (rawResponse.length() > 200
                                ? rawResponse.substring(0, 200) + "..."
                                : rawResponse));
    }

    /** 创建验证失败异常 */
    public static ResponseFormatException validationFailed(String reason) {
        return new ResponseFormatException("Validation failed: " + reason);
    }
}
