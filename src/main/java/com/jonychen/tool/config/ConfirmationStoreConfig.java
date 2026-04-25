package com.jonychen.tool.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jonychen.tool.confirmation.ConfirmationStore;
import com.jonychen.tool.confirmation.InMemoryConfirmationStore;
import com.jonychen.tool.confirmation.RedisConfirmationStore;

import lombok.extern.slf4j.Slf4j;

/**
 * 确认存储配置
 *
 * <p>根据环境和 Redis 配置自动选择合适的存储实现：
 *
 * <ul>
 *   <li>Redis 可用时：使用 {@link RedisConfirmationStore}（生产环境推荐）
 *   <li>Redis 不可用时：使用 {@link InMemoryConfirmationStore}（开发/测试环境）
 * </ul>
 *
 * <p>配置优先级：
 *
 * <ol>
 *   <li>如果 spring.data.redis.host 已配置 → RedisConfirmationStore
 *   <li>否则 → InMemoryConfirmationStore
 * </ol>
 *
 * @author jonychen
 */
@Slf4j
@Configuration
public class ConfirmationStoreConfig {

    /**
     * Redis 确认存储（优先）
     *
     * <p>当 Redis 可用时使用，支持分布式部署。
     */
    @Bean
    @Primary
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnProperty(name = "spring.data.redis.host", matchIfMissing = false)
    public ConfirmationStore redisConfirmationStore(
            StringRedisTemplate redisTemplate,
            @Qualifier("agentObjectMapper") ObjectMapper objectMapper) {
        log.info("[ConfirmationStoreConfig] 使用 Redis 确认存储（分布式模式）");
        return new RedisConfirmationStore(redisTemplate, objectMapper);
    }

    /**
     * 内存确认存储（备用）
     *
     * <p>当 Redis 不可用时使用，仅适用于单机开发/测试环境。
     */
    @Bean
    @ConditionalOnMissingBean(ConfirmationStore.class)
    public ConfirmationStore inMemoryConfirmationStore() {
        log.info("[ConfirmationStoreConfig] 使用内存确认存储（单机模式）");
        return new InMemoryConfirmationStore();
    }
}
