package com.jonychen.planning;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.jonychen.tool.ToolRegistry;
import com.jonychen.tool.ToolResult;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
    private final ChatModel chatModel;

    /**
     * 执行单个步骤
     *
     * @param step 步骤定义
     * @param context 任务上下文
     * @return 步骤结果
     */
    public StepResult executeStep(Step step, TaskContext context) {
        log.info("[TaskExecutor] 执行步骤 {}/{}", step.order(), step.description());
        log.info(
                "[TaskExecutor] 执行方式: {} | 工具: {}",
                step.tool() != null ? "TOOL" : "LLM",
                step.tool() != null ? step.tool() : "ChatModel");

        long startTime = System.currentTimeMillis();

        try {
            // 检查是否有工具调用
            if (step.tool() != null && !step.tool().isBlank()) {
                log.info("[TaskExecutor] → 调用工具: {}", step.tool());
                StepResult result = executeToolStep(step, context);
                log.info(
                        "[TaskExecutor] 工具返回 | 成功: {} | 耗时: {}ms",
                        result.success(),
                        System.currentTimeMillis() - startTime);
                return result;
            }

            // 没有工具时，使用 LLM 执行
            log.info("[TaskExecutor] → 调用 LLM (ChatModel)...");
            StepResult result = executeLLMStep(step);
            log.info(
                    "[TaskExecutor] LLM返回 | 成功: {} | 输出长度: {} | 耗时: {}ms",
                    result.success(),
                    result.output() != null ? result.output().toString().length() : 0,
                    System.currentTimeMillis() - startTime);
            return result;

        } catch (Exception e) {
            log.error("[TaskExecutor] 步骤执行失败: {}", e.getMessage(), e);
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
        log.info("[TaskExecutor] 开始执行任务 | 目标: {} | 步骤数: {}", task.goal(), task.steps().size());
        long startTime = System.currentTimeMillis();
        java.util.List<StepResult> stepResults = new java.util.ArrayList<>();

        for (Step step : task.steps()) {
            // 更新步骤状态为运行中
            log.info("[TaskExecutor] ── 步骤 {}/{} ──", step.order(), task.steps().size());
            StepResult result = executeStep(step, TaskContext.create(task.taskId()));
            stepResults.add(result);

            if (!result.success()) {
                log.warn("[TaskExecutor] 步骤 {} 失败: {}", step.order(), result.error());
                return TaskResult.failure(
                        task.taskId(),
                        "步骤 " + step.order() + " 执行失败: " + result.error(),
                        stepResults,
                        System.currentTimeMillis() - startTime);
            }
        }

        // 所有步骤成功完成
        Object finalOutput =
                stepResults.isEmpty() ? null : stepResults.get(stepResults.size() - 1).output();

        log.info(
                "[TaskExecutor] 任务完成 | 成功步骤: {}/{} | 总耗时: {}ms",
                stepResults.size(),
                task.steps().size(),
                System.currentTimeMillis() - startTime);

        return TaskResult.success(
                task.taskId(), finalOutput, stepResults, System.currentTimeMillis() - startTime);
    }

    /** 使用 LLM 执行步骤 */
    private StepResult executeLLMStep(Step step) {
        String prompt = "执行以下任务: " + step.description();
        if (step.action() != null && !step.action().isBlank()) {
            prompt += "\n具体行动: " + step.action();
        }

        String response =
                chatModel
                        .chat(ChatRequest.builder().messages(UserMessage.from(prompt)).build())
                        .aiMessage()
                        .text();
        return StepResult.success(response);
    }

    /** 执行工具步骤 */
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
