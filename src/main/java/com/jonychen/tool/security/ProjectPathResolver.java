package com.jonychen.tool.security;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 项目路径安全沙箱
 *
 * <p>提供安全的文件系统操作，防止路径遍历攻击。
 *
 * <h2>安全机制</h2>
 *
 * <ol>
 *   <li>路径遍历检测（..、~/）
 *   <li>敏感路径黑名单
 *   <li>沙箱边界检查（确保路径在项目根目录内）
 *   <li>符号链接检测（防止通过符号链接逃逸沙箱）
 *   <li>文件大小限制
 * </ol>
 *
 * @author jonychen
 */
@Component
public class ProjectPathResolver {

    private static final Logger log = LoggerFactory.getLogger(ProjectPathResolver.class);

    /** 路径遍历检测正则 */
    private static final Pattern PATH_TRAVERSAL_PATTERN =
            Pattern.compile("(\\.\\.[\\/\\\\]|~[\\/\\\\])");

    /** 敏感目录/文件黑名单 */
    private static final Set<String> SENSITIVE_PATHS =
            Set.of(
                    ".env",
                    ".git",
                    ".ssh",
                    "credentials",
                    "secrets",
                    "private",
                    "id_rsa",
                    "id_ed25519",
                    ".npmrc",
                    ".pypirc",
                    "settings.xml");

    /** 排除目录黑名单（不参与文件搜索） */
    private static final Set<String> EXCLUDED_DIRS =
            Set.of("target", "node_modules", ".git", ".idea", "logs", "data", "build", "dist");

    /** 默认最大文件大小（100KB） */
    private static final long DEFAULT_MAX_FILE_SIZE = 100 * 1024;

    /** 默认最大搜索深度 */
    private static final int DEFAULT_MAX_DEPTH = 10;

    /** 默认最大结果数量 */
    private static final int DEFAULT_MAX_RESULTS = 1000;

    /** 项目根目录 */
    private final Path projectRoot;

    /** 最大文件大小 */
    private final long maxFileSize;

    /** 允许的路径前缀白名单（null 表示允许全部） */
    private final Set<String> allowedPathPrefixes;

    /**
     * 构造函数
     *
     * @param projectRootConfig 项目根目录配置
     * @param maxFileSizeConfig 最大文件大小配置
     * @param allowedPathsConfig 允许的路径前缀配置
     */
    public ProjectPathResolver(
            @Value("${agent.file.project-root:#{null}}") String projectRootConfig,
            @Value("${agent.file.max-file-size:102400}") long maxFileSizeConfig,
            @Value("${agent.file.allowed-paths:#{null}}") String allowedPathsConfig) {

        // 确定项目根目录
        this.projectRoot = determineProjectRoot(projectRootConfig);
        this.maxFileSize = maxFileSizeConfig > 0 ? maxFileSizeConfig : DEFAULT_MAX_FILE_SIZE;

        // 解析允许的路径前缀
        this.allowedPathPrefixes = parseAllowedPaths(allowedPathsConfig);

        log.info(
                "[ProjectPathResolver] 初始化完成: projectRoot={}, maxFileSize={}KB, allowedPrefixes={}",
                projectRoot,
                maxFileSize / 1024,
                allowedPathPrefixes);
    }

    /** 获取项目根目录 */
    public Path getProjectRoot() {
        return projectRoot;
    }

