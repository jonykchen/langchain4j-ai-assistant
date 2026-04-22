package com.jonychen.rag;

import java.util.List;

/**
 * 向量存储接口
 *
 * @author jonychen
 */
public interface VectorStore {

    /**
     * 添加文档块
     *
     * @param chunks 文档块列表
     */
    void add(List<DocumentChunk> chunks);

    /**
     * 添加单个文档块
     *
     * @param chunk 文档块
     */
    void add(DocumentChunk chunk);

    /**
     * 相似度搜索
     *
     * @param queryVector 查询向量
     * @param topK 返回数量
     * @return 相似的文档块列表
     */
    List<DocumentChunk> search(float[] queryVector, int topK);

    /**
     * 带分数的相似度搜索
     *
     * @param queryVector 查询向量
     * @param topK 返回数量
     * @return 搜索结果列表（包含分数）
     */
    List<SearchResult> searchWithScore(float[] queryVector, int topK);

    /**
     * 按文档ID搜索
     *
     * @param documentId 文档ID
     * @return 文档块列表
     */
    List<DocumentChunk> findByDocumentId(String documentId);

    /**
     * 删除文档（级联删除所有块）
     *
     * @param documentId 文档ID
     */
    void delete(String documentId);

    /**
     * 删除单个文档块
     *
     * @param chunkId 文档块ID
     */
    void deleteChunk(String chunkId);

    /**
     * 获取文档块数量
     *
     * @return 数量
     */
    long count();

    /**
     * 获取指定文档的块数量
     *
     * @param documentId 文档ID
     * @return 数量
     */
    long countByDocumentId(String documentId);

    /** 清空所有数据 */
    void clear();

    /**
     * 获取文档块
     *
     * @param chunkId 块ID
     * @return 文档块（可选）
     */
    DocumentChunk getChunk(String chunkId);

    /**
     * 搜索结果
     *
     * @param chunk 文档块
     * @param score 相似度分数
     */
    record SearchResult(DocumentChunk chunk, double score) {
        /** 创建搜索结果 */
        public static SearchResult of(DocumentChunk chunk, double score) {
            return new SearchResult(chunk, score);
        }
    }
}
