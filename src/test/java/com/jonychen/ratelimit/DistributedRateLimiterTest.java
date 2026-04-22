package com.jonychen.ratelimit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

@ExtendWith(MockitoExtension.class)
class DistributedRateLimiterTest {

    @Mock private StringRedisTemplate redisTemplate;

    @Mock private ZSetOperations<String, String> zSetOperations;

    private DistributedRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        rateLimiter = new DistributedRateLimiter(redisTemplate);
    }

    @Test
    @DisplayName("构建限流键 - 应返回正确格式")
    void buildKey_shouldReturnCorrectFormat() {
        String key = DistributedRateLimiter.buildKey("chat", "192.168.1.1");
        assertEquals("rate_limit:chat:192.168.1.1", key);
    }

    @Test
    @DisplayName("获取当前计数 - 应返回正确数量")
    void getCurrentCount_shouldReturnCorrectCount() {
        when(zSetOperations.count(anyString(), anyDouble(), anyDouble())).thenReturn(5L);

        long count = rateLimiter.getCurrentCount("test-key", 60, TimeUnit.SECONDS);

        assertEquals(5L, count);
        verify(zSetOperations).count(eq("test-key"), anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("获取当前计数 - Redis 异常应返回 0")
    void getCurrentCount_redisException_shouldReturnZero() {
        when(zSetOperations.count(anyString(), anyDouble(), anyDouble()))
                .thenThrow(new RuntimeException("Redis connection error"));

        long count = rateLimiter.getCurrentCount("test-key", 60, TimeUnit.SECONDS);

        assertEquals(0L, count);
    }

    @Test
    @DisplayName("重置限流计数 - 应删除键")
    void reset_shouldDeleteKey() {
        when(redisTemplate.delete(anyString())).thenReturn(true);

        rateLimiter.reset("test-key");

        verify(redisTemplate).delete("test-key");
    }

    @Test
    @DisplayName("tryAcquireSlidingWindow - Redis 异常应降级为允许")
    void tryAcquireSlidingWindow_redisException_shouldDegradeToAllow() {
        when(redisTemplate.execute(any(), anyList(), any()))
                .thenThrow(new RuntimeException("Redis error"));

        boolean result = rateLimiter.tryAcquireSlidingWindow("test-key", 10, 60, TimeUnit.SECONDS);

        assertTrue(result); // 降级为允许
    }
}
