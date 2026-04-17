package com.jonychen.rag.loader;

import com.jonychen.rag.*;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 纯文本文档加载器
 *
 * @author jonychen
 */
@Component
public class TxtDocumentLoader implements DocumentLoader {

    @Override
    public DocumentType getSupportedType() {
        return DocumentType.TXT;
    }

    @Override
    public Document load(InputStream inputStream, String filename) throws DocumentLoadException {
        try {
            String content = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));

            return new Document(
                    UUID.randomUUID().toString(),
                    filename,
                    DocumentType.TXT,
                    content.getBytes(StandardCharsets.UTF_8).length,
                    null,
                    content,
                    java.util.Map.of("loader", "TxtDocumentLoader"),
                    java.time.LocalDateTime.now(),
                    java.time.LocalDateTime.now()
            );
        } catch (Exception e) {
            throw DocumentLoadException.readError(filename, e);
        }
    }
}