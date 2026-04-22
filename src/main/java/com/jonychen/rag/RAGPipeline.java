package com.jonychen.rag;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * RAG 流水线
 *
 * <p>提供文档入库、检索、问答的完整流程
 *
 * @author jonychen
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RAGPipeline {

    private final Map<DocumentType, DocumentLoader> loaders;
    private final TextSplitter textSplitter;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final ChatModel chatModel;

    /**
     * 文档入库流程
     *
     * @param inputStream 输入流
     * @param filename 文件名
     * @return 文档对象
     */
    public Document ingest(InputStream inputStream, String filename) throws DocumentLoadException {
        // 1. 确定文档类型
        DocumentType type = DocumentType.fromExtension(filename);

        // 2. 获取对应的加载器
        DocumentLoader loader = loaders.get(type);
        if (loader == null) {
            throw DocumentLoadException.unsupported(filename);
        }

        // 3. 加载文档
        Document document = loader.load(inputStream, filename);
        log.info(
                "Loaded document: {} (type: {}, size: {} bytes)",
                filename,
                type,
                document.fileSize());

        // 4. 分割文本
        List<DocumentChunk> chunks = textSplitter.split(document);
        log.info("Split into {} chunks", chunks.size());

        // 5. 生成嵌入向量
        List<String> texts = chunks.stream().map(DocumentChunk::content).toList();

        List<float[]> embeddings = embeddingService.embedBatch(texts);

        // 6. 更新块的向量
        for (int i = 0; i < chunks.size(); i++) {
            chunks.set(i, chunks.get(i).withEmbedding(embeddings.get(i)));
        }

        // 7. 存入向量数据库
        vectorStore.add(chunks);

        log.info("Ingested document: {} with {} chunks", document.id(), chunks.size());
        return document;
    }

    /**
     * 检索相关文档块
     *
     * @param query 查询文本
     * @param topK 返回数量
     * @return 相关文档块列表
     */
    public List<DocumentChunk> retrieve(String query, int topK) {
        // 1. 查询向量化
        float[] queryVector = embeddingService.embed(query);

        // 2. 相似度搜索
        return vectorStore.search(queryVector, topK);
    }

    /**
     * 带分数的检索
     *
     * @param query 查询文本
     * @param topK 返回数量
     * @return 搜索结果列表
     */
    public List<VectorStore.SearchResult> retrieveWithScore(String query, int topK) {
        float[] queryVector = embeddingService.embed(query);
        return vectorStore.searchWithScore(queryVector, topK);
    }

    /**
     * RAG 问答
     *
     * @param query 用户问题
     * @param topK 检索数量
     * @return 生成的回答
     */
    public String ask(String query, int topK) {
        // 1. 检索相关文档
        List<DocumentChunk> context = retrieve(query, topK);

        if (context.isEmpty()) {
            return "抱歉，没有找到相关的参考信息。";
        }

        // 2. 构建上下文
        String contextText =
                context.stream()
                        .map(
                                chunk -> {
                                    String source =
                                            (String)
                                                    chunk.metadata()
                                                            .getOrDefault(
                                                                    "documentFilename", "未知来源");
                                    return String.format("【来源：%s】\n%s", source, chunk.content());
                                })
                        .collect(Collectors.joining("\n\n---\n\n"));

        // 3. 构建 Prompt
        String prompt = buildRAGPrompt(query, contextText);

        // 4. 生成回答
        return chatModel
                .chat(ChatRequest.builder().messages(UserMessage.from(prompt)).build())
                .aiMessage()
                .text();
    }

    /**
     * RAG 问答（返回详细信息）
     *
     * @param query 用户问题
     * @param topK 检索数量
     * @return RAG 响应
     */
    public RAGResponse askWithDetails(String query, int topK) {
        List<VectorStore.SearchResult> searchResults = retrieveWithScore(query, topK);

        if (searchResults.isEmpty()) {
            return new RAGResponse("抱歉，没有找到相关的参考信息。", List.of());
        }

        String contextText =
                searchResults.stream()
                        .map(
                                result -> {
                                    String source =
                                            (String)
                                                    result.chunk()
                                                            .metadata()
                                                            .getOrDefault(
                                                                    "documentFilename", "未知来源");
                                    return String.format(
                                            "【来源：%s，相关度：%.2f】\n%s",
                                            source, result.score(), result.chunk().content());
                                })
                        .collect(Collectors.joining("\n\n---\n\n"));

        String prompt = buildRAGPrompt(query, contextText);
        String answer =
                chatModel
                        .chat(ChatRequest.builder().messages(UserMessage.from(prompt)).build())
                        .aiMessage()
                        .text();

        List<SourceReference> sources =
                searchResults.stream()
                        .map(
                                result ->
                                        new SourceReference(
                                                result.chunk().documentId(),
                                                (String)
                                                        result.chunk()
                                                                .metadata()
                                                                .getOrDefault(
                                                                        "documentFilename", "未知"),
                                                result.chunk().content(),
                                                result.score()))
                        .toList();

        return new RAGResponse(answer, sources);
    }

    /** 构建 RAG Prompt */
    private String buildRAGPrompt(String query, String contextText) {
        return """
                你是一个知识助手，请基于以下参考信息回答用户问题。

                要求：
                1. 只使用参考信息中的内容回答
                2. 如果参考信息中没有答案，请明确说明
                3. 如果引用具体内容，请注明来源
                4. 回答要简洁准确

                参考信息：
                %s

                用户问题：%s

                回答：
                """
                .formatted(contextText, query);
    }

    /**
     * 删除文档
     *
     * @param documentId 文档ID
     */
    public void deleteDocument(String documentId) {
        vectorStore.delete(documentId);
        log.info("Deleted document: {}", documentId);
    }

    /**
     * RAG 响应
     *
     * @param answer 回答内容
     * @param sources 来源引用
     */
    public record RAGResponse(String answer, List<SourceReference> sources) {}

    /**
     * 来源引用
     *
     * @param documentId 文档ID
     * @param filename 文件名
     * @param content 内容片段
     * @param relevance 相关度
     */
    public record SourceReference(
            String documentId, String filename, String content, double relevance) {}
}
