package com.jonychen.agent.quota;

import reactor.core.publisher.Mono;

/**
 * 配额提供者接口
 *
 * <p>定义 Agent 执行配额管理的抽象接口，支持多种实现：
 *
 * <ul>
 *   <li>内存实现：适用于单实例开发环境
 *   <li>Redis 实现：适用于多实例生产环境
 * </ul>
 *
 * @author jonychen
 */
public interface QuotaProvider {

    /**
     * 检查并消费配额
     *
     * @param userId 用户 ID
     * @param quotaType 配额类型（DAILY_EXECUTION, HOURLY_REQUESTS, TOKEN_BUDGET）
     * @param amount 消费数量
     * @return 配额检查结果
     */
    Mono<QuotaCheckResult> checkAndConsume(String userId, String quotaType, int amount);

    /**
     * 获取当前配额状态
     *
     * @param userId 用户 ID
     * @param quotaType 配额类型
     * @return 配额状态
     */
    Mono<QuotaStatus> getQuotaStatus(String userId, String quotaType);

    /**
     * 重置用户配额
     *
     * @param userId 用户 ID
     * @param quotaType 配额类型
     * @return 完成信号
     */
    Mono<Void> resetQuota(String userId, String quotaType);

    /**
     * 配额检查结果
     *
     * @param allowed 是否允许
     * @param remaining 剩余配额
     * @param resetInSeconds 重置时间（秒）
     */
    record QuotaCheckResult(boolean allowed, int remaining, int resetInSeconds) {
        public static QuotaCheckResult allowed(int remaining, int resetInSeconds) {
            return new QuotaCheckResult(true, remaining, resetInSeconds);
        }

        public static QuotaCheckResult denied(int remaining, int resetInSeconds) {
            return new QuotaCheckResult(false, remaining, resetInSeconds);
        }
    }

    /**
     * 配额状态
     *
     * @param used 已使用
     * @param limit 上限
     * @param resetInSeconds 重置时间（秒）
     */
    record QuotaStatus(int used, int limit, int resetInSeconds) {}
}
