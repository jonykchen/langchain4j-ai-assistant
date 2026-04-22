package com.jonychen.test.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.jonychen.test.entity.AIModelTestResultEntity;

/**
 * AI 模型测试结果 Repository
 *
 * @author jonychen
 */
@Repository
public interface AIModelTestResultRepository extends JpaRepository<AIModelTestResultEntity, Long> {

    /** 按 jobId 查询所有结果 */
    List<AIModelTestResultEntity> findByJobId(String jobId);

    /** 按 jobId 分页查询 */
    Page<AIModelTestResultEntity> findByJobId(String jobId, Pageable pageable);

    /** 按分类查询 */
    Page<AIModelTestResultEntity> findByCategory(String category, Pageable pageable);

    /** 按是否通过查询 */
    Page<AIModelTestResultEntity> findByPassed(Boolean passed, Pageable pageable);

    /** 按 jobId 和分类查询 */
    List<AIModelTestResultEntity> findByJobIdAndCategory(String jobId, String category);

    /** 按 jobId 和是否通过查询 */
    List<AIModelTestResultEntity> findByJobIdAndPassed(String jobId, Boolean passed);

    /** 按分类和是否通过查询 */
    Page<AIModelTestResultEntity> findByCategoryAndPassed(
            String category, Boolean passed, Pageable pageable);

    /** 统计 jobId 下各分类的数量 */
    @Query(
            "SELECT a.category, COUNT(a) FROM AIModelTestResultEntity a WHERE a.jobId = :jobId GROUP BY a.category")
    List<Object[]> countByJobIdGroupByCategory(@Param("jobId") String jobId);

    /** 计算 jobId 的平均得分 */
    @Query("SELECT AVG(a.score) FROM AIModelTestResultEntity a WHERE a.jobId = :jobId")
    Double avgScoreByJobId(@Param("jobId") String jobId);

    /** 计算分类的平均得分 */
    @Query(
            "SELECT AVG(a.score) FROM AIModelTestResultEntity a WHERE a.jobId = :jobId AND a.category = :category")
    Double avgScoreByJobIdAndCategory(
            @Param("jobId") String jobId, @Param("category") String category);

    /** 统计通过数 */
    @Query(
            "SELECT COUNT(a) FROM AIModelTestResultEntity a WHERE a.jobId = :jobId AND a.passed = true")
    long countPassedByJobId(@Param("jobId") String jobId);

    /** 统计失败数 */
    @Query(
            "SELECT COUNT(a) FROM AIModelTestResultEntity a WHERE a.jobId = :jobId AND a.passed = false")
    long countFailedByJobId(@Param("jobId") String jobId);

    /** 删除指定 jobId 的所有结果 */
    @Modifying
    void deleteByJobId(String jobId);

    /** 根据测试用例ID查询最新结果 */
    @Query(
            "SELECT a FROM AIModelTestResultEntity a WHERE a.testCaseId = :testCaseId ORDER BY a.createdAt DESC LIMIT 1")
    AIModelTestResultEntity findLatestByTestCaseId(@Param("testCaseId") String testCaseId);

    /** 查询得分低于阈值的记录 */
    @Query(
            "SELECT a FROM AIModelTestResultEntity a WHERE a.jobId = :jobId AND a.score < :threshold")
    List<AIModelTestResultEntity> findBelowScore(
            @Param("jobId") String jobId, @Param("threshold") Double threshold);
}
