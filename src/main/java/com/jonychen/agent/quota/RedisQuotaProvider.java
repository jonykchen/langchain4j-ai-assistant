package com.jonychen.agent.quota;

import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

/**
 * Redis 分布式配额提供者（生产环境）
 *
 * <p>使用 Redis Lua 脚本实现原子性的配额检查和消费。 适用于多实例部署的生产环境。
 *
 * <p>通过配置 {@code agent.quota.provider=redis} 启用， 默认使用 {@link InMemoryQuotaProvider}。
 *
 * @author jonychen
 */
@Service
@ConditionalOnProperty(name = "agent.quota.provider", havingValue = "redis")
public class RedisQuotaProvider implements QuotaProvider {

    private static final Logger log = LoggerFactory.getLogger(RedisQuotaProvider.class);

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<?> checkAndConsumeScript;

    /** 默认每日执行配额 */
    @Value("${agent.quota.daily:100}")
    private int dailyQuota;

    /** 默认每分钟请求配额 */
    @Value("${agent.quota.minute:30}")
    private int minuteQuota;

    /**
     * 配额检查并消费 Lua 脚本
     *
     * <p>KEYS[1]: 配额键 ARGV[1]: 消费数量 ARGV[2]: 配额上限 ARGV[3]: 窗口大小（毫秒） ARGV[4]: 当前时间戳（毫秒）
     *
     * <p>返回：1 允许，0 拒绝
     */
    private static final String CHECK_AND_CONSUME_SCRIPT =
            """
            local key = KEYS[1]
            local amount = tonumber(ARGV[1])
            local limit = tonumber(ARGV[2])
            local window = tonumber(ARGV[3])
            local now = tonumber(ARGV[4])

            -- 清理过期数据
            redis.call('ZREMRANGEBYSCORE', key, 0, now - window)

            -- 获取当前用量
            local current = redis.call('ZCARD', key)

            if current + amount <= limit then
                -- 添加消费记录
                for i = 1, amount do
                    redis.call('ZADD', key, now, now .. '-' .. math.random())
                end
                redis.call('PEXPIRE', key, window)
                return {1, limit - current - amount, window}
            else
                return {0, limit - current, window}
            end
            """;

    public RedisQuotaProvider(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.checkAndConsumeScript = new DefaultRedisScript<>(CHECK_AND_CONSUME_SCRIPT, List.class);
    }

    @Override
    public Mono<QuotaCheckResult> checkAndConsume(String userId, String quotaType, int amount) {
        String key = buildKey(userId, quotaType);
        long windowMs = getWindowMs(quotaType);
        int limit = getLimit(quotaType);
        long now = System.currentTimeMillis();

        try {
            @SuppressWarnings("unchecked")
            java.util.List<Long> result =
                    (java.util.List<Long>)
                            redisTemplate.execute(
                                    checkAndConsumeScript,
                                    Collections.singletonList(key),
                                    String.valueOf(amount),
                                    String.valueOf(limit),
                                    String.valueOf(windowMs),
                                    String.valueOf(now));

            if (result == null || result.isEmpty()) {
                log.warn("[RedisQuota] Redis 返回空结果，降级为允许: user={}, type={}", userId, quotaType);
                return Mono.just(QuotaCheckResult.allowed(limit, getResetSeconds(quotaType)));
            }

            boolean allowed = result.get(0) == 1L;
            int remaining = result.size() > 1 ? result.get(1).intValue() : 0;

            if (allowed) {
                log.debug(
                        "[RedisQuota] 配额检查通过: user={}, type={}, remaining={}",
                        userId,
                        quotaType,
                        remaining);
                return Mono.just(QuotaCheckResult.allowed(remaining, getResetSeconds(quotaType)));
            } else {
                log.warn(
                        "[RedisQuota] 配额不足: user={}, type={}, remaining={}",
                        userId,
                        quotaType,
                        remaining);
                return Mono.just(QuotaCheckResult.denied(remaining, getResetSeconds(quotaType)));
            }
        } catch (Exception e) {
            log.error("[RedisQuota] Redis 异常，降级为允许: user={}, type={}", userId, quotaType, e);
            // Redis 异常时降级为允许，避免阻塞业务
            return Mono.just(QuotaCheckResult.allowed(limit, getResetSeconds(quotaType)));
        }
    }

    @Override
    public Mono<QuotaStatus> getQuotaStatus(String userId, String quotaType) {
        String key = buildKey(userId, quotaType);
        long windowMs = getWindowMs(quotaType);
        int limit = getLimit(quotaType);
        long now = System.currentTimeMillis();

        try {
            // 清理过期数据
            long windowStart = now - windowMs;
            Long count = redisTemplate.opsForZSet().count(key, windowStart, now);
            int used = count != null ? count.intValue() : 0;
            return Mono.just(new QuotaStatus(used, limit, getResetSeconds(quotaType)));
        } catch (Exception e) {
            log.error("[RedisQuota] 获取配额状态失败: user={}, type={}", userId, quotaType, e);
            return Mono.just(new QuotaStatus(0, limit, getResetSeconds(quotaType)));
        }
    }

    @Override
    public Mono<Void> resetQuota(String userId, String quotaType) {
        String key = buildKey(userId, quotaType);
        try {
            redisTemplate.delete(key);
            log.info("[RedisQuota] 配额已重置: user={}, type={}", userId, quotaType);
        } catch (Exception e) {
            log.error("[RedisQuota] 重置配额失败: user={}, type={}", userId, quotaType, e);
        }
        return Mono.empty();
    }

    private String buildKey(String userId, String quotaType) {
        return String.format("quota:%s:%s", quotaType.toLowerCase(), userId);
    }

    private int getLimit(String quotaType) {
        return switch (quotaType) {
            case "DAILY_EXECUTION" -> dailyQuota;
            case "MINUTE_REQUESTS" -> minuteQuota;
            default -> dailyQuota;
        };
    }

    private long getWindowMs(String quotaType) {
        return switch (quotaType) {
            case "DAILY_EXECUTION" -> 86400_000L; // 24小时
            case "MINUTE_REQUESTS" -> 60_000L; // 1分钟
            default -> 86400_000L;
        };
    }

    private int getResetSeconds(String quotaType) {
        return switch (quotaType) {
            case "DAILY_EXECUTION" -> 86400;
            case "MINUTE_REQUESTS" -> 60;
            default -> 86400;
        };
    }
}
