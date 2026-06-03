package com.jonychen.agent.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jonychen.agent.core.AgentMetadata;
import com.jonychen.tool.RiskLevel;
import com.jonychen.tool.ToolDefinition;

@ExtendWith(MockitoExtension.class)
class AgentPermissionServiceTest {

    @Mock private ToolDefinition tool;

    private AgentPermissionService permissionService;

    @BeforeEach
    void setUp() {
        permissionService = new AgentPermissionService();
    }

    @Nested
    @DisplayName("Agent 执行权限")
    class ExecuteAgent {

        @Test
        @DisplayName("ADMIN 可以执行 OPS Agent")
        void adminCanExecuteOpsAgent() {
            AgentMetadata opsMeta = AgentMetadata.ops();
            assertTrue(permissionService.canExecuteAgent(AgentRole.ADMIN, opsMeta));
        }

        @Test
        @DisplayName("普通用户不能执行 OPS Agent")
        void userCannotExecuteOpsAgent() {
            AgentMetadata opsMeta = AgentMetadata.ops();
            assertFalse(permissionService.canExecuteAgent(AgentRole.USER, opsMeta));
        }

        @Test
        @DisplayName("普通用户可以执行 DATA Agent")
        void userCanExecuteDataAgent() {
            AgentMetadata dataMeta = AgentMetadata.data();
            assertTrue(permissionService.canExecuteAgent(AgentRole.USER, dataMeta));
        }

        @Test
        @DisplayName("CHAT Agent 无需特殊权限")
        void chatAgentRequiresNoPermission() {
            AgentMetadata chatMeta = AgentMetadata.chat();
            assertTrue(permissionService.canExecuteAgent(AgentRole.USER, chatMeta));
        }

        @Test
        @DisplayName("null 元信息应返回 false")
        void nullMetadataShouldReturnFalse() {
            assertFalse(permissionService.canExecuteAgent(AgentRole.ADMIN, null));
        }
    }

    @Nested
    @DisplayName("工具执行权限")
    class ExecuteTool {

        @Test
        @DisplayName("无角色限制的工具任何人可以执行")
        void noRoleRestrictionToolCanBeExecutedByAnyone() {
            when(tool.allowedRoles()).thenReturn(List.of());

            assertTrue(permissionService.canExecuteTool(AgentRole.USER, tool));
            assertTrue(permissionService.canExecuteTool(AgentRole.ADMIN, tool));
        }

        @Test
        @DisplayName("ADMIN 限制的工具只有 ADMIN 可以执行")
        void adminOnlyToolCanBeExecutedByAdmin() {
            when(tool.allowedRoles()).thenReturn(List.of("ADMIN"));

            assertTrue(permissionService.canExecuteTool(AgentRole.ADMIN, tool));
            assertFalse(permissionService.canExecuteTool(AgentRole.USER, tool));
        }

        @Test
        @DisplayName("null 工具应返回 false")
        void nullToolShouldReturnFalse() {
            assertFalse(permissionService.canExecuteTool(AgentRole.ADMIN, null));
        }
    }

    @Nested
    @DisplayName("工具确认")
    class ToolConfirmation {

        @Test
        @DisplayName("CRITICAL 级别工具必须确认")
        void criticalToolRequiresConfirmation() {
            lenient().when(tool.riskLevel()).thenReturn(RiskLevel.CRITICAL);
            when(tool.requiresConfirmation()).thenReturn(true);

            assertTrue(permissionService.requiresConfirmation(tool, true));
        }

        @Test
        @DisplayName("HIGH 级别工具需要确认")
        void highToolRequiresConfirmation() {
            lenient().when(tool.riskLevel()).thenReturn(RiskLevel.HIGH);
            when(tool.requiresConfirmation()).thenReturn(true);

            assertTrue(permissionService.requiresConfirmation(tool, true));
        }

        @Test
        @DisplayName("LOW 级别工具无需确认")
        void lowToolDoesNotRequireConfirmation() {
            when(tool.riskLevel()).thenReturn(RiskLevel.LOW);
            when(tool.requiresConfirmation()).thenReturn(false);

            assertFalse(permissionService.requiresConfirmation(tool, true));
        }

        @Test
        @DisplayName("执行选项不需要确认时不确认")
        void noConfirmationWhenOptionDisabled() {
            lenient().when(tool.riskLevel()).thenReturn(RiskLevel.CRITICAL);
            lenient().when(tool.requiresConfirmation()).thenReturn(true);

            assertFalse(permissionService.requiresConfirmation(tool, false));
        }
    }
}
