package com.jonychen.planning.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jonychen.planning.Step;
import com.jonychen.planning.StepResult;
import com.jonychen.planning.StepStatus;
import com.jonychen.planning.Task;
import com.jonychen.planning.TaskContext;
import com.jonychen.planning.TaskExecutor;
import com.jonychen.planning.TaskResult;
import com.jonychen.planning.TaskStatus;
import com.jonychen.tool.ToolRegistry;
import com.jonychen.tool.ToolResult;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Plan-Execute Agent：先规划后执行
 *
 * <p>工作流程： 1. Planning: LLM 生成完整计划 2. Execution: 按顺序执行每个步骤 3. Replanning: 遇到失败时重新规划
 *
 * @author jonychen
 * @deprecated 计划迁移到 agent/impl/ 包，新代码应使用 agent/core 包下的 AbstractAgent。
 */
@Slf4j
@Component
@Deprecated
public class PlanExecuteAgent {

    private static final String PLANNING_PROMPT =
            """
            你是一个任务规划专家。根据用户目标，分解为具体执行步骤。

            输出格式（JSON）：
            ```json
            {
              "steps": [
                {
                  "order": 1,
                  "description": "步骤描述",
                  "action": "工具名称或具体行动",
                  "tool": "工具名称（如果需要调用工具）",
                  "params": {"param1": "value1"}
                }
              ]
            }
            ```

            可用工具：
            {tools}

            规则：
            1. 每个步骤应该明确、可执行
            2. 步骤之间应该有逻辑顺序
            3. 如果需要使用工具，指定工具名称和参数
            4. 步骤数量控制在 3-10 个之间

            用户目标：{goal}
            """;

    private static final String REPLAN_PROMPT =
            """
            任务执行过程中遇到问题，需要重新规划。

            原计划：
            {originalPlan}

            已完成的步骤：
            {completedSteps}

            失败的步骤：
            步骤 {failedOrder}: {failedDescription}
            错误：{error}

            请生成新的执行计划（JSON格式，与之前相同的格式）：

            可用工具：
            {tools}

            用户目标：{goal}
            """;

    private static final int MAX_REPLANS = 3;

    private final ChatModel chatModel;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    private final TaskExecutor taskExecutor;

    public PlanExecuteAgent(
            ChatModel chatModel, ToolRegistry toolRegistry, TaskExecutor taskExecutor) {
        this.chatModel = chatModel;
        this.toolRegistry = toolRegistry;
        this.objectMapper = new ObjectMapper();
        this.taskExecutor = taskExecutor;
    }

    /**
     * 执行任务
     *
     * @param goal 任务目标
     * @param context 任务上下文
     * @return 执行结果
     */
    public TaskResult execute(String goal, TaskContext context) {
        log.info("[PlanExecute] ========== 开始 Plan-Execute 流程 ==========");
        log.info("[PlanExecute] 目标: {}", goal);
        long startTime = System.currentTimeMillis();

        // 1. 生成初始计划
        log.info("[PlanExecute] 阶段1: Planning (规划)...");
        log.info("[PlanExecute] → 调用 LLM 生成执行计划...");
        List<Step> steps = plan(goal);
        if (steps.isEmpty()) {
            log.error("[PlanExecute] 规划失败: 无法生成执行计划");
            return TaskResult.failure(null, "无法生成执行计划", List.of(), 0);
        }

        log.info("[PlanExecute] 规划完成 | 生成 {} 个步骤:", steps.size());
        for (Step s : steps) {
            log.info(
                    "[PlanExecute]   步骤{}: {} | 工具: {}",
                    s.order(),
                    s.description(),
                    s.tool() != null ? s.tool() : "LLM");
        }

        Task task = Task.create(goal).withSteps(steps);
        task = task.withStatus(TaskStatus.EXECUTING);

        // 2. 执行步骤
        log.info("[PlanExecute] 阶段2: Execution (执行)...");
        int replanCount = 0;
        List<StepResult> stepResults = new ArrayList<>();

        while (true) {
            Step currentStep = task.getNextPendingStep();

            if (currentStep == null) {
                log.info("[PlanExecute] 所有步骤执行完成");
                break;
            }

            log.info("[PlanExecute] ── 执行步骤 {}/{} ──", currentStep.order(), steps.size());
            // 执行步骤
            StepResult result = executeStep(currentStep, context);
            stepResults.add(result);

            // 更新步骤状态
            Step updatedStep =
                    currentStep
                            .withStatus(result.success() ? StepStatus.COMPLETED : StepStatus.FAILED)
                            .withResult(result);
            task = task.updateStep(currentStep.stepId(), updatedStep);

            if (!result.success()) {
                // 步骤失败，尝试重新规划
                replanCount++;

                if (replanCount <= MAX_REPLANS) {
                    log.warn(
                            "[PlanExecute] 步骤 {} 失败，尝试重新规划 ({}/{})",
                            currentStep.order(),
                            replanCount,
                            MAX_REPLANS);

                    List<Step> newSteps = replan(task, currentStep, result.error());
                    if (!newSteps.isEmpty()) {
                        log.info("[PlanExecute] 重新规划完成 | 新步骤数: {}", newSteps.size());
                        task = task.withSteps(newSteps);
                        stepResults.clear(); // 重置结果
                        continue;
                    }
                }

                log.error("[PlanExecute] 执行失败 | 重试次数耗尽");
                return TaskResult.failure(
                        task.taskId(),
                        "步骤执行失败: " + result.error(),
                        stepResults,
                        System.currentTimeMillis() - startTime);
            }
        }

        // 3. 构建最终结果
        Object finalOutput =
                stepResults.isEmpty() ? null : stepResults.get(stepResults.size() - 1).output();

        log.info("[PlanExecute] ========== Plan-Execute 完成 ==========");
        log.info(
                "[PlanExecute] 总耗时: {}ms | 步骤数: {} | 重规划次数: {}",
                System.currentTimeMillis() - startTime,
                stepResults.size(),
                replanCount);

        return TaskResult.success(
                task.taskId(), finalOutput, stepResults, System.currentTimeMillis() - startTime);
    }

