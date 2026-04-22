package com.jonychen.rag;

import java.util.List;

/**
 * 向量嵌入服务接口
 *
 * @author jonychen
 */
public interface EmbeddingService {

    /**
     * 获取单个文本的向量嵌入
     *
     * @param text 文本内容
     * @return 向量嵌入
     */
    float[] embed(String text);

    /**
     * 批量获取向量嵌入
     *
     * @param texts 文本列表
     * @return 向量嵌入列表
     */
    List<float[]> embedBatch(List<String> texts);

    /**
     * 获取向量维度
     *
     * @return 向量维度
     */
    int getDimension();

    /**
     * 获取模型名称
     *
     * @return 模型名称
     */
    String getModelName();

    /**
     * 计算两个向量的余弦相似度
     *
     * @param a 向量 a
     * @param b 向量 b
     * @return 相似度（-1 到 1）
     */
    default double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0 || normB == 0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 计算两个向量的欧几里得距离
     *
     * @param a 向量 a
     * @param b 向量 b
     * @return 距离
     */
    default double euclideanDistance(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return Double.MAX_VALUE;
        }

        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            sum += diff * diff;
        }

        return Math.sqrt(sum);
    }
}
