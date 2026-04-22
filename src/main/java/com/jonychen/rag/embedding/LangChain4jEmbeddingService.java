package com.jonychen.rag.embedding;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import com.jonychen.rag.EmbeddingService;

import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * LangChain4j Embedding 服务实现
 *
 * <p>仅当容器中存在 EmbeddingModel bean 时才激活
 *
 * @author jonychen
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(EmbeddingModel.class)
public class LangChain4jEmbeddingService implements EmbeddingService {

    private final EmbeddingModel embeddingModel;

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[0];
        }

        try {
            dev.langchain4j.data.embedding.Embedding embedding =
                    embeddingModel.embed(text).content();
            return embedding.vector();
        } catch (Exception e) {
            log.error("Failed to generate embedding: {}", e.getMessage());
            throw new RuntimeException("Embedding generation failed", e);
        }
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
        // 通过嵌入一个空字符串来获取维度
        try {
            dev.langchain4j.data.embedding.Embedding embedding =
                    embeddingModel.embed("test").content();
            return embedding.vector().length;
        } catch (Exception e) {
            // 默认维度
            return 1536; // OpenAI text-embedding-ada-002 的维度
        }
    }

    @Override
    public String getModelName() {
        return embeddingModel.getClass().getSimpleName();
    }
}
