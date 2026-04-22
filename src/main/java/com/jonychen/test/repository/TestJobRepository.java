package com.jonychen.test.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.jonychen.test.entity.TestJob;

/**
 * 测试任务 Repository
 *
 * @author jonychen
 */
@Repository
public interface TestJobRepository extends JpaRepository<TestJob, String> {

    /** 按测试类型查询 */
    Page<TestJob> findByTestType(String testType, Pageable pageable);

    /** 按状态查询 */
    Page<TestJob> findByStatus(String status, Pageable pageable);

    /** 按测试类型和状态查询 */
    Page<TestJob> findByTestTypeAndStatus(String testType, String status, Pageable pageable);

    /** 按时间范围查询 */
    @Query("SELECT t FROM TestJob t WHERE t.startTime BETWEEN :from AND :to")
    Page<TestJob> findByStartTimeBetween(
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to, Pageable pageable);

    /** 按测试类型和时间范围查询 */
    @Query(
            "SELECT t FROM TestJob t WHERE t.testType = :testType AND t.startTime BETWEEN :from AND :to")
    Page<TestJob> findByTestTypeAndStartTimeBetween(
            @Param("testType") String testType,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    /** 按状态和时间范围查询 */
    @Query("SELECT t FROM TestJob t WHERE t.status = :status AND t.startTime BETWEEN :from AND :to")
    Page<TestJob> findByStatusAndStartTimeBetween(
            @Param("status") String status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    /** 按测试类型、状态和时间范围查询 */
    @Query(
            "SELECT t FROM TestJob t WHERE t.testType = :testType AND t.status = :status AND t.startTime BETWEEN :from AND :to")
    Page<TestJob> findByTestTypeAndStatusAndStartTimeBetween(
            @Param("testType") String testType,
            @Param("status") String status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    /** 统计各状态的测试数量 */
    @Query(
            "SELECT t.status, COUNT(t) FROM TestJob t WHERE t.testType = :testType GROUP BY t.status")
    List<Object[]> countByTestTypeGroupByStatus(@Param("testType") String testType);

    /** 统计总测试数 */
    long countByTestType(String testType);

    /** 统计指定状态的测试数 */
    long countByTestTypeAndStatus(String testType, String status);

    /** 查找用户触发的测试 */
    Page<TestJob> findByTriggeredBy(String triggeredBy, Pageable pageable);

    /** 查找最近的测试任务 */
    Optional<TestJob> findFirstByTestTypeOrderByStartTimeDesc(String testType);

    /** 删除指定时间之前的记录 */
    @Modifying
    @Query("DELETE FROM TestJob t WHERE t.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") LocalDateTime cutoff);

    /** 查询运行中的测试 */
    @Query("SELECT t FROM TestJob t WHERE t.status = 'RUNNING' ORDER BY t.startTime DESC")
    List<TestJob> findRunningJobs();
}
