package com.jonychen.observability.state;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Agent 状态持久化服务
 *
 * @author jonychen
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentStateService {

    private final AgentStateSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;

    /** 默认快照过期时间（小时） */
    private static final int DEFAULT_EXPIRY_HOURS = 24;

    /**
     * 保存状态快照
     *
     * @param traceId Trace ID
     * @param sessionId 会话 ID
     * @param agentType Agent 类型
     * @param state 状态对象
     * @param currentStep 当前步骤
     * @param totalSteps 总步骤数
     * @param snapshotType 快照类型
     * @return 快照对象
     */
    @Transactional
    public AgentStateSnapshot saveSnapshot(
            String traceId,
            String sessionId,
            String agentType,
            Object state,
            int currentStep,
            int totalSteps,
            String snapshotType) {
        AgentStateSnapshot snapshot = new AgentStateSnapshot();
        snapshot.setTraceId(traceId);
        snapshot.setSessionId(sessionId);
        snapshot.setAgentType(agentType);
        snapshot.setCurrentStepIndex(currentStep);
        snapshot.setTotalSteps(totalSteps);
        snapshot.setSnapshotType(snapshotType);
        snapshot.setResumable(true);
        snapshot.setExpiresAt(LocalDateTime.now().plusHours(DEFAULT_EXPIRY_HOURS));

        // 序列化状态
        Map<String, Object> stateMap = objectMapper.convertValue(state, Map.class);
        snapshot.setInternalState(stateMap);

        snapshotRepository.save(snapshot);
        log.info(
                "Saved agent state snapshot: {} at step {}/{}",
                snapshot.getSnapshotId(),
                currentStep,
                totalSteps);

        return snapshot;
    }

    /** 保存检查点快照 */
    @Transactional
    public AgentStateSnapshot saveCheckpoint(
            String traceId,
            String sessionId,
            String agentType,
            Object state,
            int currentStep,
            int totalSteps) {
        return saveSnapshot(
                traceId, sessionId, agentType, state, currentStep, totalSteps, "CHECKPOINT");
    }

    /** 保存错误快照 */
    @Transactional
    public AgentStateSnapshot saveErrorSnapshot(
            String traceId,
            String sessionId,
            String agentType,
            Object state,
            int currentStep,
            int totalSteps) {
        AgentStateSnapshot snapshot =
                saveSnapshot(
                        traceId, sessionId, agentType, state, currentStep, totalSteps, "ERROR");
        snapshot.setResumable(false);
        return snapshotRepository.save(snapshot);
    }

    /** 保存暂停快照 */
    @Transactional
    public AgentStateSnapshot savePauseSnapshot(
            String traceId,
            String sessionId,
            String agentType,
            Object state,
            int currentStep,
            int totalSteps) {
        return saveSnapshot(traceId, sessionId, agentType, state, currentStep, totalSteps, "PAUSE");
    }

    /**
     * 从快照恢复
     *
     * @param snapshotId 快照 ID
     * @return 恢复上下文
     */
    public AgentResumeContext resumeFromSnapshot(String snapshotId) {
        AgentStateSnapshot snapshot =
                snapshotRepository
                        .findBySnapshotId(snapshotId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Snapshot not found: " + snapshotId));

        if (snapshot.isExpired()) {
            throw new IllegalStateException("Snapshot expired: " + snapshotId);
        }

        if (!snapshot.getResumable()) {
            throw new IllegalStateException("Snapshot not resumable: " + snapshotId);
        }

        log.info("Resuming from snapshot: {}", snapshotId);
        return new AgentResumeContext(
                snapshot.getTraceId(),
                snapshot.getSessionId(),
                snapshot.getAgentType(),
                snapshot.getInternalState(),
                snapshot.getCurrentStepIndex(),
                snapshot.getTotalSteps(),
                snapshot.getExecutionHistory());
    }

    /** 获取会话的最新可恢复快照 */
    public Optional<AgentStateSnapshot> getLatestResumableSnapshot(String sessionId) {
        return snapshotRepository.findLatestResumableBySessionId(sessionId);
    }

    /** 检查会话是否有可恢复的快照 */
    public boolean hasResumableSnapshot(String sessionId) {
        return snapshotRepository.findLatestResumableBySessionId(sessionId).isPresent();
    }

    /** 标记快照为不可恢复 */
    @Transactional
    public void markAsNonResumable(String snapshotId) {
        snapshotRepository
                .findBySnapshotId(snapshotId)
                .ifPresent(
                        snapshot -> {
                            snapshot.setResumable(false);
                            snapshotRepository.save(snapshot);
                            log.info("Marked snapshot as non-resumable: {}", snapshotId);
                        });
    }

    /** 删除快照 */
    @Transactional
    public void deleteSnapshot(String snapshotId) {
        snapshotRepository
                .findBySnapshotId(snapshotId)
                .ifPresent(
                        snapshot -> {
                            snapshotRepository.delete(snapshot);
                            log.info("Deleted snapshot: {}", snapshotId);
                        });
    }

    /** 删除会话的所有快照 */
    @Transactional
    public void deleteSessionSnapshots(String sessionId) {
        snapshotRepository.deleteBySessionId(sessionId);
        log.info("Deleted all snapshots for session: {}", sessionId);
    }

    /** 清理过期快照 */
    @Transactional
    public int cleanupExpiredSnapshots() {
        List<AgentStateSnapshot> expired =
                snapshotRepository.findByExpiresAtBefore(LocalDateTime.now());
        snapshotRepository.deleteAll(expired);
        log.info("Cleaned up {} expired snapshots", expired.size());
        return expired.size();
    }

    /** 获取会话的所有快照 */
    public List<AgentStateSnapshot> getSessionSnapshots(String sessionId) {
        return snapshotRepository.findBySessionIdOrderByCreatedAtDesc(sessionId);
    }

    /** 获取 Trace 的所有快照 */
    public List<AgentStateSnapshot> getTraceSnapshots(String traceId) {
        return snapshotRepository.findByTraceId(traceId);
    }

    /** 获取所有可恢复的快照 */
    public List<AgentStateSnapshot> getAllResumableSnapshots() {
        return snapshotRepository.findAllResumable();
    }

    /** 更新快照状态 */
    @Transactional
    public void updateSnapshotState(String snapshotId, Object newState, int currentStep) {
        snapshotRepository
                .findBySnapshotId(snapshotId)
                .ifPresent(
                        snapshot -> {
                            Map<String, Object> stateMap =
                                    objectMapper.convertValue(newState, Map.class);
                            snapshot.setInternalState(stateMap);
                            snapshot.setCurrentStepIndex(currentStep);
                            snapshotRepository.save(snapshot);
                        });
    }

    /** 添加执行历史记录 */
    @Transactional
    public void addExecutionHistory(String snapshotId, Map<String, Object> historyEntry) {
        snapshotRepository
                .findBySnapshotId(snapshotId)
                .ifPresent(
                        snapshot -> {
                            List<Map<String, Object>> history = snapshot.getExecutionHistory();
                            if (history == null) {
                                history = new ArrayList<>();
                            }
                            history.add(historyEntry);
                            snapshot.setExecutionHistory(history);
                            snapshotRepository.save(snapshot);
                        });
    }

    /** 获取统计信息 */
    public SnapshotStatistics getStatistics() {
        List<AgentStateSnapshot> resumable = snapshotRepository.findByResumableTrue();
        long expired = resumable.stream().filter(AgentStateSnapshot::isExpired).count();
        long active = resumable.size() - expired;

        return new SnapshotStatistics(
                snapshotRepository.count(), resumable.size(), active, expired);
    }

    /** 恢复上下文 */
    public record AgentResumeContext(
            String traceId,
            String sessionId,
            String agentType,
            Map<String, Object> internalState,
            int currentStepIndex,
            int totalSteps,
            List<Map<String, Object>> executionHistory) {
        /** 获取状态中的特定字段（使用 ObjectMapper 安全转换） */
        public <T> T getStateField(String fieldName, Class<T> type) {
            if (internalState == null) {
                return null;
            }
            Object value = internalState.get(fieldName);
            if (value == null) {
                return null;
            }
            // 使用 ObjectMapper 进行安全转换，避免 ClassCastException
            try {
                ObjectMapper mapper = new ObjectMapper();
                return mapper.convertValue(value, type);
            } catch (IllegalArgumentException e) {
                // 转换失败时返回 null
                return null;
            }
        }
    }

    /** 快照统计信息 */
    public record SnapshotStatistics(long total, long resumable, long active, long expired) {}
}
