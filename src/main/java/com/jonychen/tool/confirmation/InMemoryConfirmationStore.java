package com.jonychen.tool.confirmation;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

import lombok.extern.slf4j.Slf4j;

/**
 * 内存确认存储实现（用于测试环境）
 *
 * <p>特性：
 *
 * <ul>
 *   <li>支持 TTL 过期
 *   <li>定期清理过期条目
 *   <li>线程安全
 * </ul>
 *
 * <p>注意：生产环境请使用 Redis 实现（{@link RedisConfirmationStore}）
 *
 * <p>Bean 创建由 {@link com.jonychen.tool.config.ConfirmationStoreConfig} 管理。
 *
 * @author jonychen
 */
@Slf4j
public class InMemoryConfirmationStore implements ConfirmationStore {

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor;

    /** 默认清理间隔：60 秒 */
    private static final long CLEANUP_INTERVAL_MS = 60_000;

    public InMemoryConfirmationStore() {
        // 启动定期清理任务
        this.cleanupExecutor =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            Thread t = new Thread(r, "confirmation-cleanup");
                            t.setDaemon(true);
                            return t;
                        });
        this.cleanupExecutor.scheduleAtFixedRate(
                this::cleanupExpired,
                CLEANUP_INTERVAL_MS,
                CLEANUP_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
        log.info("[InMemoryConfirmationStore] 初始化完成");
    }

    @PreDestroy
    public void shutdown() {
        cleanupExecutor.shutdown();
        log.info("[InMemoryConfirmationStore] 已关闭");
    }

    @Override
    public void store(String confirmationId, PendingConfirmation confirmation, Duration ttl) {
        long expiresAt = System.currentTimeMillis() + ttl.toMillis();
        cache.put(confirmationId, new CacheEntry(confirmation, expiresAt));
        log.debug(
                "[InMemoryConfirmationStore] 存储: id={}, ttl={}s", confirmationId, ttl.getSeconds());
    }

    @Override
    public Optional<PendingConfirmation> get(String confirmationId) {
        CacheEntry entry = cache.get(confirmationId);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.isExpired()) {
            cache.remove(confirmationId);
            log.debug("[InMemoryConfirmationStore] 已过期: id={}", confirmationId);
            return Optional.empty();
        }
        return Optional.of(entry.confirmation());
    }

    @Override
    public void update(String confirmationId, PendingConfirmation confirmation) {
        CacheEntry existing = cache.get(confirmationId);
        if (existing != null && !existing.isExpired()) {
            // 保持原有的过期时间
            cache.put(confirmationId, new CacheEntry(confirmation, existing.expiresAt()));
            log.debug("[InMemoryConfirmationStore] 更新: id={}", confirmationId);
        } else {
            // 如果不存在或已过期，使用默认 TTL
            store(confirmationId, confirmation, Duration.ofMinutes(5));
        }
    }

    @Override
    public void delete(String confirmationId) {
        cache.remove(confirmationId);
        log.debug("[InMemoryConfirmationStore] 删除: id={}", confirmationId);
    }

    @Override
    public boolean exists(String confirmationId) {
        return get(confirmationId).isPresent();
    }

    @Override
    public long getRemainingTtl(String confirmationId) {
        CacheEntry entry = cache.get(confirmationId);
        if (entry == null) {
            return -1;
        }
        long remaining = entry.expiresAt() - System.currentTimeMillis();
        return remaining > 0 ? remaining / 1000 : -1;
    }

    /** 清理过期条目 */
    private void cleanupExpired() {
        int removed = 0;
        for (Map.Entry<String, CacheEntry> entry : cache.entrySet()) {
            if (entry.getValue().isExpired()) {
                cache.remove(entry.getKey());
                removed++;
            }
        }
        if (removed > 0) {
            log.debug("[InMemoryConfirmationStore] 清理 {} 个过期条目", removed);
        }
    }

    /** 缓存条目 */
    private record CacheEntry(PendingConfirmation confirmation, long expiresAt) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
