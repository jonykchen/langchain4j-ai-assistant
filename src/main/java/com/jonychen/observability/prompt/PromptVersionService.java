package com.jonychen.observability.prompt;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Prompt 版本管理服务
 *
 * @author jonychen
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptVersionService {

    private final PromptTemplateRepository templateRepository;
    private final ObjectMapper objectMapper;

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

    /** 创建新模板 */
    @Transactional
    public PromptTemplateEntity createTemplate(CreatePromptRequest request) {
        // 检查是否已存在相同名称和版本
        if (templateRepository.existsByNameAndVersion(request.name(), request.version())) {
            throw new IllegalArgumentException(
                    "Template already exists: " + request.name() + "@" + request.version());
        }

        // 解析模板变量
        String variablesJson = parseVariables(request.content());

        PromptTemplateEntity template = new PromptTemplateEntity();
        template.setName(request.name());
        template.setVersion(request.version() != null ? request.version() : "1.0.0");
        template.setDescription(request.description());
        template.setContent(request.content());
        template.setVariables(variablesJson);
        template.setTags(request.tags());
        template.setCreatedBy(request.createdBy());

        // 如果是第一个版本，自动激活
        if (templateRepository.countByName(request.name()) == 0) {
            template.setActive(true);
        }

        templateRepository.save(template);
        log.info("Created prompt template: {}@{}", template.getName(), template.getVersion());
        return template;
    }

    /** 创建新版本 */
    @Transactional
    public PromptTemplateEntity createVersion(
            String name, String newContent, String changeDescription) {
        PromptTemplateEntity latest =
                templateRepository
                        .findLatestByName(name)
                        .orElseThrow(
                                () -> new IllegalArgumentException("Template not found: " + name));

        // 递增版本号
        String newVersion = incrementVersion(latest.getVersion());

        String variablesJson = parseVariables(newContent);

        PromptTemplateEntity newTemplate = new PromptTemplateEntity();
        newTemplate.setName(name);
        newTemplate.setVersion(newVersion);
        newTemplate.setDescription(changeDescription);
        newTemplate.setContent(newContent);
        newTemplate.setVariables(variablesJson);
        newTemplate.setTags(latest.getTags());
        newTemplate.setCreatedBy(latest.getCreatedBy());
        newTemplate.setActive(false); // 新版本默认不激活

        templateRepository.save(newTemplate);
        log.info("Created new version: {}@{}", name, newVersion);
        return newTemplate;
    }

    /** 激活版本 */
    @Transactional
    public void activateVersion(String name, String version) {
        // 停用当前所有版本
        templateRepository.deactivateAllVersions(name);

        // 激活指定版本
        PromptTemplateEntity template =
                templateRepository
                        .findByNameAndVersion(name, version)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Version not found: " + name + "@" + version));
        template.setActive(true);

        templateRepository.save(template);
        log.info("Activated version: {}@{}", name, version);
    }

    /** 推送到生产环境 */
    @Transactional
    public void promoteToProduction(String name, String version) {
        // 移除当前生产版本标记
        templateRepository.removeProductionFlag(name);

        PromptTemplateEntity template =
                templateRepository
                        .findByNameAndVersion(name, version)
                        .orElseThrow(() -> new IllegalArgumentException("Version not found"));

        template.setProduction(true);
        templateRepository.save(template);

        log.info("Promoted to production: {}@{}", name, version);
    }

    /** 回滚到指定版本 */
    @Transactional
    public void rollback(String name, String targetVersion) {
        activateVersion(name, targetVersion);
        log.warn("Rolled back template {} to version {}", name, targetVersion);
    }

    /** 获取激活版本 */
    public Optional<PromptTemplateEntity> getActiveVersion(String name) {
        return templateRepository.findByNameAndActiveTrue(name);
    }

    /** 获取生产版本 */
    public Optional<PromptTemplateEntity> getProductionVersion(String name) {
        return templateRepository.findByNameAndProductionTrue(name);
    }

    /** 获取最新版本 */
    public Optional<PromptTemplateEntity> getLatestVersion(String name) {
        return templateRepository.findLatestByName(name);
    }

    /** 获取版本历史 */
    public List<PromptTemplateEntity> getVersionHistory(String name) {
        return templateRepository.findByNameOrderByCreatedAtDesc(name);
    }

    /** 获取所有模板名称 */
    public List<String> getAllTemplateNames() {
        return templateRepository.findAllNames();
    }

    /** 获取所有激活的模板 */
    public List<PromptTemplateEntity> getAllActiveTemplates() {
        return templateRepository.findByActiveTrue();
    }

    /** 渲染模板 */
    public String render(String name, Map<String, Object> variables) {
        PromptTemplateEntity template =
                getActiveVersion(name)
                        .orElseThrow(
                                () -> new IllegalArgumentException("Template not found: " + name));

        return renderTemplate(template.getContent(), variables);
    }

    /** 使用指定版本渲染模板 */
    public String renderVersion(String name, String version, Map<String, Object> variables) {
        PromptTemplateEntity template =
                templateRepository
                        .findByNameAndVersion(name, version)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Version not found: " + name + "@" + version));

        return renderTemplate(template.getContent(), variables);
    }

    /** 配置 A/B 测试 */
    @Transactional
    public void configureABTest(String name, ABTestConfigRequest config) {
        PromptTemplateEntity baseline =
                templateRepository
                        .findByNameAndVersion(name, config.baselineVersion())
                        .orElseThrow(
                                () -> new IllegalArgumentException("Baseline version not found"));

        PromptTemplateEntity variant =
                templateRepository
                        .findByNameAndVersion(name, config.variantVersion())
                        .orElseThrow(
                                () -> new IllegalArgumentException("Variant version not found"));

        // 先清除所有 A/B 测试配置
        List<PromptTemplateEntity> existingABTests = templateRepository.findABTestTemplates(name);
        for (PromptTemplateEntity t : existingABTests) {
            t.setAbTestEnabled(false);
            t.setAbTestTrafficPercentage(BigDecimal.ZERO);
        }
        templateRepository.saveAll(existingABTests);

        // 配置基线
        baseline.setAbTestEnabled(true);
        baseline.setAbTestTrafficPercentage(BigDecimal.valueOf(100 - config.trafficPercentage()));
        baseline.setAbTestVariantName("baseline");
        baseline.setAbTestBaselineVersion(null);

        // 配置变体
        variant.setAbTestEnabled(true);
        variant.setAbTestTrafficPercentage(BigDecimal.valueOf(config.trafficPercentage()));
        variant.setAbTestVariantName(config.variantName());
        variant.setAbTestBaselineVersion(config.baselineVersion());

        templateRepository.saveAll(List.of(baseline, variant));
        log.info(
                "Configured A/B test for {}: baseline={}, variant={}, split={}/{}",
                name,
                config.baselineVersion(),
                config.variantVersion(),
                100 - config.trafficPercentage(),
                config.trafficPercentage());
    }

    /** 获取 A/B 测试版本 */
    public PromptTemplateEntity getABTestVersion(String name, String userId) {
        List<PromptTemplateEntity> abTestTemplates = templateRepository.findABTestTemplates(name);

        if (abTestTemplates.isEmpty()) {
            return getActiveVersion(name).orElse(null);
        }

        // 根据用户 ID 确定分配哪个版本
        int hash = Math.abs(userId != null ? userId.hashCode() : (int) (Math.random() * 10000));
        BigDecimal percentage = BigDecimal.valueOf(hash % 100);

        for (PromptTemplateEntity template : abTestTemplates) {
            if (template.getAbTestEnabled() != null && template.getAbTestEnabled()) {
                if (percentage.compareTo(template.getAbTestTrafficPercentage()) < 0) {
                    return template;
                }
                percentage = percentage.subtract(template.getAbTestTrafficPercentage());
            }
        }

        return abTestTemplates.get(0);
    }

    /** 停止 A/B 测试 */
    @Transactional
    public void stopABTest(String name) {
        List<PromptTemplateEntity> abTestTemplates = templateRepository.findABTestTemplates(name);
        for (PromptTemplateEntity t : abTestTemplates) {
            t.setAbTestEnabled(false);
            t.setAbTestTrafficPercentage(BigDecimal.ZERO);
        }
        templateRepository.saveAll(abTestTemplates);
        log.info("Stopped A/B test for {}", name);
    }

    /** 比较两个版本 */
    public PromptDiff compareVersions(String name, String version1, String version2) {
        PromptTemplateEntity t1 =
                templateRepository
                        .findByNameAndVersion(name, version1)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Version not found: " + version1));
        PromptTemplateEntity t2 =
                templateRepository
                        .findByNameAndVersion(name, version2)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Version not found: " + version2));

        return new PromptDiff(
                name,
                version1,
                version2,
                t1.getContent(),
                t2.getContent(),
                computeDiff(t1.getContent(), t2.getContent()));
    }

    /** 记录使用统计 */
    @Transactional
    public void recordUse(
            String name, String version, boolean success, long responseTime, long tokenUsage) {
        templateRepository
                .findByNameAndVersion(name, version)
                .ifPresent(
                        template -> {
                            template.recordUse(success);
                            // 更新平均值（简单移动平均）
                            long total = template.getTotalUses();
                            if (template.getAvgResponseTime() == null) {
                                template.setAvgResponseTime(BigDecimal.valueOf(responseTime));
                                template.setAvgTokenUsage(BigDecimal.valueOf(tokenUsage));
                            } else {
                                // 新平均值 = 旧平均值 * (total - 1) / total + 新值 / total
                                BigDecimal avgResp = template.getAvgResponseTime();
                                BigDecimal avgToken = template.getAvgTokenUsage();
                                BigDecimal totalDecimal = BigDecimal.valueOf(total);
                                BigDecimal totalMinusOne = BigDecimal.valueOf(total - 1);

                                template.setAvgResponseTime(
                                        avgResp.multiply(totalMinusOne)
                                                .add(BigDecimal.valueOf(responseTime))
                                                .divide(totalDecimal, 2, RoundingMode.HALF_UP));
                                template.setAvgTokenUsage(
                                        avgToken.multiply(totalMinusOne)
                                                .add(BigDecimal.valueOf(tokenUsage))
                                                .divide(totalDecimal, 2, RoundingMode.HALF_UP));
                            }
                            templateRepository.save(template);
                        });
    }

    /** 删除模板 */
    @Transactional
    public void deleteTemplate(String name, String version) {
        PromptTemplateEntity template =
                templateRepository
                        .findByNameAndVersion(name, version)
                        .orElseThrow(() -> new IllegalArgumentException("Version not found"));

        if (template.getActive()) {
            throw new IllegalStateException("Cannot delete active version");
        }

        templateRepository.delete(template);
        log.info("Deleted template: {}@{}", name, version);
    }

    // ==================== 私有方法 ====================

    private String parseVariables(String content) {
        Set<String> varNames = new HashSet<>();
        Matcher matcher = VARIABLE_PATTERN.matcher(content);
        while (matcher.find()) {
            varNames.add(matcher.group(1));
        }

        try {
            return objectMapper.writeValueAsString(
                    varNames.stream()
                            .map(
                                    name -> {
                                        Map<String, Object> var = new LinkedHashMap<>();
                                        var.put("name", name);
                                        var.put("required", true);
                                        return var;
                                    })
                            .toList());
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private String renderTemplate(String template, Map<String, Object> variables) {
        if (variables == null) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
        }
        return result;
    }

    private String incrementVersion(String version) {
        String[] parts = version.split("\\.");
        if (parts.length < 2) {
            return version + ".1.0";
        }
        int minor = Integer.parseInt(parts[1]) + 1;
        return parts[0] + "." + minor + "." + (parts.length > 2 ? parts[2] : "0");
    }

    private List<String> computeDiff(String content1, String content2) {
        String[] lines1 = content1.split("\n");
        String[] lines2 = content2.split("\n");
        List<String> diff = new ArrayList<>();

        int max = Math.max(lines1.length, lines2.length);
        for (int i = 0; i < max; i++) {
            String l1 = i < lines1.length ? lines1[i] : "";
            String l2 = i < lines2.length ? lines2[i] : "";
            if (!l1.equals(l2)) {
                diff.add("Line " + (i + 1) + ":");
                if (!l1.isEmpty()) {
                    diff.add("- " + l1);
                }
                if (!l2.isEmpty()) {
                    diff.add("+ " + l2);
                }
            }
        }
        return diff;
    }

    // DTOs
    public record CreatePromptRequest(
            String name,
            String version,
            String description,
            String content,
            String tags,
            String createdBy) {}

    public record ABTestConfigRequest(
            String baselineVersion,
            String variantVersion,
            String variantName,
            double trafficPercentage) {}

    public record PromptDiff(
            String name,
            String version1,
            String version2,
            String content1,
            String content2,
            List<String> changes) {}
}