    /**
     * 安全解析相对路径
     *
     * <p>执行以下安全检查：
     *
     * <ol>
     *   <li>路径遍历检测
     *   <li>敏感路径检测
     *   <li>沙箱边界检查
     *   <li>符号链接检查
     * </ol>
     *
     * @param relativePath 相对路径
     * @return 安全验证后的绝对路径
     * @throws SecurityException 路径不安全时抛出
     */
    public Path resolveSecurePath(String relativePath) {
        // 1. 空值检查
        if (relativePath == null || relativePath.isBlank()) {
            return projectRoot;
        }

        // 2. 路径遍历检测
        if (PATH_TRAVERSAL_PATTERN.matcher(relativePath).find()) {
            log.warn("[ProjectPathResolver] 检测到路径遍历攻击尝试: {}", relativePath);
            throw new SecurityException("路径包含非法字符: 不允许使用 .. 或 ~");
        }

        // 3. 绝对路径检测
        String normalized = relativePath.replace('\\', '/');
        if (normalized.startsWith("/")
                || (normalized.length() > 1 && normalized.charAt(1) == ':')) {
            log.warn("[ProjectPathResolver] 检测到绝对路径尝试: {}", relativePath);
            throw new SecurityException("不允许使用绝对路径");
        }

        // 4. 敏感路径检测
        String lowerPath = normalized.toLowerCase();
        for (String sensitive : SENSITIVE_PATHS) {
            if (lowerPath.contains(sensitive.toLowerCase())) {
                log.warn("[ProjectPathResolver] 检测到敏感路径访问尝试: {}", relativePath);
                throw new SecurityException("禁止访问敏感路径: " + sensitive);
            }
        }

        // 5. 解析并规范化
        Path resolved = projectRoot.resolve(relativePath).normalize();

        // 6. 沙箱边界检查
        if (!resolved.startsWith(projectRoot)) {
            log.warn("[ProjectPathResolver] 路径逃逸沙箱: {} -> {}", relativePath, resolved);
            throw new SecurityException("路径不在项目根目录内");
        }

        // 7. 路径前缀白名单检查
        if (allowedPathPrefixes != null && !allowedPathPrefixes.isEmpty()) {
            String relativeToRoot = projectRoot.relativize(resolved).toString().replace('\\', '/');
            boolean isAllowed = allowedPathPrefixes.stream().anyMatch(relativeToRoot::startsWith);
            if (!isAllowed) {
                log.warn("[ProjectPathResolver] 路径不在白名单前缀内: {}", relativePath);
                throw new SecurityException("路径不在允许的目录范围内");
            }
        }

        // 8. 符号链接检查（如果路径存在）
        if (Files.exists(resolved)) {
            try {
                Path realPath = resolved.toRealPath(LinkOption.NOFOLLOW_LINKS);
                // 如果是符号链接，检查目标是否在沙箱内
                if (!realPath.equals(resolved) && !realPath.startsWith(projectRoot)) {
                    log.warn("[ProjectPathResolver] 符号链接指向项目外: {} -> {}", resolved, realPath);
                    throw new SecurityException("符号链接指向项目根目录外");
                }
            } catch (IOException e) {
                log.debug("[ProjectPathResolver] 无法解析符号链接: {}", e.getMessage());
            }
        }

        return resolved;
    }

    /**
     * 安全读取文件内容
     *
     * @param relativePath 文件相对路径
     * @return 文件内容
     * @throws IOException 文件操作失败
     * @throws SecurityException 安全检查失败
     */
    public FileContent readFile(String relativePath) throws IOException {
        return readFile(relativePath, StandardCharsets.UTF_8.name(), Integer.MAX_VALUE);
    }

    /**
     * 安全读取文件内容（带编码和行数限制）
     *
     * @param relativePath 文件相对路径
     * @param encoding 文件编码
     * @param maxLines 最大行数限制
     * @return 文件内容
     * @throws IOException 文件操作失败
     * @throws SecurityException 安全检查失败
     */
    public FileContent readFile(String relativePath, String encoding, int maxLines)
            throws IOException {
        // 1. 安全解析路径
        Path path = resolveSecurePath(relativePath);

        // 2. 文件存在检查
        if (!Files.exists(path)) {
            throw new FileNotFoundException("文件不存在: " + relativePath);
        }

        // 3. 文件类型检查
        if (!Files.isRegularFile(path)) {
            throw new IOException("路径不是常规文件: " + relativePath);
        }

        // 4. 文件大小检查
        long size = Files.size(path);
        if (size > maxFileSize) {
            throw new IOException(
                    String.format("文件过大: %d bytes，最大允许: %d bytes", size, maxFileSize));
        }

        // 5. 读取内容
        Charset charset = parseCharset(encoding);
        List<String> lines = Files.readAllLines(path, charset);

        // 6. 行数限制
        boolean truncated = false;
        if (lines.size() > maxLines) {
            lines = lines.subList(0, maxLines);
            truncated = true;
        }

        // 7. 获取最后修改时间
        Instant lastModified = Files.getLastModifiedTime(path).toInstant();

        // 8. 返回结果
        return new FileContent(
                relativePath,
                path.toString(),
                String.join("\n", lines),
                lines.size(),
                truncated,
                lastModified);
    }

    /**
     * 安全查找文件
     *
     * @param directory 起始目录
     * @param extension 文件扩展名过滤（如 ".java"）
     * @return 文件信息列表
     * @throws IOException 文件操作失败
     * @throws SecurityException 安全检查失败
     */
    public List<FileInfo> findFiles(String directory, String extension) throws IOException {
        return findFiles(directory, extension, DEFAULT_MAX_DEPTH);
    }

