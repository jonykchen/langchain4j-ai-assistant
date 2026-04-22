package com.jonychen.rag.splitter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.jonychen.rag.Document;
import com.jonychen.rag.DocumentChunk;
import com.jonychen.rag.TextSplitter;

/**
 * 递归字符文本分割器
 *
 * <p>按优先级依次尝试分割： 1. 按段落分割（双换行） 2. 按句子分割（单换行） 3. 按句子分割（句号、问号、感叹号） 4. 按词分割（空格） 5. 按字符分割
 *
 * @author jonychen
 */
@Component
public class RecursiveCharacterTextSplitter implements TextSplitter {

    private final int chunkSize;
    private final int overlap;

    /** 分割优先级顺序 */
    private static final List<String> SEPARATORS =
            List.of(
                    "\n\n", // 段落
                    "\n", // 行
                    "。", // 中文句号
                    ".", // 英文句号
                    "？", // 中文问号
                    "?", // 英文问号
                    "！", // 中文感叹号
                    "!", // 英文感叹号
                    "；", // 中文分号
                    ";", // 英文分号
                    "，", // 中文逗号
                    ",", // 英文逗号
                    " ", // 空格
                    "" // 字符
                    );

    /** 默认构造器（块大小 500，重叠 100） */
    public RecursiveCharacterTextSplitter() {
        this(500, 100);
    }

    /** 自定义构造器 */
    public RecursiveCharacterTextSplitter(int chunkSize, int overlap) {
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    @Override
    public List<DocumentChunk> split(Document document) {
        List<String> textChunks = split(document.content());
        List<DocumentChunk> chunks = new ArrayList<>();

        int startIndex = 0;
        for (int i = 0; i < textChunks.size(); i++) {
            String chunkContent = textChunks.get(i);
            int endIndex = startIndex + chunkContent.length();

            DocumentChunk chunk =
                    DocumentChunk.create(
                            UUID.randomUUID().toString(),
                            document.id(),
                            chunkContent,
                            i,
                            startIndex,
                            endIndex);

            chunk =
                    chunk.withMetadata(
                            java.util.Map.of(
                                    "documentFilename", document.filename(),
                                    "documentType", document.type().name()));

            chunks.add(chunk);
            startIndex = endIndex;
        }

        return chunks;
    }

    @Override
    public List<String> split(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }

        splitRecursive(text, SEPARATORS, chunks);

        // 处理重叠
        return applyOverlap(chunks);
    }

    @Override
    public int getChunkSize() {
        return chunkSize;
    }

    @Override
    public int getOverlap() {
        return overlap;
    }

    /** 递归分割 */
    private void splitRecursive(String text, List<String> separators, List<String> chunks) {
        if (text.length() <= chunkSize) {
            if (!text.trim().isEmpty()) {
                chunks.add(text.trim());
            }
            return;
        }

        // 找到合适的分割符
        String separator = findBestSeparator(text, separators);

        if (separator.isEmpty()) {
            // 无法分割，直接按字符切分
            splitByCharacter(text, chunks);
            return;
        }

        // 按分隔符分割
        String[] parts = text.split(separator);

        // 合并小片段
        StringBuilder currentChunk = new StringBuilder();
        for (String part : parts) {
            if (currentChunk.length() + part.length() + separator.length() <= chunkSize) {
                if (currentChunk.length() > 0 && !separator.equals("\n\n")) {
                    currentChunk.append(separator);
                }
                currentChunk.append(part);
            } else {
                // 当前块满了，保存并开始新块
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());
                }

                if (part.length() > chunkSize) {
                    // 单个部分还是太长，递归分割
                    List<String> nextSeparators =
                            separators.subList(
                                    separators.indexOf(separator) + 1, separators.size());
                    splitRecursive(part, nextSeparators, chunks);
                } else {
                    currentChunk = new StringBuilder(part);
                }
            }
        }

        if (currentChunk.length() > 0) {
            String finalChunk = currentChunk.toString().trim();
            if (!finalChunk.isEmpty()) {
                chunks.add(finalChunk);
            }
        }
    }

    /** 找到最佳分隔符 */
    private String findBestSeparator(String text, List<String> separators) {
        for (String separator : separators) {
            if (text.contains(separator)) {
                return separator;
            }
        }
        return "";
    }

    /** 按字符切分 */
    private void splitByCharacter(String text, List<String> chunks) {
        int length = text.length();
        for (int i = 0; i < length; i += chunkSize) {
            int end = Math.min(i + chunkSize, length);
            String chunk = text.substring(i, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
        }
    }

    /** 应用重叠 */
    private List<String> applyOverlap(List<String> originalChunks) {
        if (overlap <= 0 || originalChunks.size() <= 1) {
            return originalChunks;
        }

        List<String> overlappedChunks = new ArrayList<>();

        for (int i = 0; i < originalChunks.size(); i++) {
            String chunk = originalChunks.get(i);

            if (i > 0) {
                // 从前一个块获取重叠内容
                String prevChunk = originalChunks.get(i - 1);
                int overlapStart = Math.max(0, prevChunk.length() - overlap);
                String overlapContent = prevChunk.substring(overlapStart);
                chunk = overlapContent + chunk;
            }

            overlappedChunks.add(chunk);
        }

        return overlappedChunks;
    }
}
