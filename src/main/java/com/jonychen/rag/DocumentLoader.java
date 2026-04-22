package com.jonychen.rag;

import java.io.InputStream;

/**
 * 文档加载器接口
 *
 * @author jonychen
 */
public interface DocumentLoader {

    /**
     * 获取支持的文档类型
     *
     * @return 文档类型
     */
    DocumentType getSupportedType();

    /**
     * 加载文档
     *
     * @param inputStream 输入流
     * @param filename 文件名
     * @return 文档对象
     * @throws DocumentLoadException 加载异常
     */
    Document load(InputStream inputStream, String filename) throws DocumentLoadException;

    /**
     * 是否支持该文件类型
     *
     * @param filename 文件名
     * @return 是否支持
     */
    default boolean supports(String filename) {
        return getSupportedType() == DocumentType.fromExtension(filename);
    }
}
