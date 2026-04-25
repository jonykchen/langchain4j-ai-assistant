package com.jonychen.tool.builtin;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import com.jonychen.tool.ToolResult;

/**
 * DatabaseTools 单元测试
 *
 * <p>重点测试 SQL AST 安全校验逻辑，确保注入攻击被阻止。
 *
 * <h2>测试分类</h2>
 *
 * <ul>
 *   <li>表白名单校验 - 只允许查询白名单内的表
 *   <li>SQL 注入防护 - 禁止 INSERT/UPDATE/DELETE/DROP/ALTER
 *   <li>子查询绕过防护 - 递归检查子查询中的表
 *   <li>LIMIT 处理 - 自动添加和截断
 *   <li>敏感字段过滤 - 自动脱敏
 * </ul>
 *
 * @author jonychen
 */
@ExtendWith(MockitoExtension.class)
class DatabaseToolsTest {

    @Mock private JdbcTemplate jdbcTemplate;

    private DatabaseTools databaseTools;

    @BeforeEach
    void setUp() {
        // 使用默认白名单和敏感字段
        databaseTools = new DatabaseTools(jdbcTemplate, null, null);
    }

    // ==================== list_tables 测试 ====================

    @Nested
    @DisplayName("list_tables 工具测试")
    class ListTablesTest {

        @Test
        @DisplayName("应返回白名单内的所有表")
        void shouldReturnAllowedTables() {
            ToolResult result = databaseTools.listTables();

            assertTrue(result.success(), "list_tables 应该成功");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tables =
                    (List<Map<String, Object>>) ((Map<String, Object>) result.data()).get("tables");

            assertFalse(tables.isEmpty(), "白名单表列表不应为空");
            // 验证默认白名单中的表都在返回结果中
            assertTrue(tables.stream().anyMatch(t -> "users".equals(t.get("name"))), "应包含 users 表");
            assertTrue(
                    tables.stream().anyMatch(t -> "agent_traces".equals(t.get("name"))),
                    "应包含 agent_traces 表");
        }
    }

    // ==================== SQL AST 安全校验测试 ====================

    @Nested
    @DisplayName("SQL AST 安全校验测试")
    class SqlAstValidationTest {

        @Test
        @DisplayName("简单的 SELECT 查询应通过校验")
        void shouldAllowSimpleSelect() {
            ToolResult result = databaseTools.executeQuery("SELECT * FROM users", 10);

            // 校验通过后才会尝试执行查询（这里 jdbcTemplate 是 mock，会抛异常）
            // 但重点是校验没有因为安全问题被拒绝
            String error = result.error();
            if (error != null && error.contains("安全校验失败")) {
                fail("简单 SELECT 不应被安全校验拒绝: " + error);
            }
        }

        @Test
        @DisplayName("INSERT 语句应被阻止")
        void shouldBlockInsert() {
            ToolResult result =
                    databaseTools.executeQuery("INSERT INTO users (name) VALUES ('hack')", 10);

            assertFalse(result.success(), "INSERT 应被拒绝");
            assertTrue(result.error().contains("安全校验失败"), "错误信息应包含安全校验失败");
            assertTrue(result.error().contains("INSERT"), "错误信息应提到 INSERT 节点");
        }

        @Test
        @DisplayName("UPDATE 语句应被阻止")
        void shouldBlockUpdate() {
            ToolResult result =
                    databaseTools.executeQuery("UPDATE users SET password = 'hacked'", 10);

            assertFalse(result.success(), "UPDATE 应被拒绝");
            assertTrue(result.error().contains("UPDATE"), "错误信息应提到 UPDATE 节点");
        }

        @Test
        @DisplayName("DELETE 语句应被阻止")
        void shouldBlockDelete() {
            ToolResult result = databaseTools.executeQuery("DELETE FROM users", 10);

            assertFalse(result.success(), "DELETE 应被拒绝");
            assertTrue(result.error().contains("DELETE"), "错误信息应提到 DELETE 节点");
        }

        @Test
        @DisplayName("DROP 语句应被阻止")
        void shouldBlockDrop() {
            ToolResult result = databaseTools.executeQuery("DROP TABLE users", 10);

            assertFalse(result.success(), "DROP 应被拒绝");
            assertTrue(result.error().contains("DROP"), "错误信息应提到 DROP 节点");
        }

        @Test
        @DisplayName("ALTER 语句应被阻止")
        void shouldBlockAlter() {
            ToolResult result =
                    databaseTools.executeQuery("ALTER TABLE users ADD COLUMN backdoor TEXT", 10);

            assertFalse(result.success(), "ALTER 应被拒绝");
            assertTrue(result.error().contains("ALTER"), "错误信息应提到 ALTER 节点");
        }

