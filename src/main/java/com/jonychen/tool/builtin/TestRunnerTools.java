package com.jonychen.tool.builtin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.jonychen.tool.AgentTool;
import com.jonychen.tool.RiskLevel;
import com.jonychen.tool.ToolCategory;
import com.jonychen.tool.ToolParam;
import com.jonychen.tool.ToolResult;

/**
 * 测试执行工具集
 *
 * <p>提供安全的测试执行能力，供 TestAgent 调用。
 *
 * <h2>安全机制</h2>
 *
 * <ol>
 *   <li>包名白名单（默认只允许 com.jonychen.）
 *   <li>类名/方法名格式校验（正则表达式）
 *   <li>命令参数消毒（防止命令注入）
 *   <li>执行超时控制（默认 120 秒）
 *   <li>并发执行限制（Semaphore）
 * </ol>
 *
 * <h2>工具列表</h2>
 *
 * <ul>
 *   <li>{@code run_test} - 执行指定测试类/方法
 *   <li>{@code run_all_tests} - 执行全部测试
 * </ul>
 *
 * @author jonychen
 */
@Component
public class TestRunnerTools {

    private static final Logger log = LoggerFactory.getLogger(TestRunnerTools.class);

    /** 允许的包名前缀白名单 */
    private final Set<String> allowedPackages;

    /** 测试执行超时（秒） */
    private final int timeoutSeconds;

    /** 并发执行限制 */
    private final Semaphore executionSemaphore;

    /** 类名/方法名合法字符正则（防止命令注入） */
    private static final Pattern SAFE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._$#]+$");

    /** 默认允许的包名前缀 */
    private static final Set<String> DEFAULT_ALLOWED_PACKAGES = Set.of("com.jonychen.");

    /** 默认执行超时 */
    private static final int DEFAULT_TIMEOUT_SECONDS = 120;

    /** Maven 命令 */
    private static final String MVN_CMD =
            System.getProperty("os.name", "").toLowerCase().contains("win") ? "mvn.cmd" : "mvn";

    public TestRunnerTools(
            @Value("${agent.test.allowed-packages:com.jonychen.}") String allowedPackagesConfig,
            @Value("${agent.test.timeout-seconds:120}") int timeoutSeconds) {

        this.allowedPackages = parseAllowedPackages(allowedPackagesConfig);
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_TIMEOUT_SECONDS;
        this.executionSemaphore = new Semaphore(2); // 最多 2 个并发测试

        log.info(
                "[TestRunnerTools] 初始化完成: allowedPackages={}, timeout={}s",
                allowedPackages,
                this.timeoutSeconds);
    }

    /**
     * 执行指定测试类或方法
     *
     * <p>支持：
     *
     * <ul>
     *   <li>指定类名：执行整个测试类
     *   <li>指定类名#方法名：执行单个测试方法
     * </ul>
     *
     * @param testClass 测试类全限定名（如 com.jonychen.tool.builtin.DatabaseToolsTest）
     * @param testMethod 测试方法名（可选，如 testListTables）
     * @return 执行结果
     */
    @AgentTool(
            name = "run_test",
            description = "执行指定的测试类或单个测试方法",
            category = ToolCategory.TEST,
            riskLevel = RiskLevel.MEDIUM)
    public ToolResult runTest(
            @ToolParam(
                            name = "testClass",
                            description = "测试类全限定名，如 com.jonychen.tool.builtin.DatabaseToolsTest",
                            required = true)
                    String testClass,
            @ToolParam(
                            name = "testMethod",
                            description = "测试方法名（可选），如 testListTables",
                            defaultValue = "")
                    String testMethod) {

        log.info("[TestRunnerTools] 执行 run_test: class={}, method={}", testClass, testMethod);

        // 参数校验
        if (testClass == null || testClass.isBlank()) {
            log.warn("[TestRunnerTools] run_test 参数错误: testClass 为空");
            return ToolResult.failure("测试类名不能为空");
        }

        // 安全检查
        String securityError = validateTestIdentifier(testClass, testMethod);
        if (securityError != null) {
            log.warn("[TestRunnerTools] 安全检查失败: {}", securityError);
            return ToolResult.failure(securityError);
        }

        // 构建命令
        String testSpecifier =
                testMethod != null && !testMethod.isBlank()
                        ? testClass + "#" + testMethod
                        : testClass;

        try {
            // 并发限制
            if (!executionSemaphore.tryAcquire(5, TimeUnit.SECONDS)) {
                return ToolResult.failure("测试执行繁忙，请稍后重试");
            }

            try {
                // 执行测试
                TestExecutionResult result = executeMvnTest(testSpecifier);

                return ToolResult.success(
                        Map.of(
                                "success", result.success,
                                "testClass", testClass,
                                "testMethod", testMethod != null ? testMethod : "all",
                                "testsRun", result.testsRun,
                                "testsPassed", result.testsPassed,
                                "testsFailed", result.testsFailed,
                                "testsSkipped", result.testsSkipped,
                                "durationMs", result.durationMs,
                                "output", result.output,
                                "error", result.error != null ? result.error : ""));
            } finally {
                executionSemaphore.release();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.failure("测试执行被中断");
        } catch (Exception e) {
            log.error("[TestRunnerTools] 测试执行失败: {}", e.getMessage(), e);
            return ToolResult.failure("测试执行失败: " + e.getMessage());
        }
    }

    /**
     * 执行全部测试
     *
     * <p>运行项目中的所有测试用例。
     *
     * @return 执行结果
     */
    @AgentTool(
            name = "run_all_tests",
            description = "执行项目中的全部测试用例",
            category = ToolCategory.TEST,
            riskLevel = RiskLevel.HIGH)
    public ToolResult runAllTests() {

        log.info("[TestRunnerTools] 执行 run_all_tests");

        try {
            // 并发限制
            if (!executionSemaphore.tryAcquire(5, TimeUnit.SECONDS)) {
                return ToolResult.failure("测试执行繁忙，请稍后重试");
            }

            try {
                // 执行全部测试
                TestExecutionResult result = executeMvnTest(null);

                return ToolResult.success(
                        Map.of(
                                "success", result.success,
                                "testsRun", result.testsRun,
                                "testsPassed", result.testsPassed,
                                "testsFailed", result.testsFailed,
                                "testsSkipped", result.testsSkipped,
                                "durationMs", result.durationMs,
                                "output", truncateOutput(result.output),
                                "error", result.error != null ? result.error : ""));
            } finally {
                executionSemaphore.release();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.failure("测试执行被中断");
        } catch (Exception e) {
            log.error("[TestRunnerTools] 全部测试执行失败: {}", e.getMessage(), e);
            return ToolResult.failure("测试执行失败: " + e.getMessage());
        }
    }

    // ==================== 私有方法 ====================

    /** 校验测试标识符安全性 */
    private String validateTestIdentifier(String testClass, String testMethod) {
        // 校验类名格式
        if (!SAFE_NAME_PATTERN.matcher(testClass).matches()) {
            return "测试类名包含非法字符: " + testClass;
        }

        // 校验包名白名单
        boolean packageAllowed = allowedPackages.stream().anyMatch(testClass::startsWith);
        if (!packageAllowed) {
            return "测试类不在允许的包范围内: " + testClass + "，允许的包前缀: " + allowedPackages;
        }

        // 校验方法名格式
        if (testMethod != null && !testMethod.isBlank()) {
            if (!SAFE_NAME_PATTERN.matcher(testMethod).matches()) {
                return "测试方法名包含非法字符: " + testMethod;
            }
        }

        return null;
    }

    /** 执行 Maven 测试命令 */
    private TestExecutionResult executeMvnTest(String testSpecifier)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(MVN_CMD);
        command.add("test");
        command.add("-q"); // 安静模式，减少输出
        command.add("-DskipIntegrationTests=true"); // 跳过集成测试

        if (testSpecifier != null && !testSpecifier.isBlank()) {
            command.add("-Dtest=" + testSpecifier);
        }

        long startTime = System.currentTimeMillis();

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(stripAnsiCodes(line)).append("\n");
            }
        }

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        long durationMs = System.currentTimeMillis() - startTime;