    /** 规划任务 */
    private List<Step> plan(String goal) {
        String prompt =
                PLANNING_PROMPT.replace("{tools}", buildToolsDescription()).replace("{goal}", goal);

        String response =
                chatModel
                        .chat(ChatRequest.builder().messages(UserMessage.from(prompt)).build())
                        .aiMessage()
                        .text();
        return parsePlanResponse(response);
    }

    /** 重新规划 */
    private List<Step> replan(Task task, Step failedStep, String error) {
        String completedSteps =
                task.steps().stream()
                        .filter(s -> s.status() == StepStatus.COMPLETED)
                        .map(s -> String.format("  %d. %s", s.order(), s.description()))
                        .reduce("", (a, b) -> a + "\n" + b);

        String prompt =
                REPLAN_PROMPT
                        .replace("{originalPlan}", formatPlan(task.steps()))
                        .replace("{completedSteps}", completedSteps)
                        .replace("{failedOrder}", String.valueOf(failedStep.order()))
                        .replace("{failedDescription}", failedStep.description())
                        .replace("{error}", error)
                        .replace("{tools}", buildToolsDescription())
                        .replace("{goal}", task.goal());

        String response =
                chatModel
                        .chat(ChatRequest.builder().messages(UserMessage.from(prompt)).build())
                        .aiMessage()
                        .text();
        return parsePlanResponse(response);
    }

    /** 执行单个步骤 */
    private StepResult executeStep(Step step, TaskContext context) {
        log.info("[PlanExecute] 执行步骤 {}: {}", step.order(), step.description());

        // 检查是否有工具调用
        if (step.tool() != null && !step.tool().isBlank()) {
            log.info("[PlanExecute] → 调用工具: {}", step.tool());
            return executeToolStep(step, context);
        }

        // 普通步骤，使用 LLM 执行
        log.info("[PlanExecute] → 调用 LLM...");
        return executeLLMStep(step, context);
    }

    /** 执行工具步骤 */
    private StepResult executeToolStep(Step step, TaskContext context) {
        try {
            ToolResult result = toolRegistry.execute(step.tool(), step.params());
            if (result.success()) {
                return StepResult.success(result.data());
            } else {
                return StepResult.failure(result.error());
            }
        } catch (Exception e) {
            return StepResult.failure("工具执行异常: " + e.getMessage(), e);
        }
    }

    /** 执行 LLM 步骤 */
    private StepResult executeLLMStep(Step step, TaskContext context) {
        String prompt = "执行以下任务: " + step.description();
        if (step.action() != null) {
            prompt += "\n具体行动: " + step.action();
        }

        String response =
                chatModel
                        .chat(ChatRequest.builder().messages(UserMessage.from(prompt)).build())
                        .aiMessage()
                        .text();
        return StepResult.success(response);
    }

    /** 解析规划响应 */
    @SuppressWarnings("unchecked")
    private List<Step> parsePlanResponse(String response) {
        List<Step> steps = new ArrayList<>();

        try {
            // 提取 JSON
            String json = extractJson(response);
            if (json == null) {
                return steps;
            }

            Map<String, Object> plan = objectMapper.readValue(json, Map.class);
            List<Map<String, Object>> stepsData = (List<Map<String, Object>>) plan.get("steps");

            if (stepsData == null) {
                return steps;
            }

            for (Map<String, Object> stepData : stepsData) {
                int order = ((Number) stepData.getOrDefault("order", 0)).intValue();
                String description = (String) stepData.get("description");
                String tool = (String) stepData.get("tool");
                Map<String, Object> params =
                        (Map<String, Object>) stepData.getOrDefault("params", Map.of());

                Step step =
                        tool != null
                                ? Step.createWithTool(order, description, tool, params)
                                : Step.create(order, description, (String) stepData.get("action"));

                steps.add(step);
            }

        } catch (Exception e) {
            log.error("Failed to parse plan response: {}", e.getMessage());
        }

        return steps;
    }

    /** 提取 JSON */
    private String extractJson(String text) {
        int start = text.indexOf("```json");
        if (start == -1) {
            start = text.indexOf("{");
            if (start == -1) {
                return null;
            }
            int end = text.lastIndexOf("}");
            if (end == -1 || end < start) {
                return null;
            }
            return text.substring(start, end + 1);
        }

        start += 7;
        int end = text.indexOf("```", start);
        if (end == -1) {
            return null;
        }
        return text.substring(start, end).trim();
    }

    /** 格式化计划 */
    private String formatPlan(List<Step> steps) {
        StringBuilder sb = new StringBuilder();
        for (Step step : steps) {
            sb.append(step.order()).append(". ").append(step.description());
            if (step.tool() != null) {
                sb.append(" (工具: ").append(step.tool()).append(")");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /** 构建工具描述 */
    private String buildToolsDescription() {
        StringBuilder sb = new StringBuilder();
        for (var tool : toolRegistry.getAllTools()) {
            sb.append("- ")
                    .append(tool.name())
                    .append(": ")
                    .append(tool.description())
                    .append("\n");
        }
        return sb.toString();
    }
}
