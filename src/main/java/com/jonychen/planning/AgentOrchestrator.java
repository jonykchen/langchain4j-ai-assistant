package com.jonychen.planning;

import com.jonychen.planning.agent.PlanExecuteAgent;
import com.jonychen.planning.agent.ReActAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Agent 编排器
 *
 * 根据任务类型选择合适的 Agent 执行
 *
 * @author jonychen
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentOrchestrator {

    private final ReActAgent reActAgent;
    private final PlanExecuteAgent planExecuteAgent;
    private final TaskExecutor taskExecutor;

    /**
     * 执行任务
     *
     * @param goal    任务目标
     * @param context 任务上下文
     * @return 任务结果
     */
    public TaskResult execute(String goal, TaskContext context) {
        // 判断任务类型
        TaskType taskType = determineTaskType(goal);

        log.info("Executing task with type: {} for goal: {}", taskType, goal);

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
     * @param context  上下文
     * @return 任务结果
     */
    public TaskResult executeReAct(String question, TaskContext context) {
        long startTime = System.currentTimeMillis();

        var reActResult = reActAgent.execute(question, context);

        return TaskResult.withIterations(
                null,
                reActResult.success(),
                reActResult.answer(),
                reActResult.steps().stream()
                        .map(s -> new StepResult(
                                s.isFinalAnswer() || s.observation() != null,
                                s.isFinalAnswer() ? s.finalAnswer() : s.observation(),
                                null,
                                java.util.List.of(),
                                s.observation()
                        ))
                        .toList(),
                reActResult.success() ? null : reActResult.answer(),
                System.currentTimeMillis() - startTime,
                reActResult.iterations()
        );
    }

    /**
     * 使用 Plan-Execute 执行
     *
     * @param goal    目标
     * @param context 上下文
     * @return 任务结果
     */
    public TaskResult executePlanExecute(String goal, TaskContext context) {
        return planExecuteAgent.execute(goal, context);
    }

    /**
     * 执行简单任务
     */
    private TaskResult executeSimple(String goal, TaskContext context) {
        Task task = Task.create(goal);
        task = task.withStatus(TaskStatus.EXECUTING);

        Step step = Step.create(1, goal, goal);
        StepResult result = taskExecutor.executeStep(step, context);

        task = task.addStep(step.withStatus(result.success() ? StepStatus.COMPLETED : StepStatus.FAILED)
                .withResult(result));

        if (result.success()) {
            return TaskResult.success(task.taskId(), result.output(),
                    java.util.List.of(result), task.totalExecutionTimeMs());
        } else {
            return TaskResult.failure(task.taskId(), result.error(),
                    java.util.List.of(result), task.totalExecutionTimeMs());
        }
    }

    /**
     * 执行多步骤任务
     */
    private TaskResult executeMultiStep(String goal, TaskContext context) {
        // 使用 Plan-Execute Agent
        return planExecuteAgent.execute(goal, context);
    }

    /**
     * 执行复杂任务
     */
    private TaskResult executeComplex(String goal, TaskContext context) {
        // 复杂任务使用 ReAct 模式
        return executeReAct(goal, context);
    }

    /**
     * 判断任务类型
     */
    private TaskType determineTaskType(String goal) {
        if (goal == null) {
            return TaskType.SIMPLE;
        }

        String lowerGoal = goal.toLowerCase();

        // 复杂任务关键词
        if (lowerGoal.contains("分析") || lowerGoal.contains("比较")
                || lowerGoal.contains("评估") || lowerGoal.contains("决策")
                || lowerGoal.contains("不确定") || lowerGoal.contains("探索")) {
            return TaskType.COMPLEX;
        }

        // 多步骤关键词
        if (lowerGoal.contains("然后") || lowerGoal.contains("接着")
                || lowerGoal.contains("之后") || lowerGoal.contains("步骤")
                || lowerGoal.contains("依次") || lowerGoal.contains("顺序")) {
            return TaskType.MULTI_STEP;
        }

        return TaskType.SIMPLE;
    }
}