package com.jonychen.rag.store;

import com.jonychen.rag.DocumentChunk;
import com.jonychen.rag.EmbeddingService;
import com.jonychen.rag.VectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 内存向量存储实现
 *
 * 用于开发测试，生产环境建议使用 PgVectorStore
 *
 * @author jonychen
 */
@Slf4j
@Component
public class InMemoryVectorStore implements VectorStore {

    private final Map<String, DocumentChunk> chunks = new ConcurrentHashMap<>();
    private final Map<String, List<String>> documentChunks = new ConcurrentHashMap<>();
    private final EmbeddingService embeddingService;

    public InMemoryVectorStore(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @Override
    public void add(List<DocumentChunk> chunkList) {
        if (chunkList == null || chunkList.isEmpty()) {
            return;
        }

        for (DocumentChunk chunk : chunkList) {
            add(chunk);
        }

        log.debug("Added {} chunks to in-memory store", chunkList.size());
    }

    @Override
    public void add(DocumentChunk chunk) {
        if (chunk == null || chunk.id() == null) {
            return;
        }

        chunks.put(chunk.id(), chunk);

        // 更新文档索引
        documentChunks.computeIfAbsent(chunk.documentId(), k -> new ArrayList<>())
                .add(chunk.id());
    }

    @Override
    public List<DocumentChunk> search(float[] queryVector, int topK) {
        return searchWithScore(queryVector, topK).stream()
                .map(SearchResult::chunk)
                .collect(Collectors.toList());
    }

    @Override
    public List<SearchResult> searchWithScore(float[] queryVector, int topK) {
        if (queryVector == null || queryVector.length == 0) {
            return List.of();
        }

        // 计算所有块的相似度
        List<SearchResult> results = new ArrayList<>();

        for (DocumentChunk chunk : chunks.values()) {
            if (chunk.embedding() != null) {
                double similarity = embeddingService.cosineSimilarity(queryVector, chunk.embedding());
                results.add(SearchResult.of(chunk, similarity));
            }
        }

        // 按相似度排序
        results.sort((a, b) -> Double.compare(b.score(), a.score()));

        // 返回 TopK
        return results.stream()
                .limit(topK)
                .collect(Collectors.toList());
    }

    @Override
    public List<DocumentChunk> findByDocumentId(String documentId) {
        if (documentId == null) {
            return List.of();
        }

        List<String> chunkIds = documentChunks.get(documentId);
        if (chunkIds == null) {
            return List.of();
        }

        return chunkIds.stream()
                .map(chunks::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(DocumentChunk::chunkIndex))
                .collect(Collectors.toList());
    }

    @Override
    public void delete(String documentId) {
        if (documentId == null) {
            return;
        }

        List<String> chunkIds = documentChunks.remove(documentId);
        if (chunkIds != null) {
            for (String chunkId : chunkIds) {
                chunks.remove(chunkId);
            }
        }

        log.debug("Deleted document: {}", documentId);
    }

    @Override
    public void deleteChunk(String chunkId) {
        if (chunkId == null) {
            return;
        }

        DocumentChunk chunk = chunks.remove(chunkId);
        if (chunk != null) {
            List<String> docChunks = documentChunks.get(chunk.documentId());
            if (docChunks != null) {
                docChunks.remove(chunkId);
            }
        }
    }

    @Override
    public long count() {
        return chunks.size();
    }

    @Override
    public long countByDocumentId(String documentId) {
        List<String> chunkIds = documentChunks.get(documentId);
        return chunkIds != null ? chunkIds.size() : 0;
    }

    @Override
    public void clear() {
        chunks.clear();
        documentChunks.clear();
        log.info("Cleared all chunks from in-memory store");
    }

    @Override
    public DocumentChunk getChunk(String chunkId) {
        return chunks.get(chunkId);
    }
}