package com.jonychen.rag.embedding;

import com.jonychen.rag.EmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * 简单的 Embedding 降级实现
 *
 * 当没有配置 EmbeddingModel 时使用，基于哈希生成固定维度向量。
 * 仅用于开发/测试，生产环境应配置真实的 EmbeddingModel。
 *
 * @author jonychen
 */
@Slf4j
@Service
@Primary
public class SimpleEmbeddingService implements EmbeddingService {

    private static final int DIMENSION = 1536;

    public SimpleEmbeddingService() {
        log.warn("使用 SimpleEmbeddingService（基于哈希的降级实现），"
                + "生产环境请配置 EmbeddingModel（如 langchain4j-open-ai-spring-boot-starter 的 embedding 配置）");
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[DIMENSION];
        }
        return hashToVector(text);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        List<float[]> embeddings = new ArrayList<>();
        for (String text : texts) {
            embeddings.add(embed(text));
        }
        return embeddings;
    }

    @Override
    public int getDimension() {
        return DIMENSION;
    }

    @Override
    public String getModelName() {
        return "simple-hash-embedding";
    }

    private float[] hashToVector(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            float[] vector = new float[DIMENSION];

            // 对文本的不同片段进行哈希，生成足够的向量维度
            for (int i = 0; i < DIMENSION; i += 32) {
                String chunk = text + "#" + (i / 32);
                byte[] hash = digest.digest(chunk.getBytes(StandardCharsets.UTF_8));
                for (int j = 0; j < hash.length && (i + j) < DIMENSION; j++) {
                    vector[i + j] = (hash[j] & 0xFF) / 255.0f;
                }
            }

            // 归一化
            float norm = 0;
            for (float v : vector) {
                norm += v * v;
            }
            norm = (float) Math.sqrt(norm);
            if (norm > 0) {
                for (int i = 0; i < vector.length; i++) {
                    vector[i] /= norm;
                }
            }

            return vector;
        } catch (Exception e) {
            log.error("Failed to generate hash embedding: {}", e.getMessage());
            return new float[DIMENSION];
        }
    }
}
