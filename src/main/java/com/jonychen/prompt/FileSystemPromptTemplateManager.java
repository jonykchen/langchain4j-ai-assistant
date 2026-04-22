package com.jonychen.prompt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.extern.slf4j.Slf4j;

/** 基于文件系统的 Prompt 模板管理器 模板以 YAML 格式存储在指定目录 */
@Slf4j
@Component
public class FileSystemPromptTemplateManager implements PromptTemplateManager {

    private final Path templatesPath;
    private final ObjectMapper yamlMapper;

    public FileSystemPromptTemplateManager(
            @Value("${app.prompt.templates-path:classpath:prompts/}") String templatesPath) {
        // 处理 classpath 前缀
        if (templatesPath.startsWith("classpath:")) {
            this.templatesPath =
                    Paths.get("src/main/resources", templatesPath.substring("classpath:".length()));
        } else {
            this.templatesPath = Paths.get(templatesPath);
        }

        this.yamlMapper = new ObjectMapper(new YAMLFactory()).registerModule(new JavaTimeModule());

        log.info("Prompt 模板目录: {}", this.templatesPath);
    }

    @Override
    public Optional<PromptTemplate> loadTemplate(String name) {
        return loadTemplate(name, null);
    }

    @Override
    public Optional<PromptTemplate> loadTemplate(String name, String version) {
        Path templateFile = resolveTemplateFile(name, version);

        if (!Files.exists(templateFile)) {
            log.debug("模板不存在: {}", templateFile);
            return Optional.empty();
        }

        try {
            String content = Files.readString(templateFile);
            PromptTemplate template = yamlMapper.readValue(content, PromptTemplate.class);
            return Optional.of(template);
        } catch (IOException e) {
            log.error("加载模板失败: name={}, file={}", name, templateFile, e);
            return Optional.empty();
        }
    }

    @Override
    public String render(String templateName, Map<String, Object> variables) {
        return render(templateName, variables, true);
    }

    @Override
    public String render(String templateName, Map<String, Object> variables, boolean validate) {
        Optional<PromptTemplate> templateOpt = loadTemplate(templateName);

        if (templateOpt.isEmpty()) {
            throw new IllegalArgumentException("模板不存在: " + templateName);
        }

        PromptTemplate template = templateOpt.get();

        // 验证必需变量
        if (validate && !template.validateVariables(variables)) {
            List<String> missing =
                    template.variables().stream()
                            .filter(TemplateVariable::required)
                            .filter(v -> variables == null || !variables.containsKey(v.name()))
                            .map(TemplateVariable::name)
                            .collect(Collectors.toList());

            throw new IllegalArgumentException("缺少必需变量: " + missing);
        }

        return template.render(variables);
    }

    @Override
    public void saveTemplate(PromptTemplate template) {
        Path templateFile = resolveTemplateFile(template.name(), template.version());

        try {
            // 确保目录存在
            Files.createDirectories(templateFile.getParent());

            // 写入 YAML 文件
            String content = yamlMapper.writeValueAsString(template);
            Files.writeString(
                    templateFile,
                    content,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            log.info("保存模板成功: name={}, file={}", template.name(), templateFile);
        } catch (IOException e) {
            log.error("保存模板失败: name={}", template.name(), e);
            throw new RuntimeException("保存模板失败: " + template.name(), e);
        }
    }

    @Override
    public List<TemplateInfo> listTemplates() {
        if (!Files.exists(templatesPath)) {
            return Collections.emptyList();
        }

        try (Stream<Path> stream = Files.walk(templatesPath, 2)) {
            return stream.filter(
                            p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
                    .map(this::loadTemplateInfoFromFile)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(TemplateInfo::name))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("列出模板失败", e);
            return Collections.emptyList();
        }
    }

    @Override
    public void deleteTemplate(String name) {
        Path templateFile = resolveTemplateFile(name, null);

        if (Files.exists(templateFile)) {
            try {
                Files.delete(templateFile);
                log.info("删除模板成功: name={}", name);
            } catch (IOException e) {
                log.error("删除模板失败: name={}", name, e);
                throw new RuntimeException("删除模板失败: " + name, e);
            }
        }
    }

    @Override
    public boolean existsTemplate(String name) {
        return loadTemplate(name).isPresent();
    }

    // ========== 私有方法 ==========

    private Path resolveTemplateFile(String name, String version) {
        String filename = name;
        if (version != null && !version.isEmpty()) {
            filename = name + "-" + version;
        }
        return templatesPath.resolve(filename + ".yaml");
    }

    private TemplateInfo loadTemplateInfoFromFile(Path file) {
        try {
            String content = Files.readString(file);
            PromptTemplate template = yamlMapper.readValue(content, PromptTemplate.class);

            return new TemplateInfo(
                    template.name(),
                    template.version(),
                    template.description(),
                    template.metadata() != null
                            ? template.metadata().updatedAt()
                            : LocalDateTime.now());
        } catch (IOException e) {
            log.warn("解析模板文件失败: {}", file, e);
            return null;
        }
    }
}
