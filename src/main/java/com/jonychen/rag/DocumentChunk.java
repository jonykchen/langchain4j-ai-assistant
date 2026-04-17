package com.jonychen.rag;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 文档分块
 *
 * @param id          分块ID
 * @param documentId  所属文档ID
 * @param content     分块内容
 * @param embedding   向量嵌入
 * @param chunkIndex  分块索引
 * @param startIndex  内容起始位置
 * @param endIndex    内容结束位置
 * @param metadata    元数据
 * @param createdAt   创建时间
 * @author jonychen
 */
public record DocumentChunk(
        String id,
        String documentId,
        String content,
        float[] embedding,
        int chunkIndex,
        int startIndex,
        int endIndex,
        Map<String, Object> metadata,
        LocalDateTime createdAt
) {
    /**
     * 创建文档块
     */
    public static DocumentChunk create(String id, String documentId, String content, int chunkIndex) {
        return new DocumentChunk(
                id, documentId, content, null, chunkIndex, 0, content.length(), Map.of(), LocalDateTime.now()
        );
    }

    /**
     * 创建带位置的文档块
     */
    public static DocumentChunk create(String id, String documentId, String content,
                                        int chunkIndex, int startIndex, int endIndex) {
        return new DocumentChunk(
                id, documentId, content, null, chunkIndex, startIndex, endIndex, Map.of(), LocalDateTime.now()
        );
    }

    /**
     * 设置向量嵌入
     */
    public DocumentChunk withEmbedding(float[] newEmbedding) {
        return new DocumentChunk(
                id, documentId, content, newEmbedding, chunkIndex, startIndex, endIndex, metadata, createdAt
        );
    }

    /**
     * 设置元数据
     */
    public DocumentChunk withMetadata(Map<String, Object> newMetadata) {
        return new DocumentChunk(
                id, documentId, content, embedding, chunkIndex, startIndex, endIndex, newMetadata, createdAt
        );
    }

    /**
     * 获取内容长度
     */
    public int contentLength() {
        return content != null ? content.length() : 0;
    }

    /**
     * 获取向量维度
     */
    public int embeddingDimension() {
        return embedding != null ? embedding.length : 0;
    }
}