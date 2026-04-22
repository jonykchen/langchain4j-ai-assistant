package com.jonychen.test.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.jonychen.test.entity.PerformanceTestResult;

/**
 * 性能测试结果 Repository
 *
 * @author jonychen
 */
@Repository
public interface PerformanceTestResultRepository
        extends JpaRepository<PerformanceTestResult, Long> {

    /** 按 jobId 查询所有结果 */
    List<PerformanceTestResult> findByJobId(String jobId);

    /** 按 jobId 分页查询 */
    Page<PerformanceTestResult> findByJobId(String jobId, Pageable pageable);

    /** 按模拟场景查询 */
    Page<PerformanceTestResult> findBySimulation(String simulation, Pageable pageable);

    /** 按 jobId 和模拟场景查询 */
    List<PerformanceTestResult> findByJobIdAndSimulation(String jobId, String simulation);

    /** 计算平均性能指标 */
    @Query(
            "SELECT AVG(p.successRate), AVG(p.avgResponseTime), AVG(p.p95ResponseTime), AVG(p.p99ResponseTime) "
                    + "FROM PerformanceTestResult p WHERE p.jobId = :jobId")
    Object[] avgMetricsByJobId(@Param("jobId") String jobId);

    /** 删除指定 jobId 的所有结果 */
    @Modifying
    void deleteByJobId(String jobId);

    /** 查询成功率低于阈值的记录 */
    @Query(
            "SELECT p FROM PerformanceTestResult p WHERE p.jobId = :jobId AND p.successRate < :threshold")
    List<PerformanceTestResult> findBelowSuccessRate(
            @Param("jobId") String jobId, @Param("threshold") Double threshold);

    /** 查询平均响应时间高于阈值的记录 */
    @Query(
            "SELECT p FROM PerformanceTestResult p WHERE p.jobId = :jobId AND p.avgResponseTime > :threshold")
    List<PerformanceTestResult> findAboveAvgResponseTime(
            @Param("jobId") String jobId, @Param("threshold") Long threshold);
}
