package com.jonychen.observability.state;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 状态快照
 *
 * @author jonychen
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "agent_state_snapshots", schema = "app")
public class AgentStateSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 快照 ID
     */
    @Column(name = "snapshot_id", unique = true, nullable = false, length = 36)
    private String snapshotId;

    /**
     * 关联的 Trace ID
     */
    @Column(name = "trace_id", length = 36)
    private String traceId;

    /**
     * 会话 ID
     */
    @Column(name = "session_id", length = 36)
    private String sessionId;

    /**
     * Agent 类型
     */
    @Column(name = "agent_type", length = 50)
    private String agentType;

    /**
     * 当前步骤索引
     */
    @Column(name = "current_step_index")
    @Builder.Default
    private Integer currentStepIndex = 0;

    /**
     * 总步骤数
     */
    @Column(name = "total_steps")
    @Builder.Default
    private Integer totalSteps = 0;

    /**
     * Agent 内部状态（JSON）
     */
    @Column(name = "internal_state", columnDefinition = "JSONB")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private Map<String, Object> internalState;

    /**
     * 执行历史（JSON）
     */
    @Column(name = "execution_history", columnDefinition = "JSONB")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private List<Map<String, Object>> executionHistory;

    /**
     * 创建时间
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * 快照类型：CHECKPOINT, ERROR, PAUSE, STEP_COMPLETE
     */
    @Column(name = "snapshot_type", length = 20)
    @Builder.Default
    private String snapshotType = "CHECKPOINT";

    /**
     * 可恢复标志
     */
    @Column(name = "resumable")
    @Builder.Default
    private Boolean resumable = true;

    /**
     * 过期时间
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @PrePersist
    void prePersist() {
        if (snapshotId == null) {
            snapshotId = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (snapshotType == null) {
            snapshotType = "CHECKPOINT";
        }
        if (resumable == null) {
            resumable = true;
        }
        if (currentStepIndex == null) {
            currentStepIndex = 0;
        }
        if (totalSteps == null) {
            totalSteps = 0;
        }
    }

    /**
     * 检查是否已过期
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
}
