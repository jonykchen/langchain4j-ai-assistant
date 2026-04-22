package com.jonychen.tool.audit;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 工具执行审计仓库
 *
 * @author jonychen
 */
@Repository
public interface ToolExecutionAuditRepository extends JpaRepository<ToolExecutionAudit, Long> {

    /** 按执行ID查询 */
    ToolExecutionAudit findByExecutionId(String executionId);

    /** 按工具名称查询 */
    List<ToolExecutionAudit> findByToolName(String toolName);

    /** 按用户ID查询 */
    List<ToolExecutionAudit> findByUserIdOrderByExecutedAtDesc(String userId);

    /** 按会话ID查询 */
    List<ToolExecutionAudit> findBySessionIdOrderByExecutedAtDesc(String sessionId);

    /** 按时间范围查询 */
    List<ToolExecutionAudit> findByExecutedAtBetween(LocalDateTime start, LocalDateTime end);

    /** 按工具名称和时间范围查询 */
    List<ToolExecutionAudit> findByToolNameAndExecutedAtBetween(
            String toolName, LocalDateTime start, LocalDateTime end);

    /** 查询失败的执行 */
    List<ToolExecutionAudit> findBySuccessFalseOrderByExecutedAtDesc();

    /** 查询需要确认的执行 */
    List<ToolExecutionAudit> findByConfirmationRequiredTrueOrderByExecutedAtDesc();
}
