package com.jonychen.agent.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jonychen.agent.impl.RouterAgent;
import com.jonychen.agent.security.AgentInputValidator;
import com.jonychen.agent.security.AgentPermissionService;

@ExtendWith(MockitoExtension.class)
class AgentOrchestratorTest {

    @Mock private AgentRegistry agentRegistry;

    @Mock private AgentPermissionService permissionService;

    @Mock private AgentInputValidator inputValidator;

    @Mock private RouterAgent routerAgent;

    @Mock private AgentAuditService auditService;

    @Mock private AgentMetricsService metricsService;

    @Mock private Agent dataAgent;

    @Mock private Agent opsAgent;

    private AgentOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator =
                new AgentOrchestrator(
                        agentRegistry,
                        permissionService,
                        inputValidator,
                        routerAgent,
                        auditService,
                        metricsService);
    }

    @Nested
    @DisplayName("获取可用 Agent")
    class GetAvailableAgents {

        @Test
        @DisplayName("应返回所有注册的 Agent")
        void shouldReturnAllRegisteredAgents() {
            when(agentRegistry.getAllMetadata())
                    .thenReturn(
                            List.of(
                                    AgentMetadata.router(),
                                    AgentMetadata.data(),
                                    AgentMetadata.ops()));

            List<AgentMetadata> agents = orchestrator.getAvailableAgents();

            assertEquals(3, agents.size());
            assertTrue(agents.stream().anyMatch(a -> a.name().equals("router")));
            assertTrue(agents.stream().anyMatch(a -> a.name().equals("data")));
            assertTrue(agents.stream().anyMatch(a -> a.name().equals("ops")));
        }
    }

    @Nested
    @DisplayName("路由到指定 Agent")
    class RouteToAgent {

        @Test
        @DisplayName("应根据名称获取 Agent")
        void shouldGetAgentByName() {
            when(dataAgent.getMetadata()).thenReturn(AgentMetadata.data());
            when(agentRegistry.getAgent("data")).thenReturn(Optional.of(dataAgent));

            Agent found = orchestrator.routeToAgent("data");

            assertNotNull(found);
            assertEquals("data", found.getMetadata().name());
        }

        @Test
        @DisplayName("不存在的 Agent 应返回 null")
        void shouldReturnNullForNonExistentAgent() {
            when(agentRegistry.getAgent("nonexistent")).thenReturn(Optional.empty());

            Agent found = orchestrator.routeToAgent("nonexistent");

            assertNull(found);
        }
    }

    @Nested
    @DisplayName("取消执行")
    class CancelExecution {

        @Test
        @DisplayName("不存在的 traceId 应返回 false")
        void shouldReturnFalseForNonExistentTraceId() {
            assertFalse(orchestrator.cancelExecution("nonexistent-trace"));
        }
    }
}
