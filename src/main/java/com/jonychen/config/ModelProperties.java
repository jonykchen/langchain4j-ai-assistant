package com.jonychen.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.jonychen.model.ModelProvider;

/**
 * 多模型配置属性
 *
 * <p>从 application.properties 读取模型提供者配置，格式： model.providers.{name}.base-url=xxx
 * model.providers.{name}.model-name=xxx model.providers.{name}.weight=xxx
 * model.providers.{name}.priority=xxx model.providers.{name}.enabled=xxx
 *
 * <p>API Key 通过环境变量注入： model.providers.{name}.api-key=${ENV_VAR:}
 */
@Configuration
@ConfigurationProperties(prefix = "model")
public class ModelProperties {

    private static final Logger LOG = LoggerFactory.getLogger(ModelProperties.class);

    /** 模型提供者配置映射 key: 提供者名称（dashscope, zhipu, deepseek 等） value: 提供者配置 */
    private Map<String, ProviderConfig> providers = new HashMap<>();

    public Map<String, ProviderConfig> getProviders() {
        return providers;
    }

    public void setProviders(Map<String, ProviderConfig> providers) {
        this.providers = providers;
        LOG.info("加载模型配置: {} 个提供者", providers.size());
        providers.forEach(
                (name, config) -> {
                    LOG.info(
                            "  - {}: enabled={}, hasApiKey={}, baseUrl={}",
                            name,
                            config.enabled,
                            config.apiKey != null && !config.apiKey.isBlank(),
                            config.baseUrl);
                });
    }

    /** 获取所有启用的模型提供者，按优先级排序 */
    public List<ModelProvider> getEnabledProviders() {
        List<ModelProvider> result = new ArrayList<>();

        LOG.debug("检查模型配置，共 {} 个提供者", providers.size());

        providers.forEach(
                (name, config) -> {
                    LOG.debug(
                            "  检查 {}: enabled={}, hasApiKey={}, baseUrl={}, modelName={}",
                            name,
                            config.enabled,
                            config.apiKey != null && !config.apiKey.isBlank(),
                            config.baseUrl,
                            config.modelName);

                    if (config.enabled != null && config.enabled) {
                        ModelProvider provider =
                                new ModelProvider(
                                        name,
                                        config.baseUrl,
                                        config.apiKey,
                                        config.modelName,
                                        config.weight != null ? config.weight : 0,
                                        config.priority != null
                                                ? config.priority
                                                : Integer.MAX_VALUE,
                                        true);
                        if (provider.isValid()) {
                            result.add(provider);
                            LOG.info(
                                    "有效模型: {} (优先级={}, 权重={})",
                                    name,
                                    provider.priority(),
                                    provider.weight());
                        } else {
                            LOG.warn(
                                    "无效模型 {}: hasApiKey={}, hasBaseUrl={}, hasModelName={}",
                                    name,
                                    config.apiKey != null && !config.apiKey.isBlank(),
                                    config.baseUrl != null && !config.baseUrl.isBlank(),
                                    config.modelName != null && !config.modelName.isBlank());
                        }
                    }
                });

        // 按优先级排序
        result.sort((a, b) -> Integer.compare(a.priority(), b.priority()));
        LOG.info("最终有效模型数量: {}", result.size());
        return result;
    }

    /** 单个提供者配置 */
    public static class ProviderConfig {
        private String baseUrl;
        private String apiKey;
        private String modelName;
        private Integer weight;
        private Integer priority;
        private Boolean enabled;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }

        public Integer getWeight() {
            return weight;
        }

        public void setWeight(Integer weight) {
            this.weight = weight;
        }

        public Integer getPriority() {
            return priority;
        }

        public void setPriority(Integer priority) {
            this.priority = priority;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }
    }
}
