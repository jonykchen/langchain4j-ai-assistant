package com.jonychen.admin.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jonychen.admin.dto.CostStatistics;
import com.jonychen.admin.dto.DashboardMetrics;
import com.jonychen.admin.dto.PageResponse;
import com.jonychen.admin.dto.UserAdminVO;
import com.jonychen.admin.service.AdminStatisticsService;
import com.jonychen.admin.service.UserAdminService;
import com.jonychen.model.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 管理员 REST API
 *
 * @author jonychen
 */
@Tag(name = "管理员", description = "管理员后台相关接口")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final AdminStatisticsService statisticsService;
    private final UserAdminService userAdminService;

    // ==================== 仪表盘 ====================

    /** 获取仪表盘统计数据 */
    @Operation(summary = "仪表盘统计", description = "获取系统核心指标")
    @GetMapping("/dashboard/metrics")
    public ApiResponse<DashboardMetrics> getMetrics() {
        return ApiResponse.success(statisticsService.getDashboardMetrics());
    }

    /** 获取使用趋势 */
    @Operation(summary = "使用趋势", description = "获取指定天数的趋势数据")
    @GetMapping("/dashboard/trend")
    public ApiResponse<CostStatistics.TrendData> getTrend(
            @RequestParam(defaultValue = "7") int days) {
        return ApiResponse.success(statisticsService.getUsageTrend(days));
    }

    /** 获取模型分布 */
    @Operation(summary = "模型分布", description = "获取模型使用分布")
    @GetMapping("/dashboard/model-distribution")
    public ApiResponse<List<CostStatistics.ModelDistribution>> getModelDistribution() {
        return ApiResponse.success(statisticsService.getModelDistribution());
    }

    /** 获取系统资源 */
    @Operation(summary = "系统资源", description = "获取内存、线程等系统资源状态")
    @GetMapping("/dashboard/resources")
    public ApiResponse<AdminStatisticsService.SystemResourceInfo> getResources() {
        return ApiResponse.success(statisticsService.getSystemResources());
    }

    // ==================== 用户管理 ====================

    /** 获取用户列表 */
    @Operation(summary = "用户列表", description = "分页获取用户列表")
    @GetMapping("/users")
    public ApiResponse<PageResponse<UserAdminVO>> getUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String provider) {
        return ApiResponse.success(userAdminService.getUsers(page, size, search, role, provider));
    }

    /** 获取用户详情 */
    @Operation(summary = "用户详情", description = "获取指定用户的详细信息")
    @GetMapping("/users/{userId}")
    public ApiResponse<UserAdminVO> getUserDetail(@PathVariable String userId) {
        return ApiResponse.success(userAdminService.getUserDetail(userId));
    }

    /** 获取用户使用统计 */
    @Operation(summary = "用户使用统计", description = "获取用户的 Token 使用统计")
    @GetMapping("/users/{userId}/usage")
    public ApiResponse<UserAdminService.UserUsageStats> getUserUsageStats(
            @PathVariable String userId) {
        return ApiResponse.success(userAdminService.getUserUsageStats(userId));
    }

    /** 更新用户角色 */
    @Operation(summary = "更新角色", description = "更新用户角色（USER/ADMIN）")
    @PutMapping("/users/{userId}/role")
    public ApiResponse<Void> updateUserRole(
            @PathVariable String userId, @RequestBody Map<String, String> request) {
        userAdminService.updateUserRole(userId, request.get("role"));
        return ApiResponse.success(null);
    }

    /** 更新用户配额 */
    @Operation(summary = "更新配额", description = "更新用户的 Token 配额")
    @PutMapping("/users/{userId}/quota")
    public ApiResponse<Void> updateUserQuota(
            @PathVariable String userId, @RequestBody UserAdminService.QuotaUpdateRequest request) {
        userAdminService.updateUserQuota(userId, request);
        return ApiResponse.success(null);
    }

    /** 删除用户 */
    @Operation(summary = "删除用户", description = "删除指定用户")
    @DeleteMapping("/users/{userId}")
    public ApiResponse<Void> deleteUser(@PathVariable String userId) {
        userAdminService.deleteUser(userId);
        return ApiResponse.success(null);
    }

    // ==================== 成本监控 ====================

    /** 获取预算信息 */
    @Operation(summary = "预算信息", description = "获取日/月预算使用情况")
    @GetMapping("/cost/budget")
    public ApiResponse<DashboardMetrics.BudgetInfo> getBudget() {
        return ApiResponse.success(statisticsService.getDashboardMetrics().budget());
    }

    /** 获取模型成本统计 */
    @Operation(summary = "模型成本", description = "获取各模型的成本统计")
    @GetMapping("/cost/model-statistics")
    public ApiResponse<List<CostStatistics>> getModelCostStatistics() {
        return ApiResponse.success(statisticsService.getModelCostStatistics());
    }

    /** 获取用户消费排行 */
    @Operation(summary = "消费排行", description = "获取用户消费排行榜")
    @GetMapping("/cost/top-users")
    public ApiResponse<List<CostStatistics.UserCostRanking>> getTopUsers(
            @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.success(statisticsService.getTopUsers(limit));
    }

    /** 获取成本趋势 */
    @Operation(summary = "成本趋势", description = "获取成本趋势数据")
    @GetMapping("/cost/trend")
    public ApiResponse<CostStatistics.TrendData> getCostTrend(
            @RequestParam(defaultValue = "30") int days) {
        return ApiResponse.success(statisticsService.getUsageTrend(days));
    }
}
