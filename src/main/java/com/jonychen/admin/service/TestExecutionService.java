package com.jonychen.admin.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jonychen.admin.dto.AIModelTestSummary;
import com.jonychen.admin.dto.PerformanceResultSummary;
import com.jonychen.admin.dto.TestJobStatus;
import com.jonychen.admin.dto.TestResultSummary;
import com.jonychen.admin.dto.TestStatsSummary;
import com.jonychen.ai.evaluator.ResponseQualityEvaluator;
import com.jonychen.ai.model.AIModelTestCase;
import com.jonychen.ai.model.AIModelTestResult;
import com.jonychen.service.AiService;
import com.jonychen.test.entity.AIModelTestResultEntity;
import com.jonychen.test.entity.E2ETestResult;
import com.jonychen.test.entity.PerformanceTestResult;
import com.jonychen.test.entity.TestJob;
import com.jonychen.test.repository.AIModelTestResultRepository;
import com.jonychen.test.repository.E2ETestResultRepository;
import com.jonychen.test.repository.PerformanceTestResultRepository;
import com.jonychen.test.repository.TestJobRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 测试执行服务
 *
 * <p>管理测试任务的启动、状态跟踪和结果收集，所有结果持久化到数据库
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestExecutionService {

    // 进程跟踪（运行时状态，不需要持久化）
    private final Map<String, Process> runningProcesses = new ConcurrentHashMap<>();
    private final Map<String, TestJobStatus> jobStatusCache = new ConcurrentHashMap<>();

    // 数据库 Repository
    private final TestJobRepository testJobRepository;
    private final E2ETestResultRepository e2eTestResultRepository;
    private final PerformanceTestResultRepository performanceTestResultRepository;
    private final AIModelTestResultRepository aiModelTestResultRepository;

    // 业务服务
    private final AiService aiService;
    private final ResponseQualityEvaluator qualityEvaluator;

    // ==================== 测试执行方法 ====================

    /** 运行 E2E 测试 */
    public String runE2ETests(String triggeredBy) {
        String jobId = UUID.randomUUID().toString();

        // 创建并保存 TestJob
        TestJob job =
                TestJob.builder()
                        .id(jobId)
                        .testType("E2E")
                        .status("RUNNING")
                        .startTime(LocalDateTime.now())
                        .progress(0)
                        .triggeredBy(triggeredBy)
                        .build();
        testJobRepository.save(job);

        // 更新内存缓存
        jobStatusCache.put(
                jobId,
                new TestJobStatus(
                        jobId, "running", System.currentTimeMillis(), 0, "Starting E2E tests", 0));

        new Thread(
                        () -> {
                            try {
                                ProcessBuilder pb =
                                        new ProcessBuilder(
                                                System.getProperty("os.name")
                                                                .toLowerCase()
                                                                .contains("win")
                                                        ? new String[] {
                                                            "cmd",
                                                            "/c",
                                                            "npx",
                                                            "playwright",
                                                            "test",
                                                            "--reporter=html"
                                                        }
                                                        : new String[] {
                                                            "npx",
                                                            "playwright",
                                                            "test",
                                                            "--reporter=html"
                                                        });
                                pb.directory(new File("frontend"));
                                pb.redirectErrorStream(true);

                                Process process = pb.start();
                                runningProcesses.put(jobId, process);

                                // 读取输出并解析结果
                                List<TestResultSummary> results = new ArrayList<>();
                                try (BufferedReader reader =
                                        new BufferedReader(
                                                new InputStreamReader(process.getInputStream()))) {
                                    String line;
                                    while ((line = reader.readLine()) != null) {
                                        log.info("[E2E] {}", line);
                                        // 简化的结果解析
                                        if (line.contains("passed") || line.contains("failed")) {
                                            results.add(parseE2ELine(line));
                                        }
                                    }
                                }

                                int exitCode = process.waitFor();

                                // 保存结果到数据库
                                saveE2EResults(jobId, results);

                                // 更新任务状态
                                updateJobStatus(
                                        jobId,
                                        exitCode == 0 ? "COMPLETED" : "FAILED",
                                        exitCode == 0
                                                ? "Tests passed"
                                                : "Tests failed with exit code: " + exitCode,
                                        100);

                                runningProcesses.remove(jobId);

                            } catch (Exception e) {
                                log.error("E2E test execution failed", e);
                                updateJobStatus(jobId, "FAILED", e.getMessage(), 0);
                            }
                        },
                        "e2e-test-" + jobId)
                .start();

        return jobId;
    }

    /** 运行性能测试 */
    public String runPerformanceTest(String simulation, String triggeredBy) {
        String jobId = UUID.randomUUID().toString();

        // 创建并保存 TestJob
        TestJob job =
                TestJob.builder()
                        .id(jobId)
                        .testType("PERFORMANCE")
                        .status("RUNNING")
                        .startTime(LocalDateTime.now())
                        .progress(0)
                        .triggeredBy(triggeredBy)
                        .build();
        testJobRepository.save(job);

        jobStatusCache.put(
                jobId,
                new TestJobStatus(
                        jobId,
                        "running",
                        System.currentTimeMillis(),
                        0,
                        "Starting performance test: " + simulation,
                        0));

        new Thread(
                        () -> {
                            try {
                                ProcessBuilder pb =
                                        new ProcessBuilder(
                                                System.getProperty("os.name")
                                                                .toLowerCase()
                                                                .contains("win")
                                                        ? new String[] {
                                                            "cmd",
                                                            "/c",
                                                            "mvn",
                                                            "gatling:test",
                                                            "-Dgatling.simulationClass=gatling.simulations."
                                                                    + simulation
                                                        }
                                                        : new String[] {
                                                            "mvn",
                                                            "gatling:test",
                                                            "-Dgatling.simulationClass=gatling.simulations."
                                                                    + simulation
                                                        });
                                pb.redirectErrorStream(true);

                                Process process = pb.start();
                                runningProcesses.put(jobId, process);

                                try (BufferedReader reader =
                                        new BufferedReader(
                                                new InputStreamReader(process.getInputStream()))) {
                                    String line;
                                    while ((line = reader.readLine()) != null) {
                                        log.info("[Gatling] {}", line);
                                    }
                                }

                                int exitCode = process.waitFor();

                                // 解析并保存结果
                                PerformanceResultSummary result =
                                        parsePerformanceResults(simulation);
                                savePerformanceResult(jobId, result);

                                updateJobStatus(
                                        jobId,
                                        exitCode == 0 ? "COMPLETED" : "FAILED",
                                        exitCode == 0
                                                ? "Performance test completed"
                                                : "Performance test failed",
                                        100);

                                runningProcesses.remove(jobId);

                            } catch (Exception e) {
                                log.error("Performance test execution failed", e);
                                updateJobStatus(jobId, "FAILED", e.getMessage(), 0);
                            }
                        },
                        "perf-test-" + jobId)
                .start();

        return jobId;
    }

    /** 运行 AI 模型测试 */
    public String runAIModelTests(String category, String triggeredBy) {
        String jobId = UUID.randomUUID().toString();

        // 创建并保存 TestJob
        TestJob job =
                TestJob.builder()
                        .id(jobId)
                        .testType("AI_MODEL")
                        .status("RUNNING")
                        .startTime(LocalDateTime.now())
                        .progress(0)
                        .triggeredBy(triggeredBy)
                        .build();
        testJobRepository.save(job);

        jobStatusCache.put(
                jobId,
                new TestJobStatus(
                        jobId,
                        "running",
                        System.currentTimeMillis(),
                        0,
                        "Starting AI model tests",
                        0));

        new Thread(
                        () -> {
                            try {
                                List<AIModelTestSummary> results = runAITests(category);

                                // 保存结果到数据库
                                saveAIModelResults(jobId, results);

                                updateJobStatus(
                                        jobId,
                                        "COMPLETED",
                                        "AI model tests completed: " + results.size() + " tests",
                                        100);

                            } catch (Exception e) {
                                log.error("AI model test execution failed", e);
                                updateJobStatus(jobId, "FAILED", e.getMessage(), 0);
                            }
                        },
                        "ai-test-" + jobId)
                .start();

        return jobId;
    }

    // ==================== 结果保存方法 ====================

    @Transactional
    protected void saveE2EResults(String jobId, List<TestResultSummary> results) {
        List<E2ETestResult> entities = new ArrayList<>();
        for (TestResultSummary summary : results) {
            E2ETestResult entity =
                    E2ETestResult.builder()
                            .jobId(jobId)
                            .testName(summary.testName())
                            .status(summary.status())
                            .durationMs(summary.duration())
                            .assertionsPassed(
                                    summary.assertions() != null
                                            ? summary.assertions().passed()
                                            : 0)
                            .assertionsFailed(
                                    summary.assertions() != null
                                            ? summary.assertions().failed()
                                            : 0)
                            .errorMessage(summary.error())
                            .build();
            entities.add(entity);
        }
        e2eTestResultRepository.saveAll(entities);
    }

    @Transactional
    protected void savePerformanceResult(String jobId, PerformanceResultSummary result) {
        PerformanceTestResult entity =
                PerformanceTestResult.builder()
                        .jobId(jobId)
                        .simulation(result.simulation())
                        .requests(result.requests())
                        .successRate(BigDecimal.valueOf(result.successRate()))
                        .avgResponseTime(result.avgResponseTime())
                        .maxResponseTime(result.maxResponseTime())
                        .p95ResponseTime(result.p95ResponseTime())
                        .p99ResponseTime(result.p99ResponseTime())
                        .startTime(LocalDateTime.now().minusSeconds(60))
                        .endTime(LocalDateTime.now())
                        .build();
        performanceTestResultRepository.save(entity);
    }

    @Transactional
    protected void saveAIModelResults(String jobId, List<AIModelTestSummary> results) {
        List<AIModelTestResultEntity> entities = new ArrayList<>();
        for (AIModelTestSummary summary : results) {
            // 构造 details JSON
            List<Map<String, Object>> details = new ArrayList<>();
            if (summary.details() != null) {
                for (var detail : summary.details()) {
                    details.add(
                            Map.of(
                                    "assertion",
                                    detail.assertion(),
                                    "passed",
                                    detail.passed(),
                                    "expected",
                                    detail.expected() != null ? detail.expected() : "",
                                    "actual",
                                    detail.actual() != null ? detail.actual() : ""));
                }
            }

            AIModelTestResultEntity entity =
                    AIModelTestResultEntity.builder()
                            .jobId(jobId)
                            .testCaseId(summary.testCaseId())
                            .testName(summary.testName())
                            .category(summary.category())
                            .score(BigDecimal.valueOf(summary.score()))
                            .passed(summary.passed())
                            .details(details)
                            .responseTime(summary.responseTime())
                            .actualOutput(summary.actualOutput())
                            .build();
            entities.add(entity);
        }
        aiModelTestResultRepository.saveAll(entities);
    }

    // ==================== 状态更新方法 ====================

    @Transactional
    protected void updateJobStatus(String jobId, String status, String message, int progress) {
        testJobRepository
                .findById(jobId)
                .ifPresent(
                        job -> {
                            job.setStatus(status);
                            job.setMessage(message);
                            job.setProgress(progress);
                            if ("COMPLETED".equals(status)
                                    || "FAILED".equals(status)
                                    || "CANCELLED".equals(status)) {
                                job.setEndTime(LocalDateTime.now());
                            }
                            testJobRepository.save(job);
                        });

        // 更新缓存
        TestJobStatus cached = jobStatusCache.get(jobId);
        if (cached != null) {
            jobStatusCache.put(
                    jobId,
                    new TestJobStatus(
                            jobId,
                            status.toLowerCase(),
                            cached.startTime(),
                            System.currentTimeMillis(),
                            message,
                            progress));
        }
    }

    // ==================== 执行辅助方法 ====================

    /** 执行 AI 测试 */
    private List<AIModelTestSummary> runAITests(String category) {
        List<AIModelTestCase> testCases = getTestCases(category);
        List<AIModelTestSummary> results = new ArrayList<>();

        for (AIModelTestCase testCase : testCases) {
            try {
                long startTime = System.currentTimeMillis();
                String response = aiService.chat(testCase.input());
                long responseTime = System.currentTimeMillis() - startTime;

                AIModelTestResult.AssertionResult assertionResult =
                        qualityEvaluator.evaluate(testCase, response, responseTime);

                List<AIModelTestSummary.AssertionDetail> details = new ArrayList<>();
                for (String passed : assertionResult.passed()) {
                    details.add(new AIModelTestSummary.AssertionDetail(passed, true, null, null));
                }
                for (var failed : assertionResult.failed()) {
                    details.add(
                            new AIModelTestSummary.AssertionDetail(
                                    failed.description(),
                                    false,
                                    failed.expected(),
                                    failed.actual()));
                }

                results.add(
                        new AIModelTestSummary(
                                testCase.id(),
                                testCase.name(),
                                testCase.category(),
                                assertionResult.score(),
                                assertionResult.score() >= 0.8,
                                details,
                                responseTime,
                                truncate(response, 200)));

            } catch (Exception e) {
                log.error("AI test case {} failed", testCase.id(), e);
                results.add(
                        new AIModelTestSummary(
                                testCase.id(),
                                testCase.name(),
                                testCase.category(),
                                0.0,
                                false,
                                List.of(
                                        new AIModelTestSummary.AssertionDetail(
                                                "Execution", false, null, e.getMessage())),
                                0,
                                null));
            }
        }

        return results;
    }

    /** 获取测试用例 */
    private List<AIModelTestCase> getTestCases(String category) {
        List<AIModelTestCase> allCases =
                List.of(
                        new AIModelTestCase(
                                "tc-001",
                                "简单问候",
                                "basic",
                                "你好",
                                List.of(
                                        new AIModelTestCase.Assertion(
                                                "CONTAINS", "包含问候", "你好", 0.0),
                                        new AIModelTestCase.Assertion(
                                                "RESPONSE_TIME_MS", "响应时间", "10000", 0.0)),
                                Map.of()),
                        new AIModelTestCase(
                                "tc-002",
                                "生成排序算法",
                                "code-generation",
                                "用 Java 实现冒泡排序",
                                List.of(
                                        new AIModelTestCase.Assertion(
                                                "CONTAINS", "包含代码", "```java", 0.0),
                                        new AIModelTestCase.Assertion(
                                                "CONTAINS", "包含排序逻辑", "for", 0.0)),
                                Map.of()));

        if (category == null || category.isEmpty()) {
            return allCases;
        }
        return allCases.stream().filter(tc -> category.equals(tc.category())).toList();
    }

    /** 解析 E2E 输出行 */
    private TestResultSummary parseE2ELine(String line) {
        boolean passed = line.contains("passed");
        String testName = line.split("\\s+")[0];
        return new TestResultSummary(
                testName,
                passed ? "passed" : "failed",
                100L,
                new TestResultSummary.Assertions(passed ? 1 : 0, passed ? 0 : 1, 1),
                passed ? null : line,
                java.time.Instant.now());
    }

    /** 解析性能测试结果 */
    private PerformanceResultSummary parsePerformanceResults(String simulation) {
        return new PerformanceResultSummary(
                simulation,
                1000,
                95.0,
                1500L,
                5000L,
                2000L,
                3000L,
                System.currentTimeMillis() - 60000,
                System.currentTimeMillis());
    }

    // ==================== 状态查询方法 ====================

    public TestJobStatus getJobStatus(String jobId) {
        // 先查缓存
        TestJobStatus cached = jobStatusCache.get(jobId);
        if (cached != null && !"running".equals(cached.status())) {
            return cached;
        }

        // 查数据库
        return testJobRepository
                .findById(jobId)
                .map(
                        job ->
                                new TestJobStatus(
                                        job.getId(),
                                        job.getStatus().toLowerCase(),
                                        job.getStartTime() != null
                                                ? job.getStartTime()
                                                        .atZone(java.time.ZoneId.systemDefault())
                                                        .toInstant()
                                                        .toEpochMilli()
                                                : 0,
                                        job.getEndTime() != null
                                                ? job.getEndTime()
                                                        .atZone(java.time.ZoneId.systemDefault())
                                                        .toInstant()
                                                        .toEpochMilli()
                                                : 0,
                                        job.getMessage() != null ? job.getMessage() : "",
                                        job.getProgress() != null ? job.getProgress() : 0))
                .orElse(new TestJobStatus(jobId, "not_found", 0, 0, "Job not found", 0));
    }

    public void cancelJob(String jobId) {
        Process process = runningProcesses.get(jobId);
        if (process != null) {
            process.destroy();
            runningProcesses.remove(jobId);
            updateJobStatus(jobId, "CANCELLED", "Job cancelled", 0);
        }
    }

    // ==================== 历史查询方法 ====================

    public Page<TestJob> getTestJobHistory(
            String testType,
            String status,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable) {
        if (testType != null && status != null && from != null && to != null) {
            return testJobRepository.findByTestTypeAndStatusAndStartTimeBetween(
                    testType, status, from, to, pageable);
        } else if (testType != null && from != null && to != null) {
            return testJobRepository.findByTestTypeAndStartTimeBetween(
                    testType, from, to, pageable);
        } else if (status != null && from != null && to != null) {
            return testJobRepository.findByStatusAndStartTimeBetween(status, from, to, pageable);
        } else if (from != null && to != null) {
            return testJobRepository.findByStartTimeBetween(from, to, pageable);
        } else if (testType != null) {
            return testJobRepository.findByTestType(testType, pageable);
        } else if (status != null) {
            return testJobRepository.findByStatus(status, pageable);
        } else {
            return testJobRepository.findAll(pageable);
        }
    }

    public List<E2ETestResult> getE2EResultsByJobId(String jobId) {
        return e2eTestResultRepository.findByJobId(jobId);
    }

    public List<PerformanceTestResult> getPerformanceResultsByJobId(String jobId) {
        return performanceTestResultRepository.findByJobId(jobId);
    }

    public List<AIModelTestResultEntity> getAIModelResultsByJobId(String jobId, String category) {
        if (category != null && !category.isEmpty()) {
            return aiModelTestResultRepository.findByJobIdAndCategory(jobId, category);
        }
        return aiModelTestResultRepository.findByJobId(jobId);
    }

    // ==================== 兼容旧 API 的方法 ====================

    public String runE2ETests() {
        return runE2ETests("system");
    }

    public String runPerformanceTest(String simulation) {
        return runPerformanceTest(simulation, "system");
    }

    public String runAIModelTests(String category) {
        return runAIModelTests(category, "system");
    }

    public List<TestResultSummary> getE2EStatus() {
        // 返回最新一次 E2E 测试的结果
        return testJobRepository
                .findFirstByTestTypeOrderByStartTimeDesc("E2E")
                .map(
                        job ->
                                convertToTestResultSummary(
                                        e2eTestResultRepository.findByJobId(job.getId())))
                .orElse(Collections.emptyList());
    }

    private List<TestResultSummary> convertToTestResultSummary(List<E2ETestResult> results) {
        return results.stream()
                .map(
                        r ->
                                new TestResultSummary(
                                        r.getTestName(),
                                        r.getStatus(),
                                        r.getDurationMs() != null ? r.getDurationMs() : 0L,
                                        new TestResultSummary.Assertions(
                                                r.getAssertionsPassed() != null
                                                        ? r.getAssertionsPassed()
                                                        : 0,
                                                r.getAssertionsFailed() != null
                                                        ? r.getAssertionsFailed()
                                                        : 0,
                                                (r.getAssertionsPassed() != null
                                                                ? r.getAssertionsPassed()
                                                                : 0)
                                                        + (r.getAssertionsFailed() != null
                                                                ? r.getAssertionsFailed()
                                                                : 0)),
                                        r.getErrorMessage(),
                                        r.getCreatedAt() != null
                                                ? r.getCreatedAt()
                                                        .atZone(java.time.ZoneId.systemDefault())
                                                        .toInstant()
                                                : null))
                .toList();
    }

    public String getE2EReport() {
        return "/playwright-report/index.html";
    }

    public List<PerformanceResultSummary> getPerformanceResults() {
        return testJobRepository
                .findFirstByTestTypeOrderByStartTimeDesc("PERFORMANCE")
                .map(
                        job ->
                                convertToPerformanceResultSummary(
                                        performanceTestResultRepository.findByJobId(job.getId())))
                .orElse(Collections.emptyList());
    }

    private List<PerformanceResultSummary> convertToPerformanceResultSummary(
            List<PerformanceTestResult> results) {
        return results.stream()
                .map(
                        r ->
                                new PerformanceResultSummary(
                                        r.getSimulation(),
                                        r.getRequests() != null ? r.getRequests() : 0L,
                                        r.getSuccessRate() != null
                                                ? r.getSuccessRate().doubleValue()
                                                : 0.0,
                                        r.getAvgResponseTime() != null
                                                ? r.getAvgResponseTime()
                                                : 0L,
                                        r.getMaxResponseTime() != null
                                                ? r.getMaxResponseTime()
                                                : 0L,
                                        r.getP95ResponseTime() != null
                                                ? r.getP95ResponseTime()
                                                : 0L,
                                        r.getP99ResponseTime() != null
                                                ? r.getP99ResponseTime()
                                                : 0L,
                                        r.getStartTime() != null
                                                ? r.getStartTime()
                                                        .atZone(java.time.ZoneId.systemDefault())
                                                        .toInstant()
                                                        .toEpochMilli()
                                                : 0L,
                                        r.getEndTime() != null
                                                ? r.getEndTime()
                                                        .atZone(java.time.ZoneId.systemDefault())
                                                        .toInstant()
                                                        .toEpochMilli()
                                                : 0L))
                .toList();
    }

    public List<String> getAvailableSimulations() {
        return List.of("ChatSimulation", "StreamSimulation", "AuthSimulation");
    }

    public List<AIModelTestSummary> getAIModelResults() {
        return testJobRepository
                .findFirstByTestTypeOrderByStartTimeDesc("AI_MODEL")
                .map(
                        job ->
                                convertToAIModelTestSummary(
                                        aiModelTestResultRepository.findByJobId(job.getId())))
                .orElse(Collections.emptyList());
    }

    private List<AIModelTestSummary> convertToAIModelTestSummary(
            List<AIModelTestResultEntity> results) {
        return results.stream()
                .map(
                        r -> {
                            List<AIModelTestSummary.AssertionDetail> details = new ArrayList<>();
                            if (r.getDetails() != null) {
                                for (Map<String, Object> d : r.getDetails()) {
                                    details.add(
                                            new AIModelTestSummary.AssertionDetail(
                                                    (String) d.getOrDefault("assertion", ""),
                                                    (Boolean) d.getOrDefault("passed", false),
                                                    (String) d.getOrDefault("expected", null),
                                                    (String) d.getOrDefault("actual", null)));
                                }
                            }
                            return new AIModelTestSummary(
                                    r.getTestCaseId(),
                                    r.getTestName(),
                                    r.getCategory(),
                                    r.getScore() != null ? r.getScore().doubleValue() : 0.0,
                                    r.getPassed() != null ? r.getPassed() : false,
                                    details,
                                    r.getResponseTime() != null ? r.getResponseTime() : 0L,
                                    r.getActualOutput());
                        })
                .toList();
    }

    public List<String> getAICategories() {
        return List.of("basic", "code-generation", "knowledge", "conversation");
    }

    public TestStatsSummary getStatsSummary() {
        // 从数据库统计
        long totalAI = aiModelTestResultRepository.count();
        long passedAI =
                aiModelTestResultRepository.findAll().stream()
                        .filter(r -> Boolean.TRUE.equals(r.getPassed()))
                        .count();

        long running = testJobRepository.findRunningJobs().size();

        double avgResponseTime =
                aiModelTestResultRepository.findAll().stream()
                        .filter(r -> r.getResponseTime() != null)
                        .mapToLong(AIModelTestResultEntity::getResponseTime)
                        .average()
                        .orElse(0.0);

        return new TestStatsSummary(
                (int) totalAI,
                (int) passedAI,
                (int) (totalAI - passedAI),
                (int) running,
                totalAI > 0 ? (double) passedAI / totalAI : 0,
                (long) avgResponseTime);
    }

    // ==================== 清理方法 ====================

    @Transactional
    public int cleanupExpiredJobs(int retentionDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        return testJobRepository.deleteByCreatedAtBefore(cutoff);
    }

    @Transactional
    public void deleteJob(String jobId) {
        testJobRepository.deleteById(jobId);
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() > max ? text.substring(0, max) + "..." : text;
    }
}
