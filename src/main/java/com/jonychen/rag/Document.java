package com.jonychen.rag;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 文档实体
 *
 * @param id           文档ID
 * @param filename     文件名
 * @param type         文档类型
 * @param fileSize     文件大小
 * @param contentHash  内容哈希
 * @param content      文档内容
 * @param metadata     元数据
 * @param createdAt    创建时间
 * @param updatedAt    更新时间
 * @author jonychen
 */
public record Document(
        String id,
        String filename,
        DocumentType type,
        long fileSize,
        String contentHash,
        String content,
        Map<String, Object> metadata,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    /**
     * 创建新文档
     */
    public static Document create(String id, String filename, String content) {
        LocalDateTime now = LocalDateTime.now();
        return new Document(
                id,
                filename,
                DocumentType.fromExtension(filename),
                content.getBytes().length,
                null,
                content,
                Map.of(),
                now,
                now
        );
    }

    /**
     * 创建带元数据的文档
     */
    public static Document create(String id, String filename, String content, Map<String, Object> metadata) {
        LocalDateTime now = LocalDateTime.now();
        return new Document(
                id,
                filename,
                DocumentType.fromExtension(filename),
                content.getBytes().length,
                null,
                content,
                metadata,
                now,
                now
        );
    }

    /**
     * 设置内容
     */
    public Document withContent(String newContent) {
        return new Document(
                id, filename, type, newContent.getBytes().length,
                contentHash, newContent, metadata, createdAt, LocalDateTime.now()
        );
    }

    /**
     * 设置元数据
     */
    public Document withMetadata(Map<String, Object> newMetadata) {
        return new Document(
                id, filename, type, fileSize, contentHash, content,
                newMetadata, createdAt, LocalDateTime.now()
        );
    }
}