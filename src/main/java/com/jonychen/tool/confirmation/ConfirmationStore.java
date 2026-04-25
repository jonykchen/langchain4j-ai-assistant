package com.jonychen.tool.confirmation;

import java.time.Duration;
import java.util.Optional;

/**
 * 确认持久化存储接口
 *
 * <p>支持多种实现：
 *
 * <ul>
 *   <li>{@link InMemoryConfirmationStore} - 内存实现，适用于测试环境
 *   <li>Redis 实现 - 由 {@link ToolConfirmationManager} 直接使用 RedisTemplate
 * </ul>
 *
 * @author jonychen
 */
public interface ConfirmationStore {

    /**
     * 存储确认请求
     *
     * @param confirmationId 确认ID
     * @param confirmation 确认请求
     * @param ttl 过期时间
     */
    void store(String confirmationId, PendingConfirmation confirmation, Duration ttl);

    /**
     * 获取确认请求
     *
     * @param confirmationId 确认ID
     * @return 确认请求（可选）
     */
    Optional<PendingConfirmation> get(String confirmationId);

    /**
     * 更新确认请求
     *
     * @param confirmationId 确认ID
     * @param confirmation 更新后的确认请求
     */
    void update(String confirmationId, PendingConfirmation confirmation);

    /**
     * 删除确认请求
     *
     * @param confirmationId 确认ID
     */
    void delete(String confirmationId);

    /**
     * 检查确认请求是否存在
     *
     * @param confirmationId 确认ID
     * @return 是否存在
     */
    boolean exists(String confirmationId);

    /**
     * 获取剩余过期时间（秒）
     *
     * @param confirmationId 确认ID
     * @return 剩余秒数，-1 表示不存在或已过期
     */
    long getRemainingTtl(String confirmationId);
}
