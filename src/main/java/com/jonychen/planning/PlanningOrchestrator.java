package com.jonychen.planning;

import org.springframework.stereotype.Service;

import com.jonychen.planning.agent.PlanExecuteAgent;
import com.jonychen.planning.agent.ReActAgent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Planning 编排器
 *
 * <p>根据任务类型选择合适的 Agent 执行（ReAct, Plan-Execute, Simple）
 *
 * <p>注意：这是 planning 包的编排器，用于任务规划系统。
 * 多 Agent 系统请使用 {@link com.jonychen.agent.core.AgentOrchestrator}。
 *
 * @author jonychen
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanningOrchestrator {

    private final ReActAgent reActAgent;
    private final PlanExecuteAgent planExecuteAgent;
    private final TaskExecutor taskExecutor;

    /**
     * 执行任务
     *
     * @param goal 任务目标
     * @param context 任务上下文
     * @return 任务结果
     */
    public TaskResult execute(String goal, TaskContext context) {
        // 判断任务类型
        TaskType taskType = determineTaskType(goal);

        log.info("[Orchestrator] 任务类型判定: {} | 目标: {}", taskType.getDisplayName(), goal);
        log.info(
                "[Orchestrator] {} → {}",
                taskType,
                switch (taskType) {
                    case SIMPLE -> "TaskExecutor(SIMPLE模式-直接LLM执行)";
                    case MULTI_STEP -> "PlanExecuteAgent(先规划后执行)";
                    case COMPLEX -> "ReActAgent(推理-行动循环)";
                });

        return switch (taskType) {
            case SIMPLE -> executeSimple(goal, context);
            case MULTI_STEP -> executeMultiStep(goal, context);
            case COMPLEX -> executeComplex(goal, context);
        };
    }

    /**
     * 使用 ReAct 执行
     *
     * @param question 问题
     * @param context 上下文
     * @return 任务结果
     */
    public TaskResult executeReAct(String question, TaskContext context) {
        log.info("[Orchestrator] ReAct模式 | 问题: {}", question);
        long startTime = System.currentTimeMillis();

        log.info("[Orchestrator] → 调用 ReActAgent.execute()...");
        var reActResult = reActAgent.execute(question, context);

        log.info(
                "[Orchestrator] ReAct完成 | 迭代: {}次 | 成功: {} | 耗时: {}ms",
                reActResult.iterations(),
                reActResult.success(),
                System.currentTimeMillis() - startTime);

        return TaskResult.withIterations(
                null,
                reActResult.success(),
                reActResult.answer(),
                reActResult.steps().stream()
                        .map(
                                s ->
                                        new StepResult(
                                                s.isFinalAnswer() || s.observation() != null,
                                                s.isFinalAnswer()
                                                        ? s.finalAnswer()
                                                        : s.observation(),
                                                null,
                                                java.util.List.of(),
                                                s.observation()))
                        .toList(),
                reActResult.success() ? null : reActResult.answer(),
                System.currentTimeMillis() - startTime,
                reActResult.iterations());
    }

    /**
     * 使用 Plan-Execute 执行
     *
     * @param goal 目标
     * @param context 上下文
     * @return 任务结果
     */
    public TaskResult executePlanExecute(String goal, TaskContext context) {
        log.info("[Orchestrator] Plan-Execute模式 | 目标: {}", goal);
        log.info("[Orchestrator] → 调用 PlanExecuteAgent.execute()...");
        return planExecuteAgent.execute(goal, context);
    }

    /** 执行简单任务 */
    private TaskResult executeSimple(String goal, TaskContext context) {
        log.info("[Orchestrator] SIMPLE模式 | 创建单步任务...");
        Task task = Task.create(goal);
        task = task.withStatus(TaskStatus.EXECUTING);

        Step step = Step.create(1, goal, goal);
        log.info("[Orchestrator] → 调用 TaskExecutor.executeStep()...");

        StepResult result = taskExecutor.executeStep(step, context);

        task =
                task.addStep(
                        step.withStatus(result.success() ? StepStatus.COMPLETED : StepStatus.FAILED)
                                .withResult(result));

        log.info(
                "[Orchestrator] SIMPLE完成 | 成功: {} | 输出长度: {}",
                result.success(),
                result.output() != null ? result.output().toString().length() : 0);

        if (result.success()) {
            return TaskResult.success(
                    task.taskId(),
                    result.output(),
                    java.util.List.of(result),
                    task.totalExecutionTimeMs());
        } else {
            return TaskResult.failure(
                    task.taskId(),
                    result.error(),
                    java.util.List.of(result),
                    task.totalExecutionTimeMs());
        }
    }

    /** 执行多步骤任务 */
    private TaskResult executeMultiStep(String goal, TaskContext context) {
        log.info("[Orchestrator] MULTI_STEP模式 | → 委托给 PlanExecuteAgent...");
        return planExecuteAgent.execute(goal, context);
    }

    /** 执行复杂任务 */
    private TaskResult executeComplex(String goal, TaskContext context) {
        log.info("[Orchestrator] COMPLEX模式 | → 委托给 ReActAgent...");
        return executeReAct(goal, context);
    }

    /** 判断任务类型 */
    private TaskType determineTaskType(String goal) {
        if (goal == null) {
            log.info("[Orchestrator] 类型判定: goal为null → SIMPLE");
            return TaskType.SIMPLE;
        }

        String lowerGoal = goal.toLowerCase();

        // 复杂任务关键词
        if (lowerGoal.contains("分析")
                || lowerGoal.contains("比较")
                || lowerGoal.contains("评估")
                || lowerGoal.contains("决策")
                || lowerGoal.contains("不确定")
                || lowerGoal.contains("探索")) {
            log.info("[Orchestrator] 类型判定: 命中复杂任务关键词 → COMPLEX");
            return TaskType.COMPLEX;
        }

        // 多步骤关键词
        if (lowerGoal.contains("然后")
                || lowerGoal.contains("接着")
                || lowerGoal.contains("之后")
                || lowerGoal.contains("步骤")
                || lowerGoal.contains("依次")
                || lowerGoal.contains("顺序")) {
            log.info("[Orchestrator] 类型判定: 命中多步骤关键词 → MULTI_STEP");
            return TaskType.MULTI_STEP;
        }

        log.info("[Orchestrator] 类型判定: 未命中关键词 → SIMPLE");
        return TaskType.SIMPLE;
    }
}
