package com.jonychen.admin.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.jonychen.admin.dto.TestComparisonResult;
import com.jonychen.admin.dto.TestComparisonResult.ComparisonSummary;
import com.jonychen.admin.dto.TestComparisonResult.ResultDiff;
import com.jonychen.admin.dto.TestComparisonResult.TestJobInfo;
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
 * 测试结果对比服务
 *
 * <p>对比两次同类型测试执行的结果差异
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestComparisonService {

    private final TestJobRepository testJobRepository;
    private final E2ETestResultRepository e2eTestResultRepository;
    private final PerformanceTestResultRepository performanceTestResultRepository;
    private final AIModelTestResultRepository aiModelTestResultRepository;

    /** 对比两次测试执行结果 */
    public TestComparisonResult compare(String jobId1, String jobId2) {
        TestJob job1 =
                testJobRepository
                        .findById(jobId1)
                        .orElseThrow(
                                () -> new IllegalArgumentException("Job not found: " + jobId1));
        TestJob job2 =
                testJobRepository
                        .findById(jobId2)
                        .orElseThrow(
                                () -> new IllegalArgumentException("Job not found: " + jobId2));

        // 校验类型一致
        if (!Objects.equals(job1.getTestType(), job2.getTestType())) {
            throw new IllegalArgumentException(
                    "Cannot compare jobs of different types: "
                            + job1.getTestType()
                            + " vs "
                            + job2.getTestType());
        }

        TestJobInfo jobInfo1 =
                new TestJobInfo(
                        job1.getId(), job1.getTestType(), job1.getStartTime(), job1.getStatus());
        TestJobInfo jobInfo2 =
                new TestJobInfo(
                        job2.getId(), job2.getTestType(), job2.getStartTime(), job2.getStatus());

        return switch (job1.getTestType()) {
            case "E2E" -> compareE2E(jobInfo1, jobInfo2, jobId1, jobId2);
            case "PERFORMANCE" -> comparePerformance(jobInfo1, jobInfo2, jobId1, jobId2);
            case "AI_MODEL" -> compareAIModel(jobInfo1, jobInfo2, jobId1, jobId2);
            default ->
                    throw new IllegalArgumentException(
                            "Unsupported test type: " + job1.getTestType());
        };
    }

    private TestComparisonResult compareE2E(
            TestJobInfo jobInfo1, TestJobInfo jobInfo2, String jobId1, String jobId2) {
        List<E2ETestResult> results1 = e2eTestResultRepository.findByJobId(jobId1);
        List<E2ETestResult> results2 = e2eTestResultRepository.findByJobId(jobId2);

        // 按 testName 构建映射
        Map<String, E2ETestResult> map2 = new HashMap<>();
        results2.forEach(r -> map2.put(r.getTestName(), r));

        List<ResultDiff> diffs = new ArrayList<>();
        int improved = 0, degraded = 0, unchanged = 0;

        for (E2ETestResult r1 : results1) {
            E2ETestResult r2 = map2.get(r1.getTestName());
            if (r2 == null) continue;

            // 对比状态
            String direction = compareStatus(r1.getStatus(), r2.getStatus());
            diffs.add(
                    new ResultDiff(
                            r1.getTestName(),
                            "status",
                            r1.getStatus(),
                            r2.getStatus(),
                            !Objects.equals(r1.getStatus(), r2.getStatus()),
                            direction));
            if ("improved".equals(direction)) improved++;
            else if ("degraded".equals(direction)) degraded++;
            else if (!Objects.equals(r1.getStatus(), r2.getStatus())) unchanged++;

            // 对比耗时
            long d1 = r1.getDurationMs() != null ? r1.getDurationMs() : 0L;
            long d2 = r2.getDurationMs() != null ? r2.getDurationMs() : 0L;
            String durDir = d2 < d1 ? "improved" : d2 > d1 ? "degraded" : "unchanged";
            diffs.add(new ResultDiff(r1.getTestName(), "durationMs", d1, d2, d1 != d2, durDir));

            // 对比断言
            int pass1 = r1.getAssertionsPassed() != null ? r1.getAssertionsPassed() : 0;
            int pass2 = r2.getAssertionsPassed() != null ? r2.getAssertionsPassed() : 0;
            diffs.add(
                    new ResultDiff(
                            r1.getTestName(),
                            "assertionsPassed",
                            pass1,
                            pass2,
                            pass1 != pass2,
                            pass2 > pass1 ? "improved" : pass2 < pass1 ? "degraded" : "unchanged"));
        }

        ComparisonSummary summary =
                new ComparisonSummary(
                        (int)
                                results1.stream()
                                        .filter(r -> map2.containsKey(r.getTestName()))
                                        .count(),
                        improved,
                        degraded,
                        unchanged);

        return new TestComparisonResult(jobInfo1, jobInfo2, "E2E", diffs, summary);
    }

    private TestComparisonResult comparePerformance(
            TestJobInfo jobInfo1, TestJobInfo jobInfo2, String jobId1, String jobId2) {
        List<PerformanceTestResult> results1 = performanceTestResultRepository.findByJobId(jobId1);
        List<PerformanceTestResult> results2 = performanceTestResultRepository.findByJobId(jobId2);

        Map<String, PerformanceTestResult> map2 = new HashMap<>();
        results2.forEach(r -> map2.put(r.getSimulation(), r));

        List<ResultDiff> diffs = new ArrayList<>();
        int improved = 0, degraded = 0, unchanged = 0;

        for (PerformanceTestResult r1 : results1) {
            PerformanceTestResult r2 = map2.get(r1.getSimulation());
            if (r2 == null) continue;

            // 成功率对比
            double sr1 = r1.getSuccessRate() != null ? r1.getSuccessRate().doubleValue() : 0;
            double sr2 = r2.getSuccessRate() != null ? r2.getSuccessRate().doubleValue() : 0;
            String srDir = sr2 > sr1 ? "improved" : sr2 < sr1 ? "degraded" : "unchanged";
            diffs.add(
                    new ResultDiff(r1.getSimulation(), "successRate", sr1, sr2, sr1 != sr2, srDir));
            if ("improved".equals(srDir)) improved++;
            else if ("degraded".equals(srDir)) degraded++;

            // 平均响应时间对比
            long avg1 = r1.getAvgResponseTime() != null ? r1.getAvgResponseTime() : 0L;
            long avg2 = r2.getAvgResponseTime() != null ? r2.getAvgResponseTime() : 0L;
            String avgDir = avg2 < avg1 ? "improved" : avg2 > avg1 ? "degraded" : "unchanged";
            diffs.add(
                    new ResultDiff(
                            r1.getSimulation(),
                            "avgResponseTime",
                            avg1,
                            avg2,
                            avg1 != avg2,
                            avgDir));

            // P95 对比
            long p95_1 = r1.getP95ResponseTime() != null ? r1.getP95ResponseTime() : 0L;
            long p95_2 = r2.getP95ResponseTime() != null ? r2.getP95ResponseTime() : 0L;
            String p95Dir = p95_2 < p95_1 ? "improved" : p95_2 > p95_1 ? "degraded" : "unchanged";
            diffs.add(
                    new ResultDiff(
                            r1.getSimulation(),
                            "p95ResponseTime",
                            p95_1,
                            p95_2,
                            p95_1 != p95_2,
                            p95Dir));

            // P99 对比
            long p99_1 = r1.getP99ResponseTime() != null ? r1.getP99ResponseTime() : 0L;
            long p99_2 = r2.getP99ResponseTime() != null ? r2.getP99ResponseTime() : 0L;
            String p99Dir = p99_2 < p99_1 ? "improved" : p99_2 > p99_1 ? "degraded" : "unchanged";
            diffs.add(
                    new ResultDiff(
                            r1.getSimulation(),
                            "p99ResponseTime",
                            p99_1,
                            p99_2,
                            p99_1 != p99_2,
                            p99Dir));
        }

        ComparisonSummary summary =
                new ComparisonSummary(
                        (int)
                                results1.stream()
                                        .filter(r -> map2.containsKey(r.getSimulation()))
                                        .count(),
                        improved,
                        degraded,
                        unchanged);

        return new TestComparisonResult(jobInfo1, jobInfo2, "PERFORMANCE", diffs, summary);
    }

    private TestComparisonResult compareAIModel(
            TestJobInfo jobInfo1, TestJobInfo jobInfo2, String jobId1, String jobId2) {
        List<AIModelTestResultEntity> results1 = aiModelTestResultRepository.findByJobId(jobId1);
        List<AIModelTestResultEntity> results2 = aiModelTestResultRepository.findByJobId(jobId2);

        Map<String, AIModelTestResultEntity> map2 = new HashMap<>();
        results2.forEach(r -> map2.put(r.getTestCaseId(), r));

        List<ResultDiff> diffs = new ArrayList<>();
        int improved = 0, degraded = 0, unchanged = 0;

        for (AIModelTestResultEntity r1 : results1) {
            AIModelTestResultEntity r2 = map2.get(r1.getTestCaseId());
            if (r2 == null) continue;

            // 得分对比
            double s1 = r1.getScore() != null ? r1.getScore().doubleValue() : 0;
            double s2 = r2.getScore() != null ? r2.getScore().doubleValue() : 0;
            String sDir = s2 > s1 ? "improved" : s2 < s1 ? "degraded" : "unchanged";
            diffs.add(new ResultDiff(r1.getTestName(), "score", s1, s2, s1 != s2, sDir));
            if ("improved".equals(sDir)) improved++;
            else if ("degraded".equals(sDir)) degraded++;
            else unchanged++;

            // 通过状态对比
            boolean p1 = Boolean.TRUE.equals(r1.getPassed());
            boolean p2 = Boolean.TRUE.equals(r2.getPassed());
            String pDir = comparePassed(p1, p2);
            diffs.add(new ResultDiff(r1.getTestName(), "passed", p1, p2, p1 != p2, pDir));

            // 响应时间对比
            long rt1 = r1.getResponseTime() != null ? r1.getResponseTime() : 0L;
            long rt2 = r2.getResponseTime() != null ? r2.getResponseTime() : 0L;
            String rtDir = rt2 < rt1 ? "improved" : rt2 > rt1 ? "degraded" : "unchanged";
            diffs.add(
                    new ResultDiff(r1.getTestName(), "responseTime", rt1, rt2, rt1 != rt2, rtDir));
        }

        ComparisonSummary summary =
                new ComparisonSummary(
                        (int)
                                results1.stream()
                                        .filter(r -> map2.containsKey(r.getTestCaseId()))
                                        .count(),
                        improved,
                        degraded,
                        unchanged);

        return new TestComparisonResult(jobInfo1, jobInfo2, "AI_MODEL", diffs, summary);
    }

    private String compareStatus(String status1, String status2) {
        if (Objects.equals(status1, status2)) return "unchanged";
        if ("passed".equals(status2) && !"passed".equals(status1)) return "improved";
        if (!"passed".equals(status2) && "passed".equals(status1)) return "degraded";
        return "unchanged";
    }

    private String comparePassed(boolean passed1, boolean passed2) {
        if (passed1 == passed2) return "unchanged";
        if (passed2 && !passed1) return "improved";
        return "degraded";
    }
}
