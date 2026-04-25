package com.jonychen.agent.security;

import static org.junit.jupiter.api.Assertions.*;

import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Agent 命令注入安全测试
 *
 * <p>验证工具参数校验是否能阻止各类命令注入和路径遍历攻击。
 *
 * <h2>测试场景</h2>
 *
 * <ul>
 *   <li>测试类名命令注入
 *   <li>测试方法名注入
 *   <li>路径遍历攻击
 *   <li>文件名注入
 * </ul>
 *
 * @author jonychen
 */
class AgentCommandInjectionTest {

    // 包名前缀白名单
    private static final String ALLOWED_PACKAGE_PREFIX = "com.jonychen.";

    // 测试方法名合法字符正则
    private static final Pattern METHOD_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    /**
     * 测试类名校验 - 合法类名
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "com.jonychen.ChatControllerTest",
        "com.jonychen.agent.core.AgentAuditServiceTest",
        "com.jonychen.tool.builtin.DatabaseToolsTest"
    })
    @DisplayName("合法类名应通过校验")
    void testValidTestClassNames(String className) {
        assertTrue(isValidTestClass(className));
    }

    /**
     * 测试类名校验 - 非法类名
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "java.lang.Runtime",
        "org.example.MaliciousTest",
        "com.jonychen;rm -rf /",
        "com.jonychen.Test && cat /etc/passwd",
        "com.jonychen.Test`whoami`",
        "com.jonychen.Test$(id)"
    })
    @DisplayName("非法类名应被拦截")
    void testInvalidTestClassNames(String className) {
        assertFalse(isValidTestClass(className),
                "非法类名应被拦截: " + className);
    }

    /**
     * 测试方法名校验 - 合法方法名
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "testChat",
        "test_chat",
        "testChat1",
        "test"
    })
    @DisplayName("合法方法名应通过校验")
    void testValidMethodNames(String methodName) {
        assertTrue(METHOD_NAME_PATTERN.matcher(methodName).matches());
    }

    /**
     * 测试方法名校验 - 非法方法名
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "test;rm -rf /",
        "test && whoami",
        "test`id`",
        "test$(cat /etc/passwd)",
        "test|ls",
        "test\ncat /etc/passwd",
        "../../../bin/bash"
    })
    @DisplayName("非法方法名应被拦截")
    void testInvalidMethodNames(String methodName) {
        assertFalse(METHOD_NAME_PATTERN.matcher(methodName).matches(),
                "非法方法名应被拦截: " + methodName);
    }

    /**
     * 测试路径遍历攻击 - 非法路径
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "../../../etc/passwd",
        "..\\..\\..\\windows\\system32\\config\\sam",
        "/etc/passwd",
        "\\etc\\passwd",
        "~/../../etc/passwd",
        "src/../../../etc/passwd",
        "src/..\\..\\..\\etc\\passwd"
    })
    @DisplayName("路径遍历攻击应被拦截")
    void testPathTraversalAttacks(String path) {
        assertFalse(isValidPath(path),
                "路径遍历攻击应被拦截: " + path);
    }

    /**
     * 测试路径遍历攻击 - 合法路径
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "src/main/java/com/jonychen/ChatController.java",
        "src/test/java/ChatControllerTest.java",
        "frontend/src/components/ChatView.vue",
        "docs/README.md"
    })
    @DisplayName("合法路径应通过校验")
    void testValidPaths(String path) {
        assertTrue(isValidPath(path),
                "合法路径应通过校验: " + path);
    }

    /**
     * 测试绝对路径攻击
     */
    @Test
    @DisplayName("绝对路径应被拦截")
    void testAbsolutePathAttack() {
        String[] absolutePaths = {
            "/etc/passwd",
            "C:\\Windows\\System32\\config\\SAM",
            "file:///etc/passwd"
        };

        for (String path : absolutePaths) {
            assertFalse(isValidPath(path),
                    "绝对路径应被拦截: " + path);
        }
    }

    /**
     * 测试文件名注入
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "test;echo pwned",
        "test&&whoami",
        "test|cat /etc/passwd",
        "test`id`",
        "test$(whoami)",
        "test\necho pwned",
        "test\twhoami"
    })
    @DisplayName("文件名注入应被拦截")
    void testFileNameInjection(String fileName) {
        assertFalse(isValidFileName(fileName),
                "文件名注入应被拦截: " + fileName);
    }

    /**
     * 测试参数注入 - SQL 命令组合
     */
    @Test
    @DisplayName("参数不应包含命令分隔符")
    void testParameterInjection() {
        String[] injections = {
            "value; DROP TABLE users;",
            "value && cat /etc/passwd",
            "value || whoami",
            "value & dir",
            "value | ls -la"
        };

        for (String param : injections) {
            assertTrue(containsCommandInjection(param),
                    "应检测到命令注入: " + param);
        }
    }

    /**
     * 测试空值和边界情况
     */
    @Test
    @DisplayName("空值和边界情况应正确处理")
    void testNullAndBoundaryCases() {
        // null 应被拒绝
        assertFalse(isValidTestClass(null));
        assertFalse(isValidPath(null));

        // 空字符串应被拒绝
        assertFalse(isValidTestClass(""));
        assertFalse(isValidPath(""));

        // 仅空白字符应被拒绝
        assertFalse(isValidTestClass("   "));
        assertFalse(isValidPath("   "));

        // 合法的单字符文件名
        assertTrue(isValidFileName("a"));
        assertTrue(isValidFileName("A"));
    }

    /**
     * 测试 Unicode 绕过尝试
     */
    @Test
    @DisplayName("Unicode 绕过应被检测")
    void testUnicodeBypass() {
        // Unicode 换行符
        String unicodeNewline = "test
cat /etc/passwd";
        assertFalse(isValidFileName(unicodeNewline));

        // Unicode 控制字符
        String unicodeControl = "test
whoami";
        assertFalse(isValidFileName(unicodeControl));
    }

    // ===== 辅助方法 =====

    private boolean isValidTestClass(String className) {
        if (className == null || className.isBlank()) {
            return false;
        }
        // 必须以允许的包名开头
        if (!className.startsWith(ALLOWED_PACKAGE_PREFIX)) {
            return false;
        }
        // 不能包含危险字符
        return !containsDangerousCharacters(className);
    }

    private boolean isValidPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        // 不能包含路径遍历
        if (path.contains("..") || path.contains("~")) {
            return false;
        }
        // 不能是绝对路径
        if (path.startsWith("/") || path.startsWith("\\")) {
            return false;
        }
        return !containsDangerousCharacters(path);
    }

    private boolean isValidFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        // 文件名只能包含合法字符
        return fileName.matches("^[a-zA-Z0-9_.\\-]+$");
    }

    private boolean containsDangerousCharacters(String input) {
        if (input == null) return true;
        String lower = input.toLowerCase();
        return lower.contains(";")
                || lower.contains("&&")
                || lower.contains("||")
                || lower.contains("|")
                || lower.contains("`")
                || lower.contains("$(")
                || lower.contains("\n")
                || lower.contains("\r")
                || lower.contains("\t");
    }

    private boolean containsCommandInjection(String input) {
        if (input == null) return false;
        String lower = input.toLowerCase();
        return lower.contains(";")
                || lower.contains("&&")
                || lower.contains("||")
                || lower.contains("|")
                || lower.contains("`")
                || lower.contains("$(");
    }
}
