package com.jonychen.tool;

/**
 * 工具未找到异常
 *
 * @author jonychen
 */
public class ToolNotFoundException extends RuntimeException {

    public ToolNotFoundException(String message) {
        super(message);
    }

    public ToolNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
