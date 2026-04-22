package com.jonychen.test.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.jonychen.test.entity.E2ETestResult;

/**
 * E2E 测试结果 Repository
 *
 * @author jonychen
 */
@Repository
public interface E2ETestResultRepository extends JpaRepository<E2ETestResult, Long> {

    /** 按 jobId 查询所有结果 */
    List<E2ETestResult> findByJobId(String jobId);

    /** 按 jobId 分页查询 */
    Page<E2ETestResult> findByJobId(String jobId, Pageable pageable);

    /** 按状态查询 */
    Page<E2ETestResult> findByStatus(String status, Pageable pageable);

    /** 按 jobId 和状态查询 */
    List<E2ETestResult> findByJobIdAndStatus(String jobId, String status);

    /** 统计 jobId 下各状态的数量 */
    @Query(
            "SELECT e.status, COUNT(e) FROM E2ETestResult e WHERE e.jobId = :jobId GROUP BY e.status")
    List<Object[]> countByJobIdGroupByStatus(@Param("jobId") String jobId);

    /** 计算 jobId 的总断言数 */
    @Query(
            "SELECT SUM(e.assertionsPassed), SUM(e.assertionsFailed) FROM E2ETestResult e WHERE e.jobId = :jobId")
    Object[] sumAssertionsByJobId(@Param("jobId") String jobId);

    /** 删除指定 jobId 的所有结果 */
    @Modifying
    void deleteByJobId(String jobId);

    /** 统计通过率 */
    @Query("SELECT COUNT(e) FROM E2ETestResult e WHERE e.jobId = :jobId AND e.status = 'passed'")
    long countPassedByJobId(@Param("jobId") String jobId);

    /** 统计失败率 */
    @Query("SELECT COUNT(e) FROM E2ETestResult e WHERE e.jobId = :jobId AND e.status = 'failed'")
    long countFailedByJobId(@Param("jobId") String jobId);
}
