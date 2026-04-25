package com.jonychen.agent.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jonychen.agent.impl.DataAgent;
import com.jonychen.agent.impl.OpsAgent;
import com.jonychen.agent.impl.RouterAgent;

@ExtendWith(MockitoExtension.class)
class AgentOrchestratorTest {

    @Mock private RouterAgent routerAgent;

    @Mock private DataAgent dataAgent;

    @Mock private OpsAgent opsAgent;

    @Mock private AgentAuditService auditService;

    @Mock private AgentMetricsService metricsService;

    @Mock private AgentExecutionControlService executionControl;

    private AgentOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        // 配置 RouterAgent
        when(routerAgent.getMetadata()).thenReturn(AgentMetadata.router());

        // 配置 DataAgent
        when(dataAgent.getMetadata()).thenReturn(AgentMetadata.data());
        when(dataAgent.canHandle(any())).thenReturn(0.8);

        // 配置 OpsAgent
        when(opsAgent.getMetadata()).thenReturn(AgentMetadata.ops());

        orchestrator =
                new AgentOrchestrator(
                        List.of(routerAgent, dataAgent, opsAgent),
                        auditService,
                        metricsService,
                        executionControl);
    }

    @Nested
    @DisplayName("获取可用 Agent")
    class GetAvailableAgents {

        @Test
        @DisplayName("应返回所有注册的 Agent")
        void shouldReturnAllRegisteredAgents() {
            List<AgentMetadata> agents = orchestrator.getAvailableAgents();

            assertEquals(3, agents.size());
            assertTrue(agents.stream().anyMatch(a -> a.name().equals("router")));
            assertTrue(agents.stream().anyMatch(a -> a.name().equals("data")));
            assertTrue(agents.stream().anyMatch(a -> a.name().equals("ops")));
        }

        @Test
        @DisplayName("应根据名称获取 Agent")
        void shouldGetAgentByName() {
            Optional<Agent> found = orchestrator.getAgent("data");

            assertTrue(found.isPresent());
            assertEquals("data", found.get().getMetadata().name());
        }

        @Test
        @DisplayName("不存在的 Agent 应返回空")
        void shouldReturnEmptyForNonExistentAgent() {
            Optional<Agent> found = orchestrator.getAgent("nonexistent");
            assertTrue(found.isEmpty());
        }
    }

    @Nested
    @DisplayName("选择 Agent")
    class SelectAgent {

        @Test
        @DisplayName("应根据 canHandle 置信度选择最佳 Agent")
        void shouldSelectBestAgentBasedOnConfidence() {
            AgentRequest request =
                    AgentRequest.of(
                            "sess-1",
                            "user-1",
                            "查询数据库",
                            Map.of(),
                            AgentRequestOptions.defaults(),
                            "127.0.0.1",
                            "test");

            Agent selected = orchestrator.selectAgent(request);

            // DataAgent 的 canHandle 返回 0.8，应该被选中
            assertNotNull(selected);
            assertEquals("data", selected.getMetadata().name());
        }
    }

    @Nested
    @DisplayName("执行控制")
    class ExecutionControl {

        @Test
        @DisplayName("应正确获取活跃执行数")
        void shouldGetActiveExecutionCount() {
            when(executionControl.getActiveExecutionCount()).thenReturn(5);

            int count = orchestrator.getActiveExecutionCount();
            assertEquals(5, count);
        }

        @Test
        @DisplayName("应正确检查全局并发限制")
        void shouldCheckGlobalConcurrencyLimit() {
            when(executionControl.canStartNewExecution()).thenReturn(true);

            boolean canStart = orchestrator.canStartNewExecution();
            assertTrue(canStart);
        }
    }
}
