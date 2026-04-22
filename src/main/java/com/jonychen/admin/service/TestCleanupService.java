package com.jonychen.admin.service;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jonychen.test.repository.TestJobRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 测试结果自动清理服务
 *
 * <p>定时清理超过 90 天的测试数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestCleanupService {

    private static final int DEFAULT_RETENTION_DAYS = 90;

    private final TestJobRepository testJobRepository;

    /** 每天凌晨 3 点执行清理 */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public int cleanupExpiredJobs() {
        return cleanupExpiredJobs(DEFAULT_RETENTION_DAYS);
    }

    /** 清理指定天数之前的测试数据 */
    @Transactional
    public int cleanupExpiredJobs(int retentionDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        log.info("Cleaning up test jobs created before: {}", cutoff);

        int deleted = testJobRepository.deleteByCreatedAtBefore(cutoff);
        log.info("Cleaned up {} expired test jobs", deleted);

        return deleted;
    }
}
