package com.jonychen.agent.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.jonychen.agent.core.AgentMetadata;
import com.jonychen.tool.RiskLevel;
import com.jonychen.tool.ToolDefinition;

/**
 * Agent 越权安全测试
 *
 * <p>验证权限校验逻辑是否能阻止各种越权攻击。
 *
 * <h2>测试场景</h2>
 *
 * <ul>
 *   <li>普通用户执行 ADMIN 权限的 Agent
 *   <li>普通用户调用需要 ADMIN 权限的工具
 *   <li>CRITICAL 级别工具需要确认（即使 ADMIN 也需要）
 *   <li>跨用户执行确认
 * </ul>
 *
 * @author jonychen
 */
@ExtendWith(MockitoExtension.class)
class AgentAuthorizationTest {

    @Mock private Authentication adminAuth;

    @Mock private Authentication userAuth;

    private AgentPermissionService permissionService;

    @BeforeEach
    void setUp() {
        permissionService = new AgentPermissionService();

        // 配置 ADMIN 用户
        when(adminAuth.getAuthorities())
                .thenReturn(Set.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(adminAuth.isAuthenticated()).thenReturn(true);

        // 配置普通用户
        when(userAuth.getAuthorities()).thenReturn(Set.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(userAuth.isAuthenticated()).thenReturn(true);
    }

    /** 测试 ADMIN 用户可以执行 OpsAgent */
    @Test
    @DisplayName("ADMIN 用户可以执行 OpsAgent")
    void testAdminCanExecuteOpsAgent() {
        assertTrue(permissionService.canExecuteAgent(adminAuth, "ops"));
    }

    /** 测试普通用户不能执行 OpsAgent */
    @Test
    @DisplayName("普通用户不能执行 OpsAgent")
    void testUserCannotExecuteOpsAgent() {
        assertFalse(permissionService.canExecuteAgent(userAuth, "ops"));
    }

    /** 测试普通用户可以执行 DataAgent */
    @Test
    @DisplayName("普通用户可以执行 DataAgent")
    void testUserCanExecuteDataAgent() {
        assertTrue(permissionService.canExecuteAgent(userAuth, "data"));
    }

    /** 测试 CRITICAL 级别工具需要确认（ADMIN 也不例外） */
    @Test
    @DisplayName("CRITICAL 级别工具即使 ADMIN 也需要确认")
    void testCriticalToolRequiresConfirmationEvenForAdmin() {
        ToolDefinition criticalTool = mock(ToolDefinition.class);
        when(criticalTool.riskLevel()).thenReturn(RiskLevel.CRITICAL);
        when(criticalTool.requiresConfirmation()).thenReturn(true);

        // CRITICAL 级别必须确认，无豁免
        assertTrue(permissionService.requiresConfirmation(adminAuth, criticalTool));
    }

    /** 测试 HIGH 级别工具 ADMIN 可以豁免确认 */
    @Test
    @DisplayName("HIGH 级别工具 ADMIN 可以豁免确认")
    void testHighToolCanBeWaivedForAdmin() {
        ToolDefinition highTool = mock(ToolDefinition.class);
        when(highTool.riskLevel()).thenReturn(RiskLevel.HIGH);
        when(highTool.requiresConfirmation()).thenReturn(true);

        // HIGH 级别 ADMIN 可豁免
        assertFalse(permissionService.requiresConfirmationForAdmin(highTool));
    }

    /** 测试 HIGH 级别工具普通用户必须确认 */
    @Test
    @DisplayName("HIGH 级别工具普通用户必须确认")
    void testHighToolRequiresConfirmationForUser() {
        ToolDefinition highTool = mock(ToolDefinition.class);
        when(highTool.riskLevel()).thenReturn(RiskLevel.HIGH);
        when(highTool.requiresConfirmation()).thenReturn(true);

        // HIGH 级别普通用户必须确认
        assertTrue(permissionService.requiresConfirmation(userAuth, highTool));
    }

    /** 测试工具权限校验 - ADMIN 拥有 model:write 权限 */
    @Test
    @DisplayName("ADMIN 拥有 model:write 权限")
    void testAdminHasModelWritePermission() {
        assertTrue(permissionService.hasPermission(adminAuth, AgentPermission.MODEL_WRITE));
    }

    /** 测试工具权限校验 - 普通用户没有 model:write 权限 */
    @Test
    @DisplayName("普通用户没有 model:write 权限")
    void testUserDoesNotHaveModelWritePermission() {
        assertFalse(permissionService.hasPermission(userAuth, AgentPermission.MODEL_WRITE));
    }

    /** 测试工具执行权限校验 */
    @Test
    @DisplayName("工具权限校验应正确执行")
    void testToolExecutionPermission() {
        ToolDefinition tool = mock(ToolDefinition.class);
        when(tool.requiredPermissions()).thenReturn(java.util.List.of("model:write"));
        when(tool.allowedRoles()).thenReturn(java.util.List.of());

        // ADMIN 可以执行
        assertTrue(permissionService.canExecuteTool(adminAuth, tool));

        // 普通 USER 不能执行
        assertFalse(permissionService.canExecuteTool(userAuth, tool));
    }

    /** 测试角色限制校验 */
    @Test
    @DisplayName("角色限制校验应正确执行")
    void testRoleRestriction() {
        ToolDefinition adminOnlyTool = mock(ToolDefinition.class);
        when(adminOnlyTool.requiredPermissions()).thenReturn(java.util.List.of());
        when(adminOnlyTool.allowedRoles()).thenReturn(java.util.List.of("ADMIN"));

        // ADMIN 可以执行
        assertTrue(permissionService.canExecuteTool(adminAuth, adminOnlyTool));

        // 普通 USER 不能执行
        assertFalse(permissionService.canExecuteTool(userAuth, adminOnlyTool));
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
}
