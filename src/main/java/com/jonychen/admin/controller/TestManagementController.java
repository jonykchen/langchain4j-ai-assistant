package com.jonychen.admin.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jonychen.admin.dto.AIModelTestSummary;
import com.jonychen.admin.dto.PageResponse;
import com.jonychen.admin.dto.PerformanceResultSummary;
import com.jonychen.admin.dto.TestComparisonResult;
import com.jonychen.admin.dto.TestJobStatus;
import com.jonychen.admin.dto.TestResultSummary;
import com.jonychen.admin.dto.TestStatsSummary;
import com.jonychen.admin.service.TestCleanupService;
import com.jonychen.admin.service.TestComparisonService;
import com.jonychen.admin.service.TestExecutionService;
import com.jonychen.admin.service.TestExportService;
import com.jonychen.model.ApiResponse;
import com.jonychen.test.entity.AIModelTestResultEntity;
import com.jonychen.test.entity.E2ETestResult;
import com.jonychen.test.entity.PerformanceTestResult;
import com.jonychen.test.entity.TestJob;
import com.jonychen.test.repository.TestJobRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 测试管理 REST API
 *
 * <p>提供测试执行、状态查询、结果获取、历史查询、导出、对比等功能
 */
@Tag(name = "测试管理", description = "测试执行和结果查询接口")
@RestController
@RequestMapping("/api/admin/test")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class TestManagementController {

    private final TestExecutionService testExecutionService;
    private final TestExportService testExportService;
    private final TestCleanupService testCleanupService;
    private final TestComparisonService testComparisonService;
    private final TestJobRepository testJobRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // ==================== E2E 测试 ====================

    @PostMapping("/e2e/run")
    @Operation(summary = "运行 E2E 测试", description = "启动 Playwright E2E 测试")
    public ApiResponse<Map<String, String>> runE2ETests() {
        String jobId = testExecutionService.runE2ETests();
        return ApiResponse.success(Map.of("jobId", jobId));
    }

    @GetMapping("/e2e/status")
    @Operation(summary = "获取 E2E 测试状态", description = "获取 E2E 测试执行状态")
    public ApiResponse<List<TestResultSummary>> getE2EStatus() {
        return ApiResponse.success(testExecutionService.getE2EStatus());
    }

    @GetMapping("/e2e/report")
    @Operation(summary = "获取 E2E 测试报告", description = "获取 HTML 测试报告路径")
    public ApiResponse<String> getE2EReport() {
        return ApiResponse.success(testExecutionService.getE2EReport());
    }

    // ==================== 性能测试 ====================

    @PostMapping("/performance/run")
    @Operation(summary = "运行性能测试", description = "启动 Gatling 性能测试")
    public ApiResponse<Map<String, String>> runPerformanceTest(
            @RequestBody PerformanceTestRequest request) {
        String jobId = testExecutionService.runPerformanceTest(request.simulation());
        return ApiResponse.success(Map.of("jobId", jobId));
    }

    @GetMapping("/performance/results")
    @Operation(summary = "获取性能测试结果", description = "获取性能测试结果列表")
    public ApiResponse<List<PerformanceResultSummary>> getPerformanceResults() {
        return ApiResponse.success(testExecutionService.getPerformanceResults());
    }

    @GetMapping("/performance/simulations")
    @Operation(summary = "获取可用模拟场景", description = "获取所有可用的 Gatling 模拟场景")
    public ApiResponse<List<String>> getAvailableSimulations() {
        return ApiResponse.success(testExecutionService.getAvailableSimulations());
    }

    // ==================== AI 模型测试 ====================

    @PostMapping("/ai/run")
    @Operation(summary = "运行 AI 模型测试", description = "启动 AI 模型测试")
    public ApiResponse<Map<String, String>> runAIModelTests(@RequestBody AITestRequest request) {
        String jobId = testExecutionService.runAIModelTests(request.category());
        return ApiResponse.success(Map.of("jobId", jobId));
    }

    @GetMapping("/ai/results")
    @Operation(summary = "获取 AI 模型测试结果", description = "获取 AI 模型测试结果列表")
    public ApiResponse<List<AIModelTestSummary>> getAIModelResults() {
        return ApiResponse.success(testExecutionService.getAIModelResults());
    }

    @GetMapping("/ai/categories")
    @Operation(summary = "获取 AI 测试分类", description = "获取所有 AI 测试分类")
    public ApiResponse<List<String>> getAICategories() {
        return ApiResponse.success(testExecutionService.getAICategories());
    }

    // ==================== 任务管理 ====================

    @GetMapping("/job/{jobId}/status")
    @Operation(summary = "获取测试任务状态", description = "根据任务 ID 获取状态")
    public ApiResponse<TestJobStatus> getJobStatus(@PathVariable String jobId) {
        return ApiResponse.success(testExecutionService.getJobStatus(jobId));
    }

    @DeleteMapping("/job/{jobId}")
    @Operation(summary = "取消/删除测试任务", description = "取消正在运行的测试任务或删除历史记录")
    public ApiResponse<Void> cancelOrDeleteJob(@PathVariable String jobId) {
        testExecutionService.cancelJob(jobId);
        testExecutionService.deleteJob(jobId);
        return ApiResponse.success();
    }

    // ==================== 测试统计 ====================

    @GetMapping("/stats/summary")
    @Operation(summary = "获取测试统计摘要", description = "获取测试执行的统计摘要")
    public ApiResponse<TestStatsSummary> getStatsSummary() {
        return ApiResponse.success(testExecutionService.getStatsSummary());
    }

    // ==================== 历史查询 ====================

    @GetMapping("/jobs")
    @Operation(summary = "获取测试任务历史列表", description = "分页查询测试任务历史，支持按类型、状态、时间过滤")
    public ApiResponse<PageResponse<TestJob>> getTestJobHistory(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startTime"));
        LocalDateTime fromDate = from != null ? LocalDateTime.parse(from, DATE_FORMATTER) : null;
        LocalDateTime toDate = to != null ? LocalDateTime.parse(to, DATE_FORMATTER) : null;
        Page<TestJob> result =
                testExecutionService.getTestJobHistory(type, status, fromDate, toDate, pageable);
        PageResponse<TestJob> response =
                PageResponse.of(
                        result.getContent(),
                        result.getTotalElements(),
                        result.getNumber(),
                        result.getSize());
        return ApiResponse.success(response);
    }

    @GetMapping("/jobs/{jobId}")
    @Operation(summary = "获取单个任务详情", description = "获取测试任务的详细信息")
    public ApiResponse<TestJob> getTestJobDetail(@PathVariable String jobId) {
        return testJobRepository
                .findById(jobId)
                .map(ApiResponse::success)
                .orElse(ApiResponse.error(404, "Job not found"));
    }

    @GetMapping("/jobs/{jobId}/results")
    @Operation(summary = "获取任务的所有结果", description = "获取指定任务的所有测试结果")
    public ApiResponse<Map<String, Object>> getTestJobResults(@PathVariable String jobId) {
        // 从数据库加载结果
        List<E2ETestResult> e2e = testExecutionService.getE2EResultsByJobId(jobId);
        List<PerformanceTestResult> perf = testExecutionService.getPerformanceResultsByJobId(jobId);
        List<AIModelTestResultEntity> ai =
                testExecutionService.getAIModelResultsByJobId(jobId, null);
        Map<String, Object> results =
                Map.of(
                        "e2e", e2e,
                        "performance", perf,
                        "aiModel", ai);
        return ApiResponse.success(results);
    }

    @GetMapping("/results/e2e")
    @Operation(summary = "查询 E2E 结果历史", description = "分页查询 E2E 测试结果历史")
    public ApiResponse<List<E2ETestResult>> getE2EHistory(
            @RequestParam(required = false) String jobId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (jobId != null) {
            return ApiResponse.success(testExecutionService.getE2EResultsByJobId(jobId));
        }
        return ApiResponse.success(List.of());
    }

    @GetMapping("/results/performance")
    @Operation(summary = "查询性能结果历史", description = "分页查询性能测试结果历史")
    public ApiResponse<List<PerformanceTestResult>> getPerformanceHistory(
            @RequestParam(required = false) String jobId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (jobId != null) {
            return ApiResponse.success(testExecutionService.getPerformanceResultsByJobId(jobId));
        }
        return ApiResponse.success(List.of());
    }

    @GetMapping("/results/ai")
    @Operation(summary = "查询 AI 模型结果历史", description = "分页查询 AI 模型测试结果历史")
    public ApiResponse<List<AIModelTestResultEntity>> getAIModelHistory(
            @RequestParam(required = false) String jobId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean passed,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (jobId != null) {
            return ApiResponse.success(
                    testExecutionService.getAIModelResultsByJobId(jobId, category));
        }
        return ApiResponse.success(List.of());
    }

    // ==================== 导出功能 ====================

    @GetMapping("/export")
    @Operation(summary = "导出测试结果", description = "导出测试结果为 JSON/CSV/Excel 格式")
    public ResponseEntity<byte[]> exportTestResults(
            @RequestParam(defaultValue = "ALL") String type,
            @RequestParam(defaultValue = "json") String format,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDateTime fromDate = from != null ? LocalDateTime.parse(from, DATE_FORMATTER) : null;
        LocalDateTime toDate = to != null ? LocalDateTime.parse(to, DATE_FORMATTER) : null;

        byte[] content =
                testExportService.exportResults(
                        type.toUpperCase(), format.toLowerCase(), fromDate, toDate);
        String filename = testExportService.getExportFileName(type, format);
        String contentType = testExportService.getExportContentType(format);

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(content);
    }

    // ==================== 对比功能 ====================

    @PostMapping("/compare")
    @Operation(summary = "对比测试结果", description = "对比两次同类型测试执行的结果差异")
    public ApiResponse<TestComparisonResult> compareTestResults(
            @RequestBody CompareRequest request) {
        return ApiResponse.success(testComparisonService.compare(request.jobId1, request.jobId2));
    }

    // ==================== 清理功能 ====================

    @DeleteMapping("/cleanup")
    @Operation(summary = "清理过期记录", description = "清理指定天数之前的测试记录")
    public ApiResponse<Map<String, Integer>> cleanupTestJobs(
            @RequestParam(defaultValue = "90") int retentionDays) {
        int deleted = testCleanupService.cleanupExpiredJobs(retentionDays);
        return ApiResponse.success(Map.of("deleted", deleted));
    }

    /** 性能测试请求 */
    public record PerformanceTestRequest(String simulation) {}

    /** AI 测试请求 */
    public record AITestRequest(String category) {}

    /** 对比请求 */
    public record CompareRequest(String jobId1, String jobId2) {}
}
