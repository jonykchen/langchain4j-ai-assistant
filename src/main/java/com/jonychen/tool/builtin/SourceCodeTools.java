package com.jonychen.tool.builtin;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.jonychen.tool.AgentTool;
import com.jonychen.tool.RiskLevel;
import com.jonychen.tool.ToolCategory;
import com.jonychen.tool.ToolParam;
import com.jonychen.tool.ToolResult;
import com.jonychen.tool.security.ProjectPathResolver;
import com.jonychen.tool.security.ProjectPathResolver.FileContent;
import com.jonychen.tool.security.ProjectPathResolver.FileInfo;

/**
 * 源代码工具集
 *
 * <p>提供安全的源代码读取能力，供 DataAgent 和 TestAgent 调用。
 *
 * <h2>安全机制</h2>
 *
 * <ol>
 *   <li>路径遍历防护（由 ProjectPathResolver 处理）
 *   <li>文件扩展名白名单
 *   <li>敏感目录黑名单
 *   <li>文件大小和行数限制
 * </ol>
 *
 * <h2>工具列表</h2>
 *
 * <ul>
 *   <li>{@code list_source_files} - 列出项目源代码文件
 *   <li>{@code read_source_file} - 读取指定文件内容
 * </ul>
 *
 * @author jonychen
 */
@Component
public class SourceCodeTools {

    private static final Logger log = LoggerFactory.getLogger(SourceCodeTools.class);

    /** 允许的文件扩展名白名单 */
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of(
                    ".java",
                    ".xml",
                    ".yml",
                    ".yaml",
                    ".properties",
                    ".json",
                    ".md",
                    ".txt",
                    ".gradle",
                    ".sql",
                    ".html",
                    ".css",
                    ".js",
                    ".ts",
                    ".vue");

    /** 源代码目录默认列表 */
    private static final Set<String> DEFAULT_SOURCE_DIRS =
            Set.of("src/main/java", "src/test/java", "src/main/resources");

    /** 默认最大行数 */
    private static final int DEFAULT_MAX_LINES = 2000;

    /** 默认最大搜索深度 */
    private static final int DEFAULT_MAX_DEPTH = 10;

    private final ProjectPathResolver pathResolver;

    public SourceCodeTools(ProjectPathResolver pathResolver) {
        this.pathResolver = pathResolver;
        log.info("[SourceCodeTools] 初始化完成，允许的扩展名: {}", ALLOWED_EXTENSIONS);
    }

