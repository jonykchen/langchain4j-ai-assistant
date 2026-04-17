package com.jonychen.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的分布式限流器
 *
 * 使用滑动窗口算法实现精确的分布式限流
 * 通过 Lua 脚本保证原子性
 */
@Component
public class DistributedRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(DistributedRateLimiter.class);

    private final StringRedisTemplate redisTemplate;

    /**
     * 滑动窗口限流 Lua 脚本
     *
     * KEYS[1]: 限流键
     * ARGV[1]: 窗口大小（毫秒）
     * ARGV[2]: 最大请求数
     * ARGV[3]: 当前时间戳（毫秒）
     *
     * 返回值：1 允许，0 拒绝
     */
    private static final String SLIDING_WINDOW_SCRIPT = """
            local key = KEYS[1]
            local window = tonumber(ARGV[1])
            local limit = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])

            -- 清理过期数据
            redis.call('ZREMRANGEBYSCORE', key, 0, now - window)

            -- 获取当前窗口内的请求数
            local count = redis.call('ZCARD', key)

            if count < limit then
                -- 添加当前请求
                redis.call('ZADD', key, now, now .. '-' .. math.random())
                -- 设置过期时间
                redis.call('PEXPIRE', key, window)
                return 1
            else
                return 0
            end
            """;

    /**
     * 令牌桶限流 Lua 脚本
     *
     * KEYS[1]: 限流键
     * ARGV[1]: 桶容量
     * ARGV[2]: 令牌生成速率（令牌/秒）
     * ARGV[3]: 当前时间戳（毫秒）
     *
     * 返回值：1 允许，0 拒绝
     */
    private static final String TOKEN_BUCKET_SCRIPT = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local rate = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])

            -- 获取上次更新时间和当前令牌数
            local lastRefill = redis.call('HGET', key, 'last_refill')
            local tokens = redis.call('HGET', key, 'tokens')

            if lastRefill == false then
                lastRefill = now
                tokens = capacity
            else
                lastRefill = tonumber(lastRefill)
                tokens = tonumber(tokens)
            end

            -- 计算新生成的令牌
            local elapsed = (now - lastRefill) / 1000
            local newTokens = elapsed * rate
            tokens = math.min(capacity, tokens + newTokens)

            if tokens >= 1 then
                tokens = tokens - 1
                redis.call('HSET', key, 'tokens', tokens)
                redis.call('HSET', key, 'last_refill', now)
                redis.call('PEXPIRE', key, 60000)
                return 1
            else
                return 0
            end
            """;

    private final DefaultRedisScript<Long> slidingWindowScript;
    private final DefaultRedisScript<Long> tokenBucketScript;

    public DistributedRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.slidingWindowScript = new DefaultRedisScript<>(SLIDING_WINDOW_SCRIPT, Long.class);
        this.tokenBucketScript = new DefaultRedisScript<>(TOKEN_BUCKET_SCRIPT, Long.class);
    }

    /**
     * 滑动窗口限流
     *
     * @param key     限流键
     * @param limit   最大请求数
     * @param period  时间窗口
     * @param unit    时间单位
     * @return true 允许，false 拒绝
     */
    public boolean tryAcquireSlidingWindow(String key, int limit, long period, TimeUnit unit) {
        long windowMillis = unit.toMillis(period);
        long now = System.currentTimeMillis();

        try {
            Long result = redisTemplate.execute(
                    slidingWindowScript,
                    Collections.singletonList(key),
                    String.valueOf(windowMillis),
                    String.valueOf(limit),
                    String.valueOf(now)
            );

            boolean allowed = result != null && result == 1L;
            if (!allowed) {
                log.warn("分布式限流(滑动窗口)拒绝: key={}, limit={}, window={}ms", key, limit, windowMillis);
            }
            return allowed;
        } catch (Exception e) {
            log.error("Redis 限流异常，降级为允许: key={}", key, e);
            // Redis 异常时降级为允许，避免影响业务
            return true;
        }
    }

    /**
     * 令牌桶限流
     *
     * @param key      限流键
     * @param capacity 桶容量
     * @param rate     令牌生成速率（令牌/秒）
     * @return true 允许，false 拒绝
     */
    public boolean tryAcquireTokenBucket(String key, int capacity, double rate) {
        long now = System.currentTimeMillis();

        try {
            Long result = redisTemplate.execute(
                    tokenBucketScript,
                    Collections.singletonList(key),
                    String.valueOf(capacity),
                    String.valueOf(rate),
                    String.valueOf(now)
            );

            boolean allowed = result != null && result == 1L;
            if (!allowed) {
                log.warn("分布式限流(令牌桶)拒绝: key={}, capacity={}, rate={}", key, capacity, rate);
            }
            return allowed;
        } catch (Exception e) {
            log.error("Redis 限流异常，降级为允许: key={}", key, e);
            return true;
        }
    }

    /**
     * 构建分布式限流键
     *
     * @param prefix     前缀
     * @param identifier 标识符（IP/用户ID）
     * @return 完整的限流键
     */
    public static String buildKey(String prefix, String identifier) {
        return String.format("rate_limit:%s:%s", prefix, identifier);
    }

    /**
     * 获取当前窗口内的请求数
     */
    public long getCurrentCount(String key, long window, TimeUnit unit) {
        try {
            long now = System.currentTimeMillis();
            long windowStart = now - unit.toMillis(window);
            Long count = redisTemplate.opsForZSet().count(key, windowStart, now);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("获取限流计数失败: key={}", key, e);
            return 0;
        }
    }

    /**
     * 重置限流计数
     */
    public void reset(String key) {
        try {
            redisTemplate.delete(key);
            log.info("限流计数已重置: key={}", key);
        } catch (Exception e) {
            log.error("重置限流计数失败: key={}", key, e);
        }
    }
}
