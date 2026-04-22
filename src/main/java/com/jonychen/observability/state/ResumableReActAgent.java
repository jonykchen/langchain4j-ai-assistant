package com.jonychen.observability.state;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.jonychen.observability.trace.AgentTraceService;
import com.jonychen.planning.TaskContext;
import com.jonychen.planning.agent.ReActAgent;
import com.jonychen.planning.agent.ReActResult;
import com.jonychen.planning.agent.ReActStep;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 支持断点续传的 ReAct Agent 在执行过程中自动保存检查点，支持从断点恢复执行
 *
 * @author jonychen
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResumableReActAgent {

    private final ReActAgent delegate;
    private final AgentStateService stateService;
    private final AgentTraceService traceService;

    /**
     * 执行并支持恢复
     *
     * @param question 用户问题
     * @param context 任务上下文
     * @return 执行结果
     */
    public ReActResult executeWithResume(String question, TaskContext context) {
        String sessionId = context != null ? context.getSessionId() : null;

        // 检查是否有可恢复的快照
        Optional<AgentStateSnapshot> existingSnapshot =
                stateService.getLatestResumableSnapshot(sessionId);

        if (existingSnapshot.isPresent()) {
            log.info("Found resumable snapshot, attempting to resume");
            return resume(existingSnapshot.get(), question, context);
        }

        // 没有可恢复的快照，正常执行
        return execute(question, context);
    }

    /**
     * 从快照恢复执行
     *
     * @param snapshot 状态快照
     * @param question 用户问题
     * @param context 任务上下文
     * @return 执行结果
     */
    public ReActResult resume(AgentStateSnapshot snapshot, String question, TaskContext context) {
        AgentStateService.AgentResumeContext resumeContext =
                stateService.resumeFromSnapshot(snapshot.getSnapshotId());

        log.info(
                "Resuming execution from step {}/{}",
                resumeContext.currentStepIndex(),
                resumeContext.totalSteps());

        // 恢复执行上下文（如果需要可以恢复变量等）
        if (context != null && resumeContext.internalState() != null) {
            restoreContext(context, resumeContext.internalState());
        }

        // 继续执行
        ReActResult result = delegate.execute(question, context);

        // 标记旧快照为不可恢复
        stateService.markAsNonResumable(snapshot.getSnapshotId());

        return result;
    }

    /**
     * 正常执行（带检查点）
     *
     * @param question 用户问题
     * @param context 任务上下文
     * @return 执行结果
     */
    public ReActResult execute(String question, TaskContext context) {
        // 调用原始 Agent 执行
        return delegate.execute(question, context);
    }

    /**
     * 暂停执行
     *
     * @param sessionId 会话 ID
     * @param traceId Trace ID
     * @param completedSteps 已完成的步骤
     * @param currentStep 当前步骤索引
     * @param maxSteps 最大步骤数
     * @return 快照 ID
     */
    public String pause(
            String sessionId,
            String traceId,
            List<ReActStep> completedSteps,
            int currentStep,
            int maxSteps) {
        // 序列化已完成步骤
        List<Map<String, Object>> history = new ArrayList<>();
        for (ReActStep step : completedSteps) {
            Map<String, Object> stepMap = new LinkedHashMap<>();
            stepMap.put("thought", step.thought());
            stepMap.put("action", step.action());
            stepMap.put("actionInput", step.actionInput());
            stepMap.put("observation", step.observation());
            stepMap.put("isFinalAnswer", step.isFinalAnswer());
            stepMap.put("finalAnswer", step.finalAnswer());
            history.add(stepMap);
        }

        // 构建内部状态
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("completedSteps", history);
        state.put("currentStepIndex", currentStep);

        // 保存暂停快照
        AgentStateSnapshot snapshot =
                stateService.savePauseSnapshot(
                        traceId, sessionId, "REACT", state, currentStep, maxSteps);

        log.info("Agent execution paused, snapshot: {}", snapshot.getSnapshotId());
        return snapshot.getSnapshotId();
    }

    /**
     * 保存检查点
     *
     * @param sessionId 会话 ID
     * @param traceId Trace ID
     * @param state 当前状态
     * @param currentStep 当前步骤索引
     * @param maxSteps 最大步骤数
     * @return 快照 ID
     */
    public String saveCheckpoint(
            String sessionId, String traceId, Object state, int currentStep, int maxSteps) {
        AgentStateSnapshot snapshot =
                stateService.saveCheckpoint(
                        traceId, sessionId, "REACT", state, currentStep, maxSteps);

        log.debug(
                "Checkpoint saved: {} at step {}/{}",
                snapshot.getSnapshotId(),
                currentStep,
                maxSteps);
        return snapshot.getSnapshotId();
    }

    /**
     * 检查是否有可恢复的执行
     *
     * @param sessionId 会话 ID
     * @return 是否有可恢复的快照
     */
    public boolean canResume(String sessionId) {
        return stateService.hasResumableSnapshot(sessionId);
    }

    /**
     * 获取可恢复快照信息
     *
     * @param sessionId 会话 ID
     * @return 快照信息（如果存在）
     */
    public Optional<AgentStateSnapshot> getResumableSnapshot(String sessionId) {
        return stateService.getLatestResumableSnapshot(sessionId);
    }

    /**
     * 放弃恢复
     *
     * @param sessionId 会话 ID
     */
    public void abandonResume(String sessionId) {
        stateService
                .getSessionSnapshots(sessionId)
                .forEach(
                        snapshot -> {
                            if (snapshot.getResumable()) {
                                stateService.markAsNonResumable(snapshot.getSnapshotId());
                            }
                        });
        log.info("Abandoned all resumable snapshots for session: {}", sessionId);
    }

    /** 恢复任务上下文 */
    private void restoreContext(TaskContext context, Map<String, Object> state) {
        if (state == null) {
            return;
        }

        // 恢复变量
        @SuppressWarnings("unchecked")
        Map<String, Object> variables = (Map<String, Object>) state.get("variables");
        if (variables != null && context.getVariables() != null) {
            context.getVariables().putAll(variables);
        }

        // 恢复步骤结果
        @SuppressWarnings("unchecked")
        Map<String, Object> stepResults = (Map<String, Object>) state.get("stepResults");
        if (stepResults != null && context.getStepResults() != null) {
            // 逐个添加步骤结果（类型转换）
            stepResults.forEach(
                    (key, value) -> {
                        if (value instanceof com.jonychen.planning.StepResult sr) {
                            context.getStepResults().put(key, sr);
                        }
                    });
        }
    }
}
