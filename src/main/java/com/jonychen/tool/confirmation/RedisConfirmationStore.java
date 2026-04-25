package com.jonychen.tool.confirmation;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Redis 确认存储实现（用于生产环境）
 *
 * <p>使用 Redis 存储确认状态，支持分布式部署场景。
 *
 * <h2>特性</h2>
 *
 * <ul>
 *   <li>自动 TTL 过期
 *   <li>集群间状态共享
 *   <li>高性能读写
 * </ul>
 *
 * <h2>Key 格式</h2>
 *
 * {@code tool:confirmation:{confirmationId}}
 *
 * <p>Bean 创建由 {@link com.jonychen.tool.config.ConfirmationStoreConfig} 管理。
 *
 * @author jonychen
 */
@Slf4j
public class RedisConfirmationStore implements ConfirmationStore {

    private static final String KEY_PREFIX = "tool:confirmation:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisConfirmationStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        log.info("[RedisConfirmationStore] 初始化完成");
    }

    @Override
    public void store(String confirmationId, PendingConfirmation confirmation, Duration ttl) {
        String key = KEY_PREFIX + confirmationId;
        try {
            String json = objectMapper.writeValueAsString(confirmation);
            redisTemplate.opsForValue().set(key, json, ttl.toMillis(), TimeUnit.MILLISECONDS);
            log.debug(
                    "[RedisConfirmationStore] 存储: id={}, ttl={}s",
                    confirmationId,
                    ttl.getSeconds());
        } catch (JsonProcessingException e) {
            log.error("[RedisConfirmationStore] 序列化失败: {}", e.getMessage());
        }
    }

    @Override
    public Optional<PendingConfirmation> get(String confirmationId) {
        String key = KEY_PREFIX + confirmationId;
        String json = redisTemplate.opsForValue().get(key);

        if (json == null) {
            return Optional.empty();
        }

        try {
            PendingConfirmation confirmation =
                    objectMapper.readValue(json, PendingConfirmation.class);
            return Optional.of(confirmation);
        } catch (JsonProcessingException e) {
            log.error("[RedisConfirmationStore] 反序列化失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void update(String confirmationId, PendingConfirmation confirmation) {
        String key = KEY_PREFIX + confirmationId;
        Long ttl = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);

        if (ttl != null && ttl > 0) {
            try {
                String json = objectMapper.writeValueAsString(confirmation);
                redisTemplate.opsForValue().set(key, json, ttl, TimeUnit.MILLISECONDS);
                log.debug(
                        "[RedisConfirmationStore] 更新: id={}, remainingTtl={}ms",
                        confirmationId,
                        ttl);
            } catch (JsonProcessingException e) {
                log.error("[RedisConfirmationStore] 序列化失败: {}", e.getMessage());
            }
        } else {
            // 已过期或不存在，使用默认 TTL
            store(confirmationId, confirmation, Duration.ofMinutes(5));
            log.debug("[RedisConfirmationStore] 更新（使用默认 TTL）: id={}", confirmationId);
        }
    }

    @Override
    public void delete(String confirmationId) {
        String key = KEY_PREFIX + confirmationId;
        redisTemplate.delete(key);
        log.debug("[RedisConfirmationStore] 删除: id={}", confirmationId);
    }

    @Override
    public boolean exists(String confirmationId) {
        String key = KEY_PREFIX + confirmationId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    @Override
    public long getRemainingTtl(String confirmationId) {
        String key = KEY_PREFIX + confirmationId;
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl != null && ttl > 0 ? ttl : -1;
    }
}