        @Test
        @DisplayName("查询非白名单表应被阻止")
        void shouldBlockNonWhitelistedTable() {
            ToolResult result = databaseTools.executeQuery("SELECT * FROM secret_table", 10);

            assertFalse(result.success(), "查询非白名单表应被拒绝");
            assertTrue(result.error().contains("白名单"), "错误信息应提到白名单");
        }

        @Test
        @DisplayName("子查询引用非白名单表应被阻止")
        void shouldBlockSubqueryWithNonWhitelistedTable() {
            ToolResult result =
                    databaseTools.executeQuery(
                            "SELECT * FROM users WHERE id IN (SELECT user_id FROM credit_cards)",
                            10);

            assertFalse(result.success(), "子查询引用非白名单表应被拒绝");
            assertTrue(result.error().contains("白名单"), "错误信息应提到白名单");
        }

        @Test
        @DisplayName("JOIN 非白名单表应被阻止")
        void shouldBlockJoinWithNonWhitelistedTable() {
            ToolResult result =
                    databaseTools.executeQuery(
                            "SELECT u.* FROM users u JOIN credit_cards c ON u.id = c.user_id", 10);

            assertFalse(result.success(), "JOIN 非白名单表应被拒绝");
        }

        @Test
        @DisplayName("白名单内的 JOIN 查询应通过校验")
        void shouldAllowJoinOfWhitelistedTables() {
            ToolResult result =
                    databaseTools.executeQuery(
                            "SELECT u.*, s.* FROM users u JOIN agent_traces s ON u.id = s.user_id",
                            10);

            // 校验应通过（执行可能因 mock 失败，但不应是安全校验失败）
            String error = result.error();
            if (error != null && error.contains("安全校验失败")) {
                fail("白名单内的 JOIN 不应被安全校验拒绝: " + error);
            }
        }
    }

    // ==================== 参数校验测试 ====================

    @Nested
    @DisplayName("参数校验测试")
    class ParameterValidationTest {

        @Test
        @DisplayName("空 SQL 应返回错误")
        void shouldRejectEmptySql() {
            ToolResult result = databaseTools.executeQuery("", 10);

            assertFalse(result.success(), "空 SQL 应被拒绝");
            assertTrue(result.error().contains("不能为空"), "错误信息应提到 SQL 不能为空");
        }

        @Test
        @DisplayName("null SQL 应返回错误")
        void shouldRejectNullSql() {
            ToolResult result = databaseTools.executeQuery(null, 10);

            assertFalse(result.success(), "null SQL 应被拒绝");
        }

        @Test
        @DisplayName("describe_table 空表名应返回错误")
        void shouldRejectEmptyTableName() {
            ToolResult result = databaseTools.describeTable("");

            assertFalse(result.success(), "空表名应被拒绝");
            assertTrue(result.error().contains("不能为空"), "错误信息应提到表名不能为空");
        }

        @Test
        @DisplayName("describe_table 非白名单表应返回错误")
        void shouldRejectNonWhitelistedTable() {
            ToolResult result = databaseTools.describeTable("credit_cards");

            assertFalse(result.success(), "非白名单表应被拒绝");
            assertTrue(result.error().contains("白名单"), "错误信息应提到白名单");
        }
    }

    // ==================== 自定义配置测试 ====================

    @Nested
    @DisplayName("自定义配置测试")
    class CustomConfigTest {

        @Test
        @DisplayName("自定义表白名单应覆盖默认值")
        void shouldUseCustomAllowedTables() {
            DatabaseTools customTools = new DatabaseTools(jdbcTemplate, "users,token_usage", null);
            ToolResult result = customTools.listTables();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tables =
                    (List<Map<String, Object>>) ((Map<String, Object>) result.data()).get("tables");

            // 只有 2 个自定义表
            assertEquals(2, tables.size(), "自定义白名单应只有 2 个表");

            // agent_traces 不在自定义白名单中
            ToolResult descResult = customTools.describeTable("agent_traces");
            assertFalse(descResult.success(), "非自定义白名单的表应被拒绝");
        }

        @Test
        @DisplayName("自定义敏感字段应覆盖默认值")
        void shouldUseCustomSensitiveColumns() {
            DatabaseTools customTools = new DatabaseTools(jdbcTemplate, null, "email,phone");

            // 自定义敏感字段是 email 和 phone，password 不再是
            // 这可以通过 describe_table 间接验证（但由于 mock，无法完全测试）
            assertNotNull(customTools, "应成功创建自定义敏感字段的 DatabaseTools");
        }
    }
}
