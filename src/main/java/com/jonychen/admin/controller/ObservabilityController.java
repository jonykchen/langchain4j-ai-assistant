package com.jonychen.admin.controller;

import com.jonychen.admin.dto.AgentTraceVO;
import com.jonychen.admin.dto.EvaluationResultVO;
import com.jonychen.admin.dto.PageResponse;
import com.jonychen.admin.dto.PromptTemplateVO;
import com.jonychen.model.ApiResponse;
import com.jonychen.observability.evaluation.AgentEvaluator;
import com.jonychen.observability.evaluation.EvaluationService;
import com.jonychen.observability.prompt.PromptTemplateEntity;
import com.jonychen.observability.prompt.PromptTemplateRepository;
import com.jonychen.observability.prompt.PromptVersionService;
import com.jonychen.observability.state.AgentStateSnapshot;
import com.jonychen.observability.state.AgentStateService;
import com.jonychen.observability.trace.AgentTrace;
import com.jonychen.observability.trace.AgentTraceService;
import com.jonychen.observability.trace.AgentTraceSpan;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 可观测性管理 REST API
 *
 * @author jonychen
 */
@Tag(name = "可观测性", description = "Agent 可观测性相关接口")
@RestController
@RequestMapping("/api/admin/observability")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class ObservabilityController {

    private final AgentTraceService traceService;
    private final PromptVersionService promptService;
    private final PromptTemplateRepository promptRepository;
    private final EvaluationService evaluationService;
    private final AgentStateService stateService;

    // ==================== Agent 追踪 ====================

    @Operation(summary = "追踪列表", description = "获取 Agent 执行追踪列表")
    @GetMapping("/traces")
    public ApiResponse<List<AgentTraceVO>> getTraces(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String agentType,
            @RequestParam(defaultValue = "50") int limit) {

        List<AgentTrace> traces = traceService.queryTraces(userId, status, agentType, limit);
        List<AgentTraceVO> vos = traces.stream()
                .map(AgentTraceVO::from)
                .toList();
        return ApiResponse.success(vos);
    }

    @Operation(summary = "追踪详情", description = "获取指定追踪的详细信息")
    @GetMapping("/traces/{traceId}")
    public ApiResponse<AgentTraceVO> getTraceDetail(@PathVariable String traceId) {
        AgentTrace trace = traceService.getTrace(traceId);
        return ApiResponse.success(AgentTraceVO.from(trace));
    }

    @Operation(summary = "追踪 Span 列表", description = "获取追踪的执行步骤详情")
    @GetMapping("/traces/{traceId}/spans")
    public ApiResponse<List<AgentTraceSpan>> getTraceSpans(@PathVariable String traceId) {
        List<AgentTraceSpan> spans = traceService.getTraceSpans(traceId);
        return ApiResponse.success(spans);
    }

    @Operation(summary = "活跃追踪", description = "获取当前正在执行的追踪")
    @GetMapping("/traces/active")
    public ApiResponse<List<AgentTraceVO>> getActiveTraces(
            @RequestParam(required = false) String userId) {
        List<AgentTrace> traces = traceService.getActiveTraces(userId);
        List<AgentTraceVO> vos = traces.stream()
                .map(AgentTraceVO::from)
                .toList();
        return ApiResponse.success(vos);
    }

    @Operation(summary = "追踪统计", description = "获取追踪统计信息")
    @GetMapping("/traces/statistics")
    public ApiResponse<AgentTraceService.TraceStatistics> getTraceStatistics() {
        return ApiResponse.success(traceService.getStatistics());
    }

    // ==================== Prompt 管理 ====================

    @Operation(summary = "模板列表", description = "获取所有 Prompt 模板")
    @GetMapping("/prompts")
    public ApiResponse<List<PromptTemplateVO>> getPrompts() {
        List<PromptTemplateEntity> templates = promptRepository.findByActiveTrue();
        List<PromptTemplateVO> vos = templates.stream()
                .map(PromptTemplateVO::from)
                .toList();
        return ApiResponse.success(vos);
    }

    @Operation(summary = "模板名称列表", description = "获取所有模板名称")
    @GetMapping("/prompts/names")
    public ApiResponse<List<String>> getPromptNames() {
        return ApiResponse.success(promptService.getAllTemplateNames());
    }

    @Operation(summary = "版本历史", description = "获取指定模板的版本历史")
    @GetMapping("/prompts/{name}/versions")
    public ApiResponse<List<PromptTemplateVO>> getPromptVersions(@PathVariable String name) {
        List<PromptTemplateEntity> templates = promptService.getVersionHistory(name);
        List<PromptTemplateVO> vos = templates.stream()
                .map(PromptTemplateVO::from)
                .toList();
        return ApiResponse.success(vos);
    }

    @Operation(summary = "创建模板", description = "创建新的 Prompt 模板")
    @PostMapping("/prompts")
    public ApiResponse<PromptTemplateVO> createPrompt(@RequestBody CreatePromptRequest request) {
        PromptVersionService.CreatePromptRequest req = new PromptVersionService.CreatePromptRequest(
                request.name(),
                request.version(),
                request.description(),
                request.content(),
                request.tags(),
                request.createdBy()
        );
        PromptTemplateEntity template = promptService.createTemplate(req);
        return ApiResponse.success(PromptTemplateVO.from(template));
    }

    @Operation(summary = "创建新版本", description = "为现有模板创建新版本")
    @PostMapping("/prompts/{name}/versions")
    public ApiResponse<PromptTemplateVO> createVersion(
            @PathVariable String name,
            @RequestBody CreateVersionRequest request) {
        PromptTemplateEntity template = promptService.createVersion(
                name, request.content(), request.description());
        return ApiResponse.success(PromptTemplateVO.from(template));
    }

    @Operation(summary = "激活版本", description = "激活指定模板版本")
    @PostMapping("/prompts/{name}/versions/{version}/activate")
    public ApiResponse<Void> activateVersion(
            @PathVariable String name,
            @PathVariable String version) {
        promptService.activateVersion(name, version);
        return ApiResponse.success(null);
    }

    @Operation(summary = "推送生产", description = "将版本推送到生产环境")
    @PostMapping("/prompts/{name}/versions/{version}/promote")
    public ApiResponse<Void> promoteToProduction(
            @PathVariable String name,
            @PathVariable String version) {
        promptService.promoteToProduction(name, version);
        return ApiResponse.success(null);
    }

    @Operation(summary = "回滚版本", description = "回滚到指定版本")
    @PostMapping("/prompts/{name}/rollback/{version}")
    public ApiResponse<Void> rollbackVersion(
            @PathVariable String name,
            @PathVariable String version) {
        promptService.rollback(name, version);
        return ApiResponse.success(null);
    }

    @Operation(summary = "配置 A/B 测试", description = "为模板配置 A/B 测试")
    @PostMapping("/prompts/{name}/ab-test")
    public ApiResponse<Void> configureABTest(
            @PathVariable String name,
            @RequestBody ABTestConfigRequest request) {
        PromptVersionService.ABTestConfigRequest config = new PromptVersionService.ABTestConfigRequest(
                request.baselineVersion(),
                request.variantVersion(),
                request.variantName(),
                request.trafficPercentage()
        );
        promptService.configureABTest(name, config);
        return ApiResponse.success(null);
    }

    @Operation(summary = "停止 A/B 测试", description = "停止模板的 A/B 测试")
    @PostMapping("/prompts/{name}/ab-test/stop")
    public ApiResponse<Void> stopABTest(@PathVariable String name) {
        promptService.stopABTest(name);
        return ApiResponse.success(null);
    }

    // ==================== Agent 评测 ====================

    @Operation(summary = "评测追踪", description = "对指定追踪执行评测")
    @PostMapping("/evaluation/evaluate/{traceId}")
    public ApiResponse<EvaluationResultVO> evaluateTrace(@PathVariable String traceId) {
        EvaluationService.FullEvaluationResult result = evaluationService.evaluateFull(
                traceId,
                new EvaluationService.EvaluationRequest(null, null, Map.of(), "single")
        );

        EvaluationResultVO vo = convertToVO(result);
        return ApiResponse.success(vo);
    }

    @Operation(summary = "批量评测", description = "批量评测多个追踪")
    @PostMapping("/evaluation/batch")
    public ApiResponse<List<EvaluationResultVO>> evaluateBatch(
            @RequestBody List<String> traceIds) {
        List<EvaluationService.FullEvaluationResult> results = evaluationService.evaluateBatch(traceIds);
        List<EvaluationResultVO> vos = results.stream()
                .map(this::convertToVO)
                .toList();
        return ApiResponse.success(vos);
    }

    @Operation(summary = "生成评测报告", description = "生成评测报告")
    @PostMapping("/evaluation/report")
    public ApiResponse<EvaluationResultVO.ReportVO> generateReport(
            @RequestBody List<String> traceIds) {
        EvaluationService.EvaluationReport report = evaluationService.generateReport(traceIds);

        EvaluationResultVO.ReportVO vo = EvaluationResultVO.ReportVO.builder()
                .reportId(report.reportId())
                .generatedAt(report.generatedAt())
                .totalTraces(report.totalTraces())
                .avgOverallScore((Double) report.summary().getOrDefault("avgOverallScore", 0.0))
                .passRate((Double) report.summary().getOrDefault("passRate", 0.0))
                .results(report.results().stream().map(this::convertToVO).toList())
                .build();

        return ApiResponse.success(vo);
    }

    @Operation(summary = "评测器列表", description = "获取所有可用的评测器")
    @GetMapping("/evaluation/evaluators")
    public ApiResponse<List<EvaluationService.EvaluatorInfo>> getEvaluators() {
        return ApiResponse.success(evaluationService.getAvailableEvaluators());
    }

    // ==================== 状态快照 ====================

    @Operation(summary = "会话快照列表", description = "获取会话的所有快照")
    @GetMapping("/snapshots/session/{sessionId}")
    public ApiResponse<List<AgentStateSnapshot>> getSessionSnapshots(@PathVariable String sessionId) {
        return ApiResponse.success(stateService.getSessionSnapshots(sessionId));
    }

    @Operation(summary = "可恢复快照", description = "获取会话的最新可恢复快照")
    @GetMapping("/snapshots/session/{sessionId}/resumable")
    public ApiResponse<Optional<AgentStateSnapshot>> getResumableSnapshot(@PathVariable String sessionId) {
        return ApiResponse.success(stateService.getLatestResumableSnapshot(sessionId));
    }

    @Operation(summary = "快照统计", description = "获取快照统计信息")
    @GetMapping("/snapshots/statistics")
    public ApiResponse<AgentStateService.SnapshotStatistics> getSnapshotStatistics() {
        return ApiResponse.success(stateService.getStatistics());
    }

    @Operation(summary = "清理过期快照", description = "清理过期的快照")
    @PostMapping("/snapshots/cleanup")
    public ApiResponse<Integer> cleanupExpiredSnapshots() {
        int count = stateService.cleanupExpiredSnapshots();
        return ApiResponse.success(count);
    }

    // ==================== 私有方法 ====================

    private EvaluationResultVO convertToVO(EvaluationService.FullEvaluationResult result) {
        List<EvaluationResultVO.EvaluatorResultVO> evaluatorResults = result.evaluatorResults().stream()
                .map(r -> EvaluationResultVO.EvaluatorResultVO.builder()
                        .evaluatorId(r.evaluatorId())
                        .passed(r.passed())
                        .score(r.score())
                        .details(r.details())
                        .issues(r.issues())
                        .recommendation(r.recommendation())
                        .build())
                .toList();

        EvaluationResultVO.MetricsVO metricsVO = EvaluationResultVO.MetricsVO.builder()
                .taskCompletionRate(result.metrics().getTaskCompletionRate())
                .avgIterations(result.metrics().getAvgIterations())
                .toolSuccessRate(result.metrics().getToolSuccessRate())
                .avgResponseTimeMs(result.metrics().getAvgResponseTimeMs())
                .qualityScore(result.metrics().getQualityScore())
                .errorRate(result.metrics().getErrorRate())
                .build();

        return EvaluationResultVO.builder()
                .traceId(result.traceId())
                .overallScore(result.overallScore())
                .recommendation(result.recommendation())
                .evaluatorResults(evaluatorResults)
                .metrics(metricsVO)
                .build();
    }

    // DTOs for request
    public record CreatePromptRequest(String name, String version, String description,
                                       String content, String tags, String createdBy) {}

    public record CreateVersionRequest(String content, String description) {}

    public record ABTestConfigRequest(String baselineVersion, String variantVersion,
                                       String variantName, double trafficPercentage) {}
}
