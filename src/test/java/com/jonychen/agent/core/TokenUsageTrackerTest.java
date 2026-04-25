package com.jonychen.agent.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jonychen.agent.quota.QuotaProvider;

@ExtendWith(MockitoExtension.class)
class TokenUsageTrackerTest {

    @Mock private QuotaProvider quotaProvider;

    private TokenUsageTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new TokenUsageTracker(quotaProvider);
    }

    @Nested
    @DisplayName("记录 Token 使用")
    class RecordUsage {

        @Test
        @DisplayName("应正确记录单次 Token 使用")
        void shouldRecordSingleUsage() {
            tracker.recordUsage("trace-1", "user-1", "model-1", 100, 200);

            TokenUsageTracker.UsageSummary summary = tracker.getSummary("trace-1");
            assertNotNull(summary);
            assertEquals(100, summary.promptTokens());
            assertEquals(200, summary.completionTokens());
            assertEquals(300, summary.totalTokens());
        }

        @Test
        @DisplayName("应正确累加多次 Token 使用")
        void shouldAccumulateMultipleUsages() {
            tracker.recordUsage("trace-1", "user-1", "model-1", 100, 200);
            tracker.recordUsage("trace-1", "user-1", "model-1", 50, 100);

            TokenUsageTracker.UsageSummary summary = tracker.getSummary("trace-1");
            assertNotNull(summary);
            assertEquals(150, summary.promptTokens());
            assertEquals(300, summary.completionTokens());
            assertEquals(450, summary.totalTokens());
        }

        @Test
        @DisplayName("不同 traceId 应分开记录")
        void shouldSeparateDifferentTraces() {
            tracker.recordUsage("trace-1", "user-1", "model-1", 100, 200);
            tracker.recordUsage("trace-2", "user-1", "model-1", 50, 100);

            TokenUsageTracker.UsageSummary summary1 = tracker.getSummary("trace-1");
            TokenUsageTracker.UsageSummary summary2 = tracker.getSummary("trace-2");

            assertEquals(300, summary1.totalTokens());
            assertEquals(150, summary2.totalTokens());
        }
    }

    @Nested
    @DisplayName("配额检查")
    class CheckQuota {

        @Test
        @DisplayName("配额充足时返回 true")
        void shouldReturnTrueWhenQuotaAvailable() {
            when(quotaProvider.getUserDailyQuota("user-1")).thenReturn(Optional.of(10000L));
            when(quotaProvider.getUserDailyUsed("user-1")).thenReturn(Optional.of(1000L));

            boolean result = tracker.checkQuota("user-1", 1000);
            assertTrue(result);
        }

        @Test
        @DisplayName("配额不足时返回 false")
        void shouldReturnFalseWhenQuotaExceeded() {
            when(quotaProvider.getUserDailyQuota("user-1")).thenReturn(Optional.of(1000L));
            when(quotaProvider.getUserDailyUsed("user-1")).thenReturn(Optional.of(900L));

            boolean result = tracker.checkQuota("user-1", 200);
            assertFalse(result);
        }

        @Test
        @DisplayName("无配额限制时默认允许")
        void shouldAllowWhenNoQuotaConfigured() {
            when(quotaProvider.getUserDailyQuota("user-1")).thenReturn(Optional.empty());

            boolean result = tracker.checkQuota("user-1", 10000);
            assertTrue(result);
        }
    }

    @Nested
    @DisplayName("清理")
    class Cleanup {

        @Test
        @DisplayName("应正确清理已完成的 trace")
        void shouldCleanupCompletedTrace() {
            tracker.recordUsage("trace-1", "user-1", "model-1", 100, 200);

            tracker.cleanup("trace-1");

            TokenUsageTracker.UsageSummary summary = tracker.getSummary("trace-1");
            assertNotNull(summary);
        }
    }
}
