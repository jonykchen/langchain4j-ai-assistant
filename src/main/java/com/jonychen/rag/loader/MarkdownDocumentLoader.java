package com.jonychen.rag.loader;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.jonychen.rag.Document;
import com.jonychen.rag.DocumentLoadException;
import com.jonychen.rag.DocumentLoader;
import com.jonychen.rag.DocumentType;

/**
 * Markdown 文档加载器
 *
 * @author jonychen
 */
@Component
public class MarkdownDocumentLoader implements DocumentLoader {

    @Override
    public DocumentType getSupportedType() {
        return DocumentType.MD;
    }

    @Override
    public Document load(InputStream inputStream, String filename) throws DocumentLoadException {
        try {
            String content =
                    new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                            .lines()
                            .collect(Collectors.joining("\n"));

            // 提取标题作为元数据
            String title = extractTitle(content);
            int headingCount = countHeadings(content);

            return new Document(
                    UUID.randomUUID().toString(),
                    filename,
                    DocumentType.MD,
                    content.getBytes(StandardCharsets.UTF_8).length,
                    null,
                    content,
                    java.util.Map.of(
                            "loader",
                            "MarkdownDocumentLoader",
                            "title",
                            title != null ? title : filename,
                            "headingCount",
                            headingCount),
                    java.time.LocalDateTime.now(),
                    java.time.LocalDateTime.now());
        } catch (Exception e) {
            throw DocumentLoadException.readError(filename, e);
        }
    }

    /** 提取第一个标题 */
    private String extractTitle(String content) {
        String[] lines = content.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ")) {
                return trimmed.substring(2).trim();
            }
        }
        return null;
    }

    /** 统计标题数量 */
    private int countHeadings(String content) {
        int count = 0;
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.matches("^#{1,6}\\s+.+")) {
                count++;
            }
        }
        return count;
    }
}