    /**
     * 安全查找文件（带深度限制）
     *
     * @param directory 起始目录
     * @param extension 文件扩展名过滤
     * @param maxDepth 最大搜索深度
     * @return 文件信息列表
     * @throws IOException 文件操作失败
     * @throws SecurityException 安全检查失败
     */
    public List<FileInfo> findFiles(String directory, String extension, int maxDepth)
            throws IOException {
        // 1. 安全解析路径
        Path startPath = resolveSecurePath(directory);

        // 2. 目录存在检查
        if (!Files.exists(startPath)) {
            return Collections.emptyList();
        }

        if (!Files.isDirectory(startPath)) {
            throw new IOException("路径不是目录: " + directory);
        }

        // 3. 深度限制
        int effectiveDepth = Math.min(Math.max(maxDepth, 1), DEFAULT_MAX_DEPTH);

        // 4. 构建文件过滤器
        BiPredicate<Path, BasicFileAttributes> matcher =
                (path, attrs) -> {
                    // 跳过黑名单目录
                    for (String excluded : EXCLUDED_DIRS) {
                        if (path.startsWith(projectRoot.resolve(excluded))) {
                            return false;
                        }
                    }

                    // 只匹配常规文件
                    if (!attrs.isRegularFile()) {
                        return false;
                    }

                    // 扩展名过滤
                    if (extension != null && !extension.isEmpty()) {
                        String fileName = path.getFileName().toString();
                        return fileName.endsWith(extension);
                    }

                    return true;
                };

        // 5. 执行搜索
        try (Stream<Path> stream = Files.find(startPath, effectiveDepth, matcher)) {
            return stream.limit(DEFAULT_MAX_RESULTS)
                    .map(this::toFileInfo)
                    .collect(Collectors.toList());
        }
    }

    /** 检查路径是否存在 */
    public boolean exists(String relativePath) {
        try {
            Path path = resolveSecurePath(relativePath);
            return Files.exists(path);
        } catch (SecurityException e) {
            return false;
        }
    }

    /** 检查路径是否是目录 */
    public boolean isDirectory(String relativePath) {
        try {
            Path path = resolveSecurePath(relativePath);
            return Files.isDirectory(path);
        } catch (SecurityException e) {
            return false;
        }
    }

    /** 检查路径是否是常规文件 */
    public boolean isRegularFile(String relativePath) {
        try {
            Path path = resolveSecurePath(relativePath);
            return Files.isRegularFile(path);
        } catch (SecurityException e) {
            return false;
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 确定项目根目录
     *
     * <p>优先级：配置 > 环境变量 > 当前工作目录
     */
    private Path determineProjectRoot(String config) {
        if (config != null && !config.isBlank()) {
            Path configured = Paths.get(config).toAbsolutePath().normalize();
            if (Files.isDirectory(configured)) {
                return configured;
            }
            log.warn("[ProjectPathResolver] 配置的项目根目录不存在: {}", config);
        }

        // 尝试从环境变量获取
        String envRoot = System.getenv("PROJECT_ROOT");
        if (envRoot != null && !envRoot.isBlank()) {
            Path envPath = Paths.get(envRoot).toAbsolutePath().normalize();
            if (Files.isDirectory(envPath)) {
                return envPath;
            }
        }

        // 使用当前工作目录
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        log.info("[ProjectPathResolver] 使用当前工作目录作为项目根目录: {}", cwd);
        return cwd;
    }

    /** 解析允许的路径前缀配置 */
    private Set<String> parseAllowedPaths(String config) {
        if (config == null || config.isBlank()) {
            return Collections.emptySet();
        }

        return Arrays.stream(config.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.replace('\\', '/'))
                .collect(Collectors.toSet());
    }

    /** 解析字符编码 */
    private Charset parseCharset(String encoding) {
        if (encoding == null || encoding.isBlank()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(encoding);
        } catch (Exception e) {
            log.warn("[ProjectPathResolver] 不支持的编码格式: {}，使用 UTF-8", encoding);
            return StandardCharsets.UTF_8;
        }
    }

    /** 转换路径为 FileInfo */
    private FileInfo toFileInfo(Path path) {
        try {
            String relativePath = projectRoot.relativize(path).toString().replace('\\', '/');
            long size = Files.size(path);
            Instant lastModified = Files.getLastModifiedTime(path).toInstant();
            String fileName = path.getFileName().toString();
            String extension =
                    fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.')) : "";

            return new FileInfo(relativePath, path.toString(), size, lastModified, extension);
        } catch (IOException e) {
            log.warn("[ProjectPathResolver] 无法获取文件信息: {}", path, e);
            return new FileInfo(path.toString(), path.toString(), 0, Instant.now(), "");
        }
    }

    // ==================== 辅助 Record ====================

    /** 文件内容 */
    public record FileContent(
            String relativePath,
            String absolutePath,
            String content,
            int lineCount,
            boolean truncated,
            Instant lastModified) {}

    /** 文件信息 */
    public record FileInfo(
            String relativePath,
            String absolutePath,
            long size,
            Instant lastModified,
            String extension) {}
}