    /**
     * 列出项目源代码文件
     *
     * <p>支持按目录和扩展名过滤，返回符合条件的文件列表。
     *
     * @param directory 相对目录路径（如 src/main/java）
     * @param extension 文件扩展名过滤（如 .java）
     * @param maxDepth 最大搜索深度（1-10）
     * @return 文件列表
     */
    @AgentTool(
            name = "list_source_files",
            description = "列出项目中的源代码文件，支持按目录和扩展名过滤",
            category = ToolCategory.FILE,
            riskLevel = RiskLevel.LOW)
    public ToolResult listSourceFiles(
            @ToolParam(
                            name = "directory",
                            description = "相对目录路径，如 src/main/java，默认搜索所有源码目录",
                            defaultValue = "")
                    String directory,
            @ToolParam(
                            name = "extension",
                            description = "文件扩展名过滤，如 .java，默认列出所有允许类型",
                            defaultValue = "")
                    String extension,
            @ToolParam(name = "maxDepth", description = "最大递归深度（1-10），默认 5", defaultValue = "5")
                    int maxDepth) {

        log.info(
                "[SourceCodeTools] 执行 list_source_files: directory={}, extension={}, maxDepth={}",
                directory,
                extension,
                maxDepth);

        // 参数校验
        int effectiveDepth = Math.max(1, Math.min(maxDepth, DEFAULT_MAX_DEPTH));
        String effectiveExtension = sanitizeExtension(extension);

        try {
            List<FileInfo> allFiles = new ArrayList<>();

            // 确定搜索目录
            List<String> searchDirs = determineSearchDirs(directory);

            for (String dir : searchDirs) {
                try {
                    List<FileInfo> files =
                            pathResolver.findFiles(dir, effectiveExtension, effectiveDepth);
                    allFiles.addAll(files);
                } catch (Exception e) {
                    log.debug("[SourceCodeTools] 搜索目录 {} 时出错: {}", dir, e.getMessage());
                }
            }

            // 应用扩展名白名单过滤
            if (effectiveExtension == null || effectiveExtension.isEmpty()) {
                allFiles =
                        allFiles.stream().filter(f -> isAllowedExtension(f.extension())).toList();
            }

            // 构建返回数据
            List<Map<String, Object>> fileList = new ArrayList<>();
            for (FileInfo file : allFiles) {
                fileList.add(
                        Map.of(
                                "path", file.relativePath(),
                                "size", file.size(),
                                "sizeFormatted", formatSize(file.size()),
                                "lastModified", file.lastModified().toString(),
                                "extension", file.extension()));
            }

            log.info("[SourceCodeTools] list_source_files 返回 {} 个文件", fileList.size());

            return ToolResult.success(
                    Map.of(
                            "files",
                            fileList,
                            "total",
                            fileList.size(),
                            "directory",
                            directory != null && !directory.isBlank()
                                    ? directory
                                    : "all source dirs",
                            "extension",
                            effectiveExtension != null ? effectiveExtension : "all",
                            "hint",
                            "使用 read_source_file 读取具体文件内容"));

        } catch (SecurityException e) {
            log.warn("[SourceCodeTools] 安全检查失败: {}", e.getMessage());
            return ToolResult.failure("安全检查失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("[SourceCodeTools] list_source_files 执行失败: {}", e.getMessage(), e);
            return ToolResult.failure("文件搜索失败: " + e.getMessage());
        }
    }

    /**
     * 读取源代码文件内容
     *
     * <p>安全读取指定文件，支持编码和行数限制。
     *
     * @param path 文件相对路径
     * @param encoding 文件编码（默认 UTF-8）
     * @param maxLines 最大行数限制（默认 2000）
     * @return 文件内容
     */
    @AgentTool(
            name = "read_source_file",
            description = "读取指定源代码文件内容，支持编码和行数限制",
            category = ToolCategory.FILE,
            riskLevel = RiskLevel.LOW)
    public ToolResult readSourceFile(
            @ToolParam(
                            name = "path",
                            description = "文件相对路径，如 src/main/java/com/jonychen/AiApplication.java",
                            required = true)
                    String path,
            @ToolParam(name = "encoding", description = "文件编码，默认 UTF-8", defaultValue = "UTF-8")
                    String encoding,
            @ToolParam(name = "maxLines", description = "最大行数限制，默认 2000", defaultValue = "2000")
                    int maxLines) {

        log.info("[SourceCodeTools] 执行 read_source_file: path={}", path);

        // 参数校验
        if (path == null || path.isBlank()) {
            log.warn("[SourceCodeTools] read_source_file 参数错误: path 为空");
            return ToolResult.failure("文件路径不能为空");
        }

        // 扩展名检查
        if (!isAllowedExtension(path)) {
            log.warn("[SourceCodeTools] read_source_file 拒绝访问: 不支持的文件类型 {}", path);
            return ToolResult.failure("不支持的文件类型，允许的扩展名: " + String.join(", ", ALLOWED_EXTENSIONS));
        }

        int effectiveMaxLines = Math.max(1, Math.min(maxLines, DEFAULT_MAX_LINES));

        try {
            // 安全读取文件
            FileContent content = pathResolver.readFile(path, encoding, effectiveMaxLines);

            log.info(
                    "[SourceCodeTools] read_source_file 成功: {} 行，大小 {} bytes",
                    content.lineCount(),
                    content.content().length());

            return ToolResult.success(
                    Map.of(
                            "path", content.relativePath(),
                            "absolutePath", content.absolutePath(),
                            "content", content.content(),
                            "lines", content.lineCount(),
                            "truncated", content.truncated(),
                            "lastModified", content.lastModified().toString(),
                            "encoding", encoding != null ? encoding : "UTF-8"));

        } catch (FileNotFoundException e) {
            log.warn("[SourceCodeTools] 文件不存在: {}", path);
            return ToolResult.failure("文件不存在: " + path);
        } catch (SecurityException e) {
            log.warn("[SourceCodeTools] 安全检查失败: {}", e.getMessage());
            return ToolResult.failure("安全检查失败: " + e.getMessage());
        } catch (IOException e) {
            log.error("[SourceCodeTools] 读取文件失败: {}", e.getMessage(), e);
            return ToolResult.failure("读取文件失败: " + e.getMessage());
        }
    }

    // ==================== 私有方法 ====================

    /** 确定搜索目录 */
    private List<String> determineSearchDirs(String directory) {
        if (directory != null && !directory.isBlank()) {
            return List.of(directory);
        }
        // 默认搜索所有源码目录
        List<String> dirs = new ArrayList<>();
        for (String dir : DEFAULT_SOURCE_DIRS) {
            if (pathResolver.exists(dir) && pathResolver.isDirectory(dir)) {
                dirs.add(dir);
            }
        }
        // 如果没有找到默认目录，搜索根目录
        if (dirs.isEmpty()) {
            dirs.add("");
        }
        return dirs;
    }

    /** 清理扩展名参数 */
    private String sanitizeExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return null;
        }
        String ext = extension.trim();
        if (!ext.startsWith(".")) {
            ext = "." + ext;
        }
        return ext.toLowerCase();
    }

    /** 检查是否为允许的扩展名 */
    private boolean isAllowedExtension(String path) {
        if (path == null) {
            return false;
        }
        String lowerPath = path.toLowerCase();
        return ALLOWED_EXTENSIONS.stream().anyMatch(lowerPath::endsWith);
    }

    /** 格式化文件大小 */
    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + "B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1fKB", bytes / 1024.0);
        } else {
            return String.format("%.1fMB", bytes / (1024.0 * 1024));
        }
    }
}
