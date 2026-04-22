package com.jonychen.rag.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.jonychen.rag.DocumentLoader;
import com.jonychen.rag.DocumentType;
import com.jonychen.rag.EmbeddingService;
import com.jonychen.rag.TextSplitter;
import com.jonychen.rag.VectorStore;
import com.jonychen.rag.loader.MarkdownDocumentLoader;
import com.jonychen.rag.loader.TxtDocumentLoader;
import com.jonychen.rag.splitter.RecursiveCharacterTextSplitter;
import com.jonychen.rag.store.InMemoryVectorStore;

/**
 * RAG 配置
 *
 * @author jonychen
 */
@Configuration
public class RAGConfig {

    /** 文档加载器映射 */
    @Bean
    public Map<DocumentType, DocumentLoader> documentLoaders(
            TxtDocumentLoader txtLoader, MarkdownDocumentLoader mdLoader) {
        Map<DocumentType, DocumentLoader> loaders = new HashMap<>();
        loaders.put(DocumentType.TXT, txtLoader);
        loaders.put(DocumentType.MD, mdLoader);
        // 其他类型可以后续添加
        return loaders;
    }

    /** 文本分割器 */
    @Bean
    public TextSplitter textSplitter() {
        // 默认块大小 500，重叠 100
        return new RecursiveCharacterTextSplitter(500, 100);
    }

    /** 向量存储（默认内存实现） */
    @Bean
    @ConditionalOnMissingBean
    public VectorStore vectorStore(EmbeddingService embeddingService) {
        return new InMemoryVectorStore(embeddingService);
    }
}
