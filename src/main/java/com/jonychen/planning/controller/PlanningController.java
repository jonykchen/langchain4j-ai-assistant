package com.jonychen.planning.controller;

import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jonychen.model.ApiResponse;
import com.jonychen.planning.PlanningOrchestrator;
import com.jonychen.planning.Step;
import com.jonychen.planning.StepResult;
import com.jonychen.planning.Task;
import com.jonychen.planning.TaskContext;
import com.jonychen.planning.TaskExecutor;
import com.jonychen.planning.TaskResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 任务规划 REST API
 *
 * @author jonychen
 */
@Slf4j
@Tag(name = "任务规划", description = "任务规划与执行相关接口")
@RestController
@RequestMapping("/api/planning")
@RequiredArgsConstructor
public class PlanningController {

    private final PlanningOrchestrator planningOrchestrator;
    private final TaskExecutor taskExecutor;

    private static final String LOG_PREFIX = "════════════════════════════════════════";

    /** 执行任务（自动选择策略） */
    @Operation(summary = "执行任务", description = "根据任务类型自动选择执行策略")
    @PostMapping("/execute")
    public ApiResponse<TaskResultInfo> executeTask(@RequestBody TaskRequest request) {
        log.info("{} [Planning] API入口: POST /api/planning/execute {}", LOG_PREFIX, LOG_PREFIX);
        log.info(
                "[Planning] 策略: AUTO(自动选择) | 目标: {} | SessionId: {}",
                request.goal(),
                request.sessionId());

        long startTime = System.currentTimeMillis();
        TaskContext context = TaskContext.create(request.sessionId());

        log.info("[Planning] 调用 AgentOrchestrator.execute()...");
        TaskResult result = planningOrchestrator.execute(request.goal(), context);

        log.info(
                "[Planning] 执行完成 | 耗时: {}ms | 成功: {} | 步骤数: {}",
                System.currentTimeMillis() - startTime,
                result.success(),
                result.stepResults().size());
        log.info("{} [Planning] API出口 {}", LOG_PREFIX, LOG_PREFIX);

        return ApiResponse.success(toTaskResultInfo(result));
    }

    /** 使用 ReAct 模式执行 */
    @Operation(summary = "ReAct 执行", description = "使用推理-行动循环模式执行任务")
    @PostMapping("/react")
    public ApiResponse<TaskResultInfo> executeReAct(@RequestBody TaskRequest request) {
        log.info("{} [Planning] API入口: POST /api/planning/react {}", LOG_PREFIX, LOG_PREFIX);
        log.info(
                "[Planning] 策略: REACT | 问题: {} | SessionId: {}",
                request.question(),
                request.sessionId());

        long startTime = System.currentTimeMillis();
        TaskContext context = TaskContext.create(request.sessionId());

        log.info("[Planning] 调用 AgentOrchestrator.executeReAct()...");
        TaskResult result = planningOrchestrator.executeReAct(request.question(), context);

        log.info(
                "[Planning] ReAct完成 | 耗时: {}ms | 迭代次数: {} | 成功: {}",
                System.currentTimeMillis() - startTime,
                result.iterations(),
                result.success());
        log.info("{} [Planning] API出口 {}", LOG_PREFIX, LOG_PREFIX);

        return ApiResponse.success(toTaskResultInfo(result));
    }

    /** 使用 Plan-Execute 模式执行 */
    @Operation(summary = "Plan-Execute 执行", description = "先规划后执行模式")
    @PostMapping("/plan-execute")
    public ApiResponse<TaskResultInfo> executePlanExecute(@RequestBody TaskRequest request) {
        log.info("{} [Planning] API入口: POST /api/planning/plan-execute {}", LOG_PREFIX, LOG_PREFIX);
        log.info(
                "[Planning] 策略: PLAN-EXECUTE | 目标: {} | SessionId: {}",
                request.goal(),
                request.sessionId());

        long startTime = System.currentTimeMillis();
        TaskContext context = TaskContext.create(request.sessionId());

        log.info("[Planning] 调用 AgentOrchestrator.executePlanExecute()...");
        TaskResult result = planningOrchestrator.executePlanExecute(request.goal(), context);

        log.info(
                "[Planning] Plan-Execute完成 | 耗时: {}ms | 步骤数: {} | 成功: {}",
                System.currentTimeMillis() - startTime,
                result.stepResults().size(),
                result.success());
        log.info("{} [Planning] API出口 {}", LOG_PREFIX, LOG_PREFIX);

        return ApiResponse.success(toTaskResultInfo(result));
    }

    /** 执行预定义任务 */
    @Operation(summary = "执行预定义任务", description = "执行包含预定义步骤的任务")
    @PostMapping("/task")
    public ApiResponse<TaskResultInfo> executePredefinedTask(
            @RequestBody PredefinedTaskRequest request) {
        log.info("{} [Planning] API入口: POST /api/planning/task {}", LOG_PREFIX, LOG_PREFIX);
        log.info(
                "[Planning] 策略: PREDEFINED | 目标: {} | 步骤数: {} | SessionId: {}",
                request.goal(),
                request.steps().size(),
                request.sessionId());

        long startTime = System.currentTimeMillis();

        Task task = Task.create(request.goal());

        List<Step> steps = new java.util.ArrayList<>();
        for (int i = 0; i < request.steps().size(); i++) {
            StepDef stepDef = request.steps().get(i);
            Step step =
                    stepDef.tool() != null
                            ? Step.createWithTool(
                                    i + 1, stepDef.description(), stepDef.tool(), stepDef.params())
                            : Step.create(i + 1, stepDef.description(), stepDef.action());
            steps.add(step);
            log.info(
                    "[Planning] 步骤{}: {} | 工具: {}",
                    i + 1,
                    stepDef.description(),
                    stepDef.tool() != null ? stepDef.tool() : "LLM");
        }

        task = task.withSteps(steps);

        TaskContext context = TaskContext.create(request.sessionId());
        log.info("[Planning] 调用 TaskExecutor.executeTask()...");
        TaskResult result = taskExecutor.executeTask(task);

        log.info(
                "[Planning] 预定义任务完成 | 耗时: {}ms | 成功: {}",
                System.currentTimeMillis() - startTime,
                result.success());
        log.info("{} [Planning] API出口 {}", LOG_PREFIX, LOG_PREFIX);

        return ApiResponse.success(toTaskResultInfo(result));
    }

    private TaskResultInfo toTaskResultInfo(TaskResult result) {
        return new TaskResultInfo(
                result.taskId(),
                result.success(),
                result.finalOutput() != null ? result.finalOutput().toString() : null,
                result.stepResults().stream().map(this::toStepResultInfo).toList(),
                result.error(),
                result.totalExecutionTimeMs(),
                result.iterations());
    }

    private StepResultInfo toStepResultInfo(StepResult result) {
        return new StepResultInfo(
                result.success(),
                result.output() != null ? result.output().toString() : null,
                result.error(),
                result.observation());
    }

    // ========== DTO 类 ==========

    public record TaskRequest(String goal, String question, String sessionId) {}

    public record PredefinedTaskRequest(String goal, String sessionId, List<StepDef> steps) {}

    public record StepDef(
            String description, String action, String tool, java.util.Map<String, Object> params) {}

    public record TaskResultInfo(
            String taskId,
            boolean success,
            String output,
            List<StepResultInfo> stepResults,
            String error,
            long executionTimeMs,
            int iterations) {}

    public record StepResultInfo(
            boolean success, String output, String error, String observation) {}
}
