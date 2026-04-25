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

import com.jonychen.agent.impl.DataAgent;
import com.jonychen.agent.impl.OpsAgent;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;

@ExtendWith(MockitoExtension.class)
class AgentDelegationServiceTest {

    @Mock
    private DataAgent dataAgent;

    @Mock
    private OpsAgent opsAgent;

    @Mock
    private AgentAuditService auditService;

    @Mock
    private AgentMetricsService metricsService;

    @Mock
    private AgentTraceService traceService;

    private AgentDelegationService delegationService;

    @BeforeEach
    void setUp() {
        // 配置 Agent 元信息
        when(dataAgent.getMetadata()).thenReturn(AgentMetadata.data());
        when(opsAgent.getMetadata()).thenReturn(AgentMetadata.ops());

        delegationService = new AgentDelegationService(
                Map.of("data", dataAgent, "ops", opsAgent),
                auditService,
                metricsService,
                traceService
        );
    }

    @Nested
    @DisplayName("委托执行")
    class Delegate {

        @Test
        @DisplayName("应正确委托到目标 Agent")
        void shouldDelegateToTargetAgent() {
            // 配置 dataAgent 执行结果
            AgentResult mockResult = AgentResult.success("data-agent-trace", "查询结果", 50, 100);
            when(dataAgent.execute(any(AgentRequest.class), any(AgentContext.class)))
                    .thenReturn(mockResult);

            AgentContext context = new AgentContext(
                    "trace-1", "sess-1", "user-1",
                    AgentType.DATA, null,
                    MessageWindowChatMemory.withMaxMessages(10),
                    traceService,
                    AgentRequestOptions.defaults()
            );

            AgentResult result = delegationService.delegate(
                    "source-agent", "data", "查询用户数据", context
            );

            assertTrue(result.isSuccess());
            verify(metricsService).recordDelegation(eq("source-agent"), eq("data"), eq(true), anyInt());
        }

        @Test
        @DisplayName("不存在的目标 Agent 应返回失败")
        void shouldReturnFailureForNonExistentTarget() {
            AgentContext context = new AgentContext(
                    "trace-1", "sess-1", "user-1",
                    AgentType.DATA, null,
                    MessageWindowChatMemory.withMaxMessages(10),
                    traceService,
                    AgentRequestOptions.defaults()
            );

            AgentResult result = delegationService.delegate(
                    "source-agent", "nonexistent", "测试", context
            );

            assertFalse(result.isSuccess());
            verify(metricsService).recordDelegation(eq("source-agent"), eq("nonexistent"), eq(false), anyInt());
        }

        @Test
        @DisplayName("委托深度限制应正确检查")
        void shouldCheckDelegationDepthLimit() {
            // 深度超过限制时应拒绝
            assertDoesNotThrow(() -> {
                delegationService.checkDelegationDepth(1);
            });

            assertDoesNotThrow(() -> {
                delegationService.checkDelegationDepth(2);
            });

            assertThrows(AgentException.class, () -> {
                delegationService.checkDelegationDepth(5);
            });
        }
    }

    @Nested
    @DisplayName("获取委托链")
    class GetDelegationChain {

        @Test
        @DisplayName("应返回可用的委托目标")
        void shouldReturnAvailableDelegationTargets() {
            var targets = delegationService.getDelegationTargets();

            assertTrue(targets.contains("data"));
            assertTrue(targets.contains("ops"));
        }
    }
}
