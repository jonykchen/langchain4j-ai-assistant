package com.jonychen.agent.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.jonychen.tool.ToolDefinition;
import com.jonychen.tool.RiskLevel;

@ExtendWith(MockitoExtension.class)
class AgentPermissionServiceTest {

    @Mock
    private Authentication adminAuth;

    @Mock
    private Authentication userAuth;

    @Mock
    private ToolDefinition tool;

    private AgentPermissionService permissionService;

    @BeforeEach
    void setUp() {
        permissionService = new AgentPermissionService();

        // 配置 ADMIN 用户
        when(adminAuth.getAuthorities())
                .thenReturn(Set.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(adminAuth.isAuthenticated()).thenReturn(true);

        // 配置普通用户
        when(userAuth.getAuthorities())
                .thenReturn(Set.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(userAuth.isAuthenticated()).thenReturn(true);
    }

    @Nested
    @DisplayName("Agent 执行权限")
    class ExecuteAgent {

        @Test
        @DisplayName("ADMIN 可以执行高权限 Agent")
        void adminCanExecuteHighPrivilegeAgent() {
            assertTrue(permissionService.canExecuteAgent(adminAuth, "ops"));
        }

        @Test
        @DisplayName("普通用户可以执行普通 Agent")
        void userCanExecuteNormalAgent() {
            assertTrue(permissionService.canExecuteAgent(userAuth, "data"));
        }

        @Test
        @DisplayName("普通用户不能执行 ops Agent")
        void userCannotExecuteOpsAgent() {
            assertFalse(permissionService.canExecuteAgent(userAuth, "ops"));
        }

        @Test
        @DisplayName("未认证用户不能执行任何 Agent")
        void unauthenticatedCannotExecute() {
            when(userAuth.isAuthenticated()).thenReturn(false);
            assertFalse(permissionService.canExecuteAgent(userAuth, "data"));
        }

        @Test
        @DisplayName("null authentication 应返回 false")
        void nullAuthShouldReturnFalse() {
            assertFalse(permissionService.canExecuteAgent(null, "data"));
        }
    }

    @Nested
    @DisplayName("工具执行权限")
    class ExecuteTool {

        @Test
        @DisplayName("无权限要求的工具任何人可以执行")
        void noPermissionRequiredToolCanBeExecutedByAnyone() {
            when(tool.requiredPermissions()).thenReturn(List.of());
            when(tool.allowedRoles()).thenReturn(List.of());

            assertTrue(permissionService.canExecuteTool(userAuth, tool));
        }

        @Test
        @DisplayName("需要特定权限的工具只有拥有权限的用户可以执行")
        void permissionRequiredToolCanBeExecutedByAuthorizedUser() {
            when(tool.requiredPermissions()).thenReturn(List.of("model:write"));
            when(tool.allowedRoles()).thenReturn(List.of());

            // ADMIN 拥有 model:write 权限
            assertTrue(permissionService.canExecuteTool(adminAuth, tool));

            // USER 没有 model:write 权限
            assertFalse(permissionService.canExecuteTool(userAuth, tool));
        }

        @Test
        @DisplayName("角色限制的工具只有指定角色可以执行")
        void roleRestrictedToolCanBeExecutedByAllowedRole() {
            when(tool.requiredPermissions()).thenReturn(List.of());
            when(tool.allowedRoles()).thenReturn(List.of("ADMIN"));

            assertTrue(permissionService.canExecuteTool(adminAuth, tool));
            assertFalse(permissionService.canExecuteTool(userAuth, tool));
        }
    }

    @Nested
    @DisplayName("工具确认")
    class ToolConfirmation {

        @Test
        @DisplayName("CRITICAL 级别工具必须确认")
        void criticalToolRequiresConfirmation() {
            when(tool.riskLevel()).thenReturn(RiskLevel.CRITICAL);
            when(tool.requiresConfirmation()).thenReturn(true);

            assertTrue(permissionService.requiresConfirmation(userAuth, tool));
            assertTrue(permissionService.requiresConfirmation(adminAuth, tool));
        }

        @Test
        @DisplayName("HIGH 级别工具普通用户必须确认")
        void highToolRequiresConfirmationForUser() {
            when(tool.riskLevel()).thenReturn(RiskLevel.HIGH);
            when(tool.requiresConfirmation()).thenReturn(true);

            assertTrue(permissionService.requiresConfirmation(userAuth, tool));
            assertFalse(permissionService.requiresConfirmationForAdmin(tool));
        }

        @Test
        @DisplayName("LOW 级别工具无需确认")
        void lowToolDoesNotRequireConfirmation() {
            when(tool.riskLevel()).thenReturn(RiskLevel.LOW);
            when(tool.requiresConfirmation()).thenReturn(false);

            assertFalse(permissionService.requiresConfirmation(userAuth, tool));
        }
    }

    @Nested
    @DisplayName("权限检查")
    class HasPermission {

        @Test
        @DisplayName("ADMIN 拥有所有权限")
        void adminHasAllPermissions() {
            assertTrue(permissionService.hasPermission(adminAuth, AgentPermission.MODEL_WRITE));
            assertTrue(permissionService.hasPermission(adminAuth, AgentPermission.EXECUTE_CRITICAL));
        }

        @Test
        @DisplayName("普通用户只有基本权限")
        void userHasBasicPermissions() {
            assertFalse(permissionService.hasPermission(userAuth, AgentPermission.MODEL_WRITE));
            assertFalse(permissionService.hasPermission(userAuth, AgentPermission.EXECUTE_CRITICAL));
        }
    }
}
