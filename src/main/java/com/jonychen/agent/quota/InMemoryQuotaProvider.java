package com.jonychen.agent.quota;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

/**
 * 内存配额提供者（开发环境）
 *
 * <p>适用于单实例部署的开发环境，使用 JVM 内存存储配额状态。 生产环境应使用 {@link RedisQuotaProvider}。
 *
 * <p>默认使用此实现，除非配置了 {@code agent.quota.provider=redis}。
 *
 * @author jonychen
 */
@Service
@ConditionalOnMissingBean(RedisQuotaProvider.class)
public class InMemoryQuotaProvider implements QuotaProvider {

    private static final Logger log = LoggerFactory.getLogger(InMemoryQuotaProvider.class);

    /** 默认每日执行配额 */
    private static final int DEFAULT_DAILY_QUOTA = 100;

    /** 默认每分钟请求配额 */
    private static final int DEFAULT_MINUTE_QUOTA = 30;

    /** 用户配额计数器：key = userId:quotaType */
    private final ConcurrentHashMap<String, QuotaCounter> quotaCounters = new ConcurrentHashMap<>();

    @Override
    public Mono<QuotaCheckResult> checkAndConsume(String userId, String quotaType, int amount) {
        String key = buildKey(userId, quotaType);
        QuotaCounter counter =
                quotaCounters.computeIfAbsent(key, k -> new QuotaCounter(getLimit(quotaType)));

        int limit = counter.limit;
        int current = counter.count.get();
        int remaining = limit - current;

        if (current + amount <= limit) {
            counter.count.addAndGet(amount);
            remaining = limit - counter.count.get();
            log.debug(
                    "[InMemoryQuota] 配额检查通过: user={}, type={}, used={}/{}",
                    userId,
                    quotaType,
                    counter.count.get(),
                    limit);
            return Mono.just(QuotaCheckResult.allowed(remaining, getResetSeconds(quotaType)));
        } else {
            log.warn(
                    "[InMemoryQuota] 配额不足: user={}, type={}, used={}/{}",
                    userId,
                    quotaType,
                    current,
                    limit);
            return Mono.just(QuotaCheckResult.denied(remaining, getResetSeconds(quotaType)));
        }
    }

    @Override
    public Mono<QuotaStatus> getQuotaStatus(String userId, String quotaType) {
        String key = buildKey(userId, quotaType);
        QuotaCounter counter = quotaCounters.get(key);
        if (counter == null) {
            return Mono.just(new QuotaStatus(0, getLimit(quotaType), getResetSeconds(quotaType)));
        }
        return Mono.just(
                new QuotaStatus(counter.count.get(), counter.limit, getResetSeconds(quotaType)));
    }

    @Override
    public Mono<Void> resetQuota(String userId, String quotaType) {
        String key = buildKey(userId, quotaType);
        QuotaCounter counter = quotaCounters.get(key);
        if (counter != null) {
            counter.count.set(0);
            log.info("[InMemoryQuota] 配额已重置: user={}, type={}", userId, quotaType);
        }
        return Mono.empty();
    }

    private String buildKey(String userId, String quotaType) {
        return userId + ":" + quotaType;
    }

    private int getLimit(String quotaType) {
        return switch (quotaType) {
            case "DAILY_EXECUTION" -> DEFAULT_DAILY_QUOTA;
            case "MINUTE_REQUESTS" -> DEFAULT_MINUTE_QUOTA;
            default -> DEFAULT_DAILY_QUOTA;
        };
    }

    private int getResetSeconds(String quotaType) {
        return switch (quotaType) {
            case "DAILY_EXECUTION" -> 86400; // 24小时
            case "MINUTE_REQUESTS" -> 60; // 1分钟
            default -> 86400;
        };
    }

    /** 配额计数器 */
    private static class QuotaCounter {
        final AtomicInteger count = new AtomicInteger(0);
        final int limit;
        final long startTime = System.currentTimeMillis();

        QuotaCounter(int limit) {
            this.limit = limit;
        }
    }
}
