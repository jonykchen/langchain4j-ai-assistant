package com.jonychen.admin.controller;

import com.jonychen.admin.dto.*;
import com.jonychen.admin.service.TestExecutionService;
import com.jonychen.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 测试管理 REST API
 *
 * 提供测试执行、状态查询、结果获取等功能
 */
@Tag(name = "测试管理", description = "测试执行和结果查询接口")
@RestController
@RequestMapping("/api/admin/test")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class TestManagementController {

    private final TestExecutionService testExecutionService;

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
    public ApiResponse<Map<String, String>> runAIModelTests(
            @RequestBody AITestRequest request) {
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
    @Operation(summary = "取消测试任务", description = "取消正在运行的测试任务")
    public ApiResponse<Void> cancelJob(@PathVariable String jobId) {
        testExecutionService.cancelJob(jobId);
        return ApiResponse.success();
    }

    // ==================== 测试统计 ====================

    @GetMapping("/stats/summary")
    @Operation(summary = "获取测试统计摘要", description = "获取测试执行的统计摘要")
    public ApiResponse<TestStatsSummary> getStatsSummary() {
        return ApiResponse.success(testExecutionService.getStatsSummary());
    }

    /**
     * 性能测试请求
     */
    public record PerformanceTestRequest(String simulation) {}

    /**
     * AI 测试请求
     */
    public record AITestRequest(String category) {}
}