        TestExecutionResult result = new TestExecutionResult();
        result.durationMs = durationMs;
        result.output = output.toString();

        if (!finished) {
            process.destroyForcibly();
            result.error = "测试执行超时（" + timeoutSeconds + "秒）";
            result.success = false;
            return result;
        }

        int exitCode = process.exitValue();
        result.success = exitCode == 0;

        // 解析测试结果
        parseTestResults(result);

        if (exitCode != 0 && result.testsFailed == 0 && result.error == null) {
            result.error = "测试执行失败，退出码: " + exitCode;
        }

        return result;
    }

    /** 解析 Maven 测试输出 */
    private void parseTestResults(TestExecutionResult result) {
        String output = result.output;

        // 解析测试统计：Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
        java.util.regex.Pattern statsPattern =
                java.util.regex.Pattern.compile(
                        "Tests run:\\s*(\\d+),\\s*Failures:\\s*(\\d+),\\s*Errors:\\s*(\\d+),\\s*Skipped:\\s*(\\d+)");
        java.util.regex.Matcher matcher = statsPattern.matcher(output);

        int totalRun = 0;
        int totalFailures = 0;
        int totalErrors = 0;
        int totalSkipped = 0;

        while (matcher.find()) {
            totalRun += Integer.parseInt(matcher.group(1));
            totalFailures += Integer.parseInt(matcher.group(2));
            totalErrors += Integer.parseInt(matcher.group(3));
            totalSkipped += Integer.parseInt(matcher.group(4));
        }

        result.testsRun = totalRun;
        result.testsFailed = totalFailures + totalErrors;
        result.testsPassed = totalRun - totalFailures - totalErrors - totalSkipped;
        result.testsSkipped = totalSkipped;
    }

    /** 剥离 ANSI 转义码 */
    private String stripAnsiCodes(String text) {
        if (text == null) return null;
        return text.replaceAll("\\[[;\\d]*[ -/]*[@-~]", "");
    }

    /** 截断输出内容 */
    private String truncateOutput(String output) {
        if (output == null) return "";
        if (output.length() > 5000) {
            return output.substring(0, 5000) + "\n... [输出已截断]";
        }
        return output;
    }

    /** 解析允许的包名前缀配置 */
    private Set<String> parseAllowedPackages(String config) {
        if (config == null || config.isBlank()) {
            return DEFAULT_ALLOWED_PACKAGES;
        }

        Set<String> packages = new java.util.HashSet<>();
        for (String pkg : config.split(",")) {
            String trimmed = pkg.trim();
            if (!trimmed.isEmpty()) {
                // 确保以 . 结尾
                if (!trimmed.endsWith(".")) {
                    trimmed = trimmed + ".";
                }
                packages.add(trimmed);
            }
        }
        return packages.isEmpty() ? DEFAULT_ALLOWED_PACKAGES : packages;
    }

    /** 测试执行结果 */
    private static class TestExecutionResult {
        boolean success;
        int testsRun;
        int testsPassed;
        int testsFailed;
        int testsSkipped;
        long durationMs;
        String output;
        String error;
    }
}
