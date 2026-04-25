package com.jonychen.agent.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jonychen.agent.core.AgentAuditService.AuditEventType;
import com.jonychen.agent.entity.AgentAuditLog;
import com.jonychen.agent.repository.AgentAuditLogRepository;

@ExtendWith(MockitoExtension.class)
class AgentAuditServiceTest {

    @Mock private AgentAuditLogRepository auditLogRepository;

    private AgentAuditService auditService;

    @BeforeEach
    void setUp() {
        auditService = new AgentAuditService(auditLogRepository);
    }

    @Nested
    @DisplayName("记录执行开始")
    class RecordExecutionStart {

        @Test
        @DisplayName("应正确记录执行开始事件")
        void shouldRecordExecutionStart() {
            auditService.recordExecutionStart(
                    "trace-123", "user-1", "DataAgent", "查询上周数据", "192.168.1.1", "Mozilla/5.0");

            verify(auditLogRepository).save(any(AgentAuditLog.class));
        }
    }

    @Nested
    @DisplayName("记录执行结束")
    class RecordExecutionEnd {

        @Test
        @DisplayName("成功结束应记录 EXECUTION_END")
        void successShouldRecordExecutionEnd() {
            auditService.recordExecutionEnd(
                    "trace-123", "user-1", "DataAgent", true, 100, 5000L, 3);

            verify(auditLogRepository)
                    .save(argThat(log -> log.getEventType() == AuditEventType.EXECUTION_END));
        }

        @Test
        @DisplayName("失败结束应记录 EXECUTION_ERROR")
        void failureShouldRecordExecutionError() {
            auditService.recordExecutionEnd("trace-123", "user-1", "DataAgent", false, 0, 3000L, 2);

            verify(auditLogRepository)
                    .save(argThat(log -> log.getEventType() == AuditEventType.EXECUTION_ERROR));
        }
    }

    @Nested
    @DisplayName("记录工具调用")
    class RecordToolCall {

        @Test
        @DisplayName("应正确记录工具调用事件")
        void shouldRecordToolCall() {
            auditService.recordToolCall(
                    "trace-123",
                    "user-1",
                    "DataAgent",
                    "execute_query",
                    Map.of("sql", "SELECT * FROM users"),
                    true,
                    150L);

            verify(auditLogRepository)
                    .save(
                            argThat(
                                    log ->
                                            log.getEventType() == AuditEventType.TOOL_CALL
                                                    && log.getDetails().containsKey("toolName")));
        }
    }

    @Nested
    @DisplayName("记录确认请求")
    class RecordConfirmationRequired {

        @Test
        @DisplayName("应正确记录确认请求")
        void shouldRecordConfirmationRequired() {
            auditService.recordConfirmationRequired(
                    "trace-123", "user-1", "conf-456", "adjust_weight", "HIGH");

            verify(auditLogRepository)
                    .save(
                            argThat(
                                    log ->
                                            log.getEventType()
                                                    == AuditEventType.CONFIRMATION_REQUIRED));
        }
    }

    @Nested
    @DisplayName("记录确认结果")
    class RecordConfirmationResult {

        @Test
        @DisplayName("批准应记录 CONFIRMATION_APPROVED")
        void approvedShouldRecordApproved() {
            auditService.recordConfirmationResult("trace-123", "user-1", "conf-456", true);

            verify(auditLogRepository)
                    .save(
                            argThat(
                                    log ->
                                            log.getEventType()
                                                    == AuditEventType.CONFIRMATION_APPROVED));
        }

        @Test
        @DisplayName("拒绝应记录 CONFIRMATION_REJECTED")
        void rejectedShouldRecordRejected() {
            auditService.recordConfirmationResult("trace-123", "user-1", "conf-456", false);

            verify(auditLogRepository)
                    .save(
                            argThat(
                                    log ->
                                            log.getEventType()
                                                    == AuditEventType.CONFIRMATION_REJECTED));
        }
    }

    @Nested
    @DisplayName("敏感参数脱敏")
    class SanitizeParams {

        @Test
        @DisplayName("password 字段应被脱敏")
        void passwordShouldBeMasked() {
            auditService.recordToolCall(
                    "trace-123",
                    "user-1",
                    "TestAgent",
                    "login",
                    Map.of("username", "admin", "password", "secret123"),
                    true,
                    100L);

            verify(auditLogRepository)
                    .save(
                            argThat(
                                    log -> {
                                        Object params = log.getDetails().get("params");
                                        return params instanceof Map
                                                && ((Map<?, ?>) params).containsKey("password")
                                                && "***"
                                                        .equals(
                                                                ((Map<?, ?>) params)
                                                                        .get("password"));
                                    }));
        }

        @Test
        @DisplayName("token 字段应被脱敏")
        void tokenShouldBeMasked() {
            auditService.recordToolCall(
                    "trace-123",
                    "user-1",
                    "OpsAgent",
                    "call_api",
                    Map.of("apiToken", "abc123xyz"),
                    true,
                    50L);

            verify(auditLogRepository)
                    .save(
                            argThat(
                                    log -> {
                                        Object params = log.getDetails().get("params");
                                        return params instanceof Map
                                                && ((Map<?, ?>) params).containsKey("apiToken")
                                                && "***"
                                                        .equals(
                                                                ((Map<?, ?>) params)
                                                                        .get("apiToken"));
                                    }));
        }
    }
}
