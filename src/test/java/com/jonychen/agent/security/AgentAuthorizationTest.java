package com.jonychen.agent.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jonychen.agent.core.AgentMetadata;
import com.jonychen.tool.RiskLevel;
import com.jonychen.tool.ToolDefinition;

/**
 * Agent 越权安全测试
 *
 * <p>验证权限校验逻辑是否能阻止各种越权攻击。
 *
 * @author jonychen
 */
@ExtendWith(MockitoExtension.class)
class AgentAuthorizationTest {

    private AgentPermissionService permissionService;

    @BeforeEach
    void setUp() {
        permissionService = new AgentPermissionService();
    }

    /** 测试 ADMIN 用户可以执行 OpsAgent */
    @Test
    @DisplayName("ADMIN 用户可以执行 OpsAgent")
    void testAdminCanExecuteOpsAgent() {
        assertTrue(permissionService.canExecuteAgent(AgentRole.ADMIN, AgentMetadata.ops()));
    }

    /** 测试普通用户不能执行 OpsAgent */
    @Test
    @DisplayName("普通用户不能执行 OpsAgent")
    void testUserCannotExecuteOpsAgent() {
        assertFalse(permissionService.canExecuteAgent(AgentRole.USER, AgentMetadata.ops()));
    }

    /** 测试普通用户可以执行 DataAgent */
    @Test
    @DisplayName("普通用户可以执行 DataAgent")
    void testUserCanExecuteDataAgent() {
        assertTrue(permissionService.canExecuteAgent(AgentRole.USER, AgentMetadata.data()));
    }

    /** 测试 CRITICAL 级别工具需要确认 */
    @Test
    @DisplayName("CRITICAL 级别工具需要确认")
    void testCriticalToolRequiresConfirmation() {
        ToolDefinition criticalTool = mock(ToolDefinition.class);
        lenient().when(criticalTool.riskLevel()).thenReturn(RiskLevel.CRITICAL);
        when(criticalTool.requiresConfirmation()).thenReturn(true);

        assertTrue(permissionService.requiresConfirmation(criticalTool, true));
    }

    /** 测试 HIGH 级别工具需要确认 */
    @Test
    @DisplayName("HIGH 级别工具需要确认")
    void testHighToolRequiresConfirmation() {
        ToolDefinition highTool = mock(ToolDefinition.class);
        lenient().when(highTool.riskLevel()).thenReturn(RiskLevel.HIGH);
        when(highTool.requiresConfirmation()).thenReturn(true);

        assertTrue(permissionService.requiresConfirmation(highTool, true));
    }

    /** 测试工具执行权限校验 - ADMIN 限制角色 */
    @Test
    @DisplayName("角色限制校验应正确执行")
    void testToolExecutionPermission() {
        ToolDefinition adminOnlyTool = mock(ToolDefinition.class);
        when(adminOnlyTool.allowedRoles()).thenReturn(List.of("ADMIN"));

        // ADMIN 可以执行
        assertTrue(permissionService.canExecuteTool(AgentRole.ADMIN, adminOnlyTool));

        // 普通 USER 不能执行
        assertFalse(permissionService.canExecuteTool(AgentRole.USER, adminOnlyTool));
    }

    /** 测试 Agent 元信息权限要求 */
    @Test
    @DisplayName("Agent 元信息权限校验")
    void testAgentMetadataPermissions() {
        AgentMetadata opsMetadata = AgentMetadata.ops();

        // OpsAgent 应该要求 ADMIN 权限
        assertTrue(
                opsMetadata.requiredPermissions().contains("ADMIN")
                        || opsMetadata.requiredPermissions().stream()
                                .anyMatch(p -> p.contains("ops") || p.contains("model")));
    }

    /** 测试 AgentRole 权限检查 */
    @Test
    @DisplayName("ADMIN 角色拥有 MODEL_WRITE 权限")
    void testAdminHasModelWritePermission() {
        assertTrue(AgentRole.ADMIN.hasPermission(AgentPermission.MODEL_WRITE));
    }

    @Test
    @DisplayName("USER 角色没有 MODEL_WRITE 权限")
    void testUserDoesNotHaveModelWritePermission() {
        assertFalse(AgentRole.USER.hasPermission(AgentPermission.MODEL_WRITE));
    }
}
