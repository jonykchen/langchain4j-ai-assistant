package com.jonychen.tool.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jonychen.tool.ToolResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 幂等性管理器
 *
 * <p>基于 Redis 实现工具调用的幂等性控制
 *
 * @author jonychen
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyManager {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    /** 幂等键 TTL（默认 24 小时） */
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    /** 结果缓存前缀 */
    private static final String RESULT_SUFFIX = ":result";

    /**
     * 生成幂等键
     *
     * @param toolName 工具名称
     * @param params 参数
     * @param sessionId 会话ID
     * @return 幂等键
     */
    public String generateIdempotencyKey(
            String toolName, Map<String, Object> params, String sessionId) {
        try {
            String paramJson = objectMapper.writeValueAsString(params);
            String paramHash =
                    DigestUtils.md5DigestAsHex(paramJson.getBytes(StandardCharsets.UTF_8));
            return String.format("idempotent:%s:%s:%s", toolName, sessionId, paramHash);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize params for idempotency key: {}", e.getMessage());
            // 降级方案：使用参数的 hashCode
            return String.format("idempotent:%s:%s:%d", toolName, sessionId, params.hashCode());
        }
    }

    /**
     * 检查并设置幂等键（原子操作）
     *
     * @param idempotencyKey 幂等键
     * @return true 表示首次执行，false 表示重复请求
     */
    public boolean checkAndSet(String idempotencyKey) {
        Boolean success =
                redisTemplate
                        .opsForValue()
                        .setIfAbsent(idempotencyKey, "processing", IDEMPOTENCY_TTL);
        boolean isFirst = Boolean.TRUE.equals(success);

        if (!isFirst) {
            log.trace("Idempotency check failed for key: {}", idempotencyKey);
        }

        return isFirst;
    }

    /**
     * 缓存执行结果
     *
     * @param idempotencyKey 幂等键
     * @param result 执行结果
     */
    public void cacheResult(String idempotencyKey, ToolResult result) {
        try {
            String resultJson = objectMapper.writeValueAsString(result);
            redisTemplate
                    .opsForValue()
                    .set(idempotencyKey + RESULT_SUFFIX, resultJson, IDEMPOTENCY_TTL);
            log.trace("Cached result for key: {}", idempotencyKey);
        } catch (JsonProcessingException e) {
            log.warn("Failed to cache result: {}", e.getMessage());
        }
    }

    /**
     * 获取缓存的执行结果
     *
     * @param idempotencyKey 幂等键
     * @return 缓存的结果（可选）
     */
    public Optional<ToolResult> getCachedResult(String idempotencyKey) {
        String cached = redisTemplate.opsForValue().get(idempotencyKey + RESULT_SUFFIX);
        if (cached == null) {
            return Optional.empty();
        }

        try {
            ToolResult result = objectMapper.readValue(cached, ToolResult.class);
            log.trace("Retrieved cached result for key: {}", idempotencyKey);
            return Optional.of(result);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize cached result: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 释放幂等键（执行失败时允许重试）
     *
     * @param idempotencyKey 幂等键
     */
    public void release(String idempotencyKey) {
        redisTemplate.delete(idempotencyKey);
        redisTemplate.delete(idempotencyKey + RESULT_SUFFIX);
        log.trace("Released idempotency key: {}", idempotencyKey);
    }

    /**
     * 检查幂等键是否存在
     *
     * @param idempotencyKey 幂等键
     * @return 是否存在
     */
    public boolean exists(String idempotencyKey) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(idempotencyKey));
    }

    /**
     * 获取幂等键的剩余 TTL
     *
     * @param idempotencyKey 幂等键
     * @return 剩余时间（毫秒），-2 表示不存在，-1 表示无过期时间
     */
    public Long getRemainingTtl(String idempotencyKey) {
        return redisTemplate.getExpire(idempotencyKey);
    }
}
