package com.jonychen.rag.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.jonychen.model.ApiResponse;
import com.jonychen.rag.Document;
import com.jonychen.rag.DocumentChunk;
import com.jonychen.rag.DocumentLoadException;
import com.jonychen.rag.RAGPipeline;
import com.jonychen.rag.VectorStore;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 文档管理 REST API
 *
 * @author jonychen
 */
@Tag(name = "文档管理", description = "文档上传、检索、问答相关接口")
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final RAGPipeline ragPipeline;
    private final VectorStore vectorStore;

    /** 上传文档 */
    @Operation(summary = "上传文档", description = "上传文档并进行向量化和存储")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DocumentInfo> uploadDocument(@RequestParam("file") MultipartFile file) {
        try {
            Document document =
                    ragPipeline.ingest(file.getInputStream(), file.getOriginalFilename());

            return ApiResponse.success(
                    new DocumentInfo(
                            document.id(),
                            document.filename(),
                            document.type().name(),
                            document.fileSize(),
                            document.createdAt()));
        } catch (DocumentLoadException e) {
            return ApiResponse.error(40001, "文档加载失败: " + e.getMessage());
        } catch (IOException e) {
            return ApiResponse.error(50001, "文件读取失败: " + e.getMessage());
        }
    }

    /** 语义搜索 */
    @Operation(summary = "语义搜索", description = "基于语义相似度搜索文档内容")
    @PostMapping("/search")
    public ApiResponse<List<SearchResultInfo>> search(@RequestBody SearchRequest request) {
        List<VectorStore.SearchResult> results =
                ragPipeline.retrieveWithScore(
                        request.query(), request.topK() != null ? request.topK() : 5);

        List<SearchResultInfo> infos = results.stream().map(this::toSearchResultInfo).toList();

        return ApiResponse.success(infos);
    }

    /** RAG 问答 */
    @Operation(summary = "RAG 问答", description = "基于检索增强生成的问答")
    @PostMapping("/ask")
    public ApiResponse<RAGResponseInfo> ask(@RequestBody AskRequest request) {
        RAGPipeline.RAGResponse response =
                ragPipeline.askWithDetails(
                        request.query(), request.topK() != null ? request.topK() : 5);

        RAGResponseInfo info =
                new RAGResponseInfo(
                        response.answer(),
                        response.sources().stream()
                                .map(
                                        s ->
                                                new SourceInfo(
                                                        s.documentId(),
                                                        s.filename(),
                                                        s.content(),
                                                        s.relevance()))
                                .toList());

        return ApiResponse.success(info);
    }

    /** 获取文档详情 */
    @Operation(summary = "获取文档详情", description = "获取指定文档的所有分块")
    @GetMapping("/{documentId}")
    public ApiResponse<DocumentDetail> getDocument(@PathVariable String documentId) {
        List<DocumentChunk> chunks = vectorStore.findByDocumentId(documentId);

        if (chunks.isEmpty()) {
            return ApiResponse.error(40404, "文档不存在");
        }

        DocumentDetail detail =
                new DocumentDetail(
                        documentId,
                        chunks.size(),
                        chunks.stream().mapToInt(DocumentChunk::contentLength).sum(),
                        chunks.stream()
                                .map(c -> new ChunkInfo(c.id(), c.chunkIndex(), c.contentLength()))
                                .toList());

        return ApiResponse.success(detail);
    }

    /** 删除文档 */
    @Operation(summary = "删除文档", description = "删除文档及其所有分块")
    @DeleteMapping("/{documentId}")
    public ApiResponse<Void> deleteDocument(@PathVariable String documentId) {
        ragPipeline.deleteDocument(documentId);
        return ApiResponse.success(null);
    }

    /** 获取统计信息 */
    @Operation(summary = "获取统计", description = "获取文档和分块数量统计")
    @GetMapping("/stats")
    public ApiResponse<StatsInfo> getStats() {
        return ApiResponse.success(new StatsInfo(vectorStore.count()));
    }

    private SearchResultInfo toSearchResultInfo(VectorStore.SearchResult result) {
        DocumentChunk chunk = result.chunk();
        return new SearchResultInfo(
                chunk.id(),
                chunk.documentId(),
                (String) chunk.metadata().getOrDefault("documentFilename", "未知"),
                chunk.content(),
                result.score());
    }

    // ========== DTO 类 ==========

    public record SearchRequest(String query, Integer topK) {}

    public record AskRequest(String query, Integer topK) {}

    public record DocumentInfo(
            String id,
            String filename,
            String type,
            long fileSize,
            java.time.LocalDateTime createdAt) {}

    public record SearchResultInfo(
            String chunkId, String documentId, String filename, String content, double score) {}

    public record SourceInfo(
            String documentId, String filename, String content, double relevance) {}

    public record RAGResponseInfo(String answer, List<SourceInfo> sources) {}

    public record ChunkInfo(String id, int index, int length) {}

    public record DocumentDetail(
            String documentId, int chunkCount, int totalLength, List<ChunkInfo> chunks) {}

    public record StatsInfo(long totalChunks) {}
}
