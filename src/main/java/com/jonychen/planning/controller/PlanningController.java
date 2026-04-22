package com.jonychen.planning.controller;

import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jonychen.model.ApiResponse;
import com.jonychen.planning.AgentOrchestrator;
import com.jonychen.planning.Step;
import com.jonychen.planning.StepResult;
import com.jonychen.planning.Task;
import com.jonychen.planning.TaskContext;
import com.jonychen.planning.TaskExecutor;
import com.jonychen.planning.TaskResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 任务规划 REST API
 *
 * @author jonychen
 */
@Tag(name = "任务规划", description = "任务规划与执行相关接口")
@RestController
@RequestMapping("/api/planning")
@RequiredArgsConstructor
public class PlanningController {

    private final AgentOrchestrator agentOrchestrator;
    private final TaskExecutor taskExecutor;

    /** 执行任务（自动选择策略） */
    @Operation(summary = "执行任务", description = "根据任务类型自动选择执行策略")
    @PostMapping("/execute")
    public ApiResponse<TaskResultInfo> executeTask(@RequestBody TaskRequest request) {
        TaskContext context = TaskContext.create(request.sessionId());

        TaskResult result = agentOrchestrator.execute(request.goal(), context);

        return ApiResponse.success(toTaskResultInfo(result));
    }

    /** 使用 ReAct 模式执行 */
    @Operation(summary = "ReAct 执行", description = "使用推理-行动循环模式执行任务")
    @PostMapping("/react")
    public ApiResponse<TaskResultInfo> executeReAct(@RequestBody TaskRequest request) {
        TaskContext context = TaskContext.create(request.sessionId());

        TaskResult result = agentOrchestrator.executeReAct(request.question(), context);

        return ApiResponse.success(toTaskResultInfo(result));
    }

    /** 使用 Plan-Execute 模式执行 */
    @Operation(summary = "Plan-Execute 执行", description = "先规划后执行模式")
    @PostMapping("/plan-execute")
    public ApiResponse<TaskResultInfo> executePlanExecute(@RequestBody TaskRequest request) {
        TaskContext context = TaskContext.create(request.sessionId());

        TaskResult result = agentOrchestrator.executePlanExecute(request.goal(), context);

        return ApiResponse.success(toTaskResultInfo(result));
    }

    /** 执行预定义任务 */
    @Operation(summary = "执行预定义任务", description = "执行包含预定义步骤的任务")
    @PostMapping("/task")
    public ApiResponse<TaskResultInfo> executePredefinedTask(
            @RequestBody PredefinedTaskRequest request) {
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
        }

        task = task.withSteps(steps);

        TaskContext context = TaskContext.create(request.sessionId());
        TaskResult result = taskExecutor.executeTask(task);

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
