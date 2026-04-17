package com.jonychen.rag;

import java.util.List;

/**
 * 文本分割器接口
 *
 * @author jonychen
 */
public interface TextSplitter {

    /**
     * 分割文档为多个块
     *
     * @param document 文档
     * @return 分块列表
     */
    List<DocumentChunk> split(Document document);

    /**
     * 分割文本为多个块
     *
     * @param text 文本内容
     * @return 分块列表
     */
    List<String> split(String text);

    /**
     * 获取块大小
     */
    int getChunkSize();

    /**
     * 获取重叠大小
     */
    int getOverlap();
}