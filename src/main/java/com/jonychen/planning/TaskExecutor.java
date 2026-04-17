package com.jonychen.planning;

import com.jonychen.tool.ToolRegistry;
import com.jonychen.tool.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 任务执行器
 *
 * @author jonychen
 */
@Slf4j
@Service("planningTaskExecutor")
@RequiredArgsConstructor
public class TaskExecutor {

    private final ToolRegistry toolRegistry;

    /**
     * 执行单个步骤
     *
     * @param step    步骤定义
     * @param context 任务上下文
     * @return 步骤结果
     */
    public StepResult executeStep(Step step, TaskContext context) {
        log.info("Executing step {}/{}: {}", step.order(), step.description());

        long startTime = System.currentTimeMillis();

        try {
            // 检查是否有工具调用
            if (step.tool() != null && !step.tool().isBlank()) {
                return executeToolStep(step, context);
            }

            // 如果没有工具，返回描述作为结果
            return StepResult.success(step.description())
                    .addLog("Step executed in " + (System.currentTimeMillis() - startTime) + "ms");

        } catch (Exception e) {
            log.error("Step {} execution failed: {}", step.order(), e.getMessage(), e);
            return StepResult.failure("执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行整个任务
     *
     * @param task 任务定义
     * @return 任务结果
     */
    public TaskResult executeTask(Task task) {
        long startTime = System.currentTimeMillis();
        java.util.List<StepResult> stepResults = new java.util.ArrayList<>();

        for (Step step : task.steps()) {
            // 更新步骤状态为运行中
            StepResult result = executeStep(step, TaskContext.create(task.taskId()));
            stepResults.add(result);

            if (!result.success()) {
                return TaskResult.failure(
                        task.taskId(),
                        "步骤 " + step.order() + " 执行失败: " + result.error(),
                        stepResults,
                        System.currentTimeMillis() - startTime
                );
            }
        }

        // 所有步骤成功完成
        Object finalOutput = stepResults.isEmpty() ? null :
                stepResults.get(stepResults.size() - 1).output();

        return TaskResult.success(
                task.taskId(),
                finalOutput,
                stepResults,
                System.currentTimeMillis() - startTime
        );
    }

    /**
     * 执行工具步骤
     */
    private StepResult executeToolStep(Step step, TaskContext context) {
        try {
            // 合并上下文变量到参数
            Map<String, Object> params = new java.util.HashMap<>(step.params());
            if (context != null) {
                if (context.getSessionId() != null) {
                    params.put("_sessionId", context.getSessionId());
                }
                if (context.getUserId() != null) {
                    params.put("_userId", context.getUserId());
                }
            }

            ToolResult toolResult = toolRegistry.execute(step.tool(), params);

            if (toolResult.success()) {
                return StepResult.success(toolResult.data())
                        .addLog("Tool " + step.tool() + " executed successfully");
            } else {
                return StepResult.failure(toolResult.error())
                        .addLog("Tool " + step.tool() + " failed: " + toolResult.error());
            }
        } catch (Exception e) {
            return StepResult.failure("工具执行异常: " + e.getMessage(), e);
        }
    }

    /**
     * 检查是否应该继续执行
     *
     * @param result 步骤结果
     * @return 是否应该继续
     */
    public boolean shouldContinue(StepResult result) {
        if (!result.success()) {
            // 可以添加更复杂的判断逻辑
            return false;
        }
        return true;
    }
}