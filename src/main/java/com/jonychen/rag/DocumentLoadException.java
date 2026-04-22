package com.jonychen.rag;

/**
 * 文档加载异常
 *
 * @author jonychen
 */
public class DocumentLoadException extends RuntimeException {

    public DocumentLoadException(String message) {
        super(message);
    }

    public DocumentLoadException(String message, Throwable cause) {
        super(message, cause);
    }

    public static DocumentLoadException unsupported(String filename) {
        return new DocumentLoadException("不支持的文件类型: " + filename);
    }

    public static DocumentLoadException parseError(String filename, String reason) {
        return new DocumentLoadException("解析文件失败 [" + filename + "]: " + reason);
    }

    public static DocumentLoadException readError(String filename, Throwable cause) {
        return new DocumentLoadException("读取文件失败 [" + filename + "]: " + cause.getMessage(), cause);
    }
}
