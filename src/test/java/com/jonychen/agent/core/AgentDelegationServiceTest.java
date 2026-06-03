package com.jonychen.agent.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jonychen.observability.trace.AgentTraceService;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import reactor.core.publisher.FluxSink;

@ExtendWith(MockitoExtension.class)
class AgentDelegationServiceTest {

    @Mock private Agent dataAgent;

    @Mock private Agent opsAgent;

    @Mock private AgentMetricsService metricsService;

    @Mock private AgentRegistry agentRegistry;

    @Mock private AgentTraceService traceService;

    @Mock private FluxSink<AgentEvent> sink;

    private AgentDelegationService delegationService;

    private AtomicInteger sequenceCounter;

    @BeforeEach
    void setUp() {
        delegationService = new AgentDelegationService(metricsService);
        delegationService.setAgentRegistry(agentRegistry);
        sequenceCounter = new AtomicInteger(0);
    }

    private AgentContext createContext() {
        return new AgentContext(
                "trace-1",
                "sess-1",
                "user-1",
                AgentType.DATA,
                null,
                MessageWindowChatMemory.withMaxMessages(10),
                traceService,
                AgentRequestOptions.defaults());
    }

    @Nested
    @DisplayName("委托执行")
    class Delegate {

        @Test
        @DisplayName("应正确委托到目标 Agent")
        void shouldDelegateToTargetAgent() {
            when(agentRegistry.getAgent("data")).thenReturn(java.util.Optional.of(dataAgent));
            lenient().when(dataAgent.getMetadata()).thenReturn(AgentMetadata.data());

            AgentResult mockResult =
                    AgentResult.success("data-agent-trace", "查询结果", Collections.emptyList());
            when(dataAgent.execute(any(AgentRequest.class), any(AgentContext.class)))
                    .thenReturn(mockResult);

            AgentContext context = createContext();
            context.setVariable("sourceAgentName", "source-agent");

            AgentDelegationService.DelegationResult result =
                    delegationService.delegate("data", "查询用户数据", context, sink, sequenceCounter, 0);

            assertTrue(result.success());
            assertEquals("查询结果", result.output());
            verify(metricsService).recordDelegation("source-agent", "data", true, 0);
        }

        @Test
        @DisplayName("不存在的目标 Agent 应返回失败")
        void shouldReturnFailureForNonExistentTarget() {
            when(agentRegistry.getAgent("nonexistent")).thenReturn(java.util.Optional.empty());

            AgentContext context = createContext();
            context.setVariable("sourceAgentName", "source-agent");

            AgentDelegationService.DelegationResult result =
                    delegationService.delegate(
                            "nonexistent", "测试", context, sink, sequenceCounter, 0);

            assertFalse(result.success());
            assertNotNull(result.error());
        }

        @Test
        @DisplayName("不能委托给自己")
        void shouldNotDelegateToSelf() {
            when(agentRegistry.getAgent("data")).thenReturn(java.util.Optional.of(dataAgent));
            lenient().when(dataAgent.getMetadata()).thenReturn(AgentMetadata.data());

            AgentContext context = createContext();
            context.setVariable("sourceAgentName", "data");

            AgentDelegationService.DelegationResult result =
                    delegationService.delegate("data", "测试", context, sink, sequenceCounter, 0);

            assertFalse(result.success());
            assertNotNull(result.error());
        }

        @Test
        @DisplayName("委托深度超限应返回失败")
        void shouldReturnFailureWhenDepthExceeded() {
            AgentContext context = createContext();
            context.setVariable("delegationDepth", 3);
            context.setVariable("sourceAgentName", "source-agent");

            AgentDelegationService.DelegationResult result =
                    delegationService.delegate("data", "测试", context, sink, sequenceCounter, 0);

            assertFalse(result.success());
            assertNotNull(result.error());
        }
    }

    @Nested
    @DisplayName("获取可委托 Agent")
    class GetDelegatableAgents {

        @Test
        @DisplayName("应排除 ROUTER 类型和指定 Agent")
        void shouldExcludeRouterAndSelf() {
            AgentMetadata routerMeta = AgentMetadata.router();
            AgentMetadata dataMeta = AgentMetadata.data();
            AgentMetadata opsMeta = AgentMetadata.ops();

            when(agentRegistry.getAllMetadata()).thenReturn(List.of(routerMeta, dataMeta, opsMeta));

            List<AgentMetadata> result = delegationService.getDelegatableAgents("data");

            assertEquals(1, result.size());
            assertEquals("ops", result.get(0).name());
        }
    }
}
