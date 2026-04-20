package com.jonychen.observability.prompt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Prompt 模板仓库
 *
 * @author jonychen
 */
@Repository
public interface PromptTemplateRepository extends JpaRepository<PromptTemplateEntity, Long> {

    /**
     * 根据名称和版本查找
     */
    Optional<PromptTemplateEntity> findByNameAndVersion(String name, String version);

    /**
     * 查找指定名称的激活版本
     */
    Optional<PromptTemplateEntity> findByNameAndActiveTrue(String name);

    /**
     * 查找指定名称的生产版本
     */
    Optional<PromptTemplateEntity> findByNameAndProductionTrue(String name);

    /**
     * 查找指定名称的最新版本
     */
    @Query("SELECT p FROM PromptTemplateEntity p WHERE p.name = :name ORDER BY p.createdAt DESC LIMIT 1")
    Optional<PromptTemplateEntity> findLatestByName(@Param("name") String name);

    /**
     * 查找指定名称的所有版本（按版本号降序）
     */
    List<PromptTemplateEntity> findByNameOrderByCreatedAtDesc(String name);

    /**
     * 查找所有模板名称（去重）
     */
    @Query("SELECT DISTINCT p.name FROM PromptTemplateEntity p ORDER BY p.name")
    List<String> findAllNames();

    /**
     * 查找所有激活的模板
     */
    List<PromptTemplateEntity> findByActiveTrue();

    /**
     * 查找所有生产环境的模板
     */
    List<PromptTemplateEntity> findByProductionTrue();

    /**
     * 查找启用了 A/B 测试的模板
     */
    @Query("SELECT p FROM PromptTemplateEntity p WHERE p.name = :name AND p.abTestEnabled = true")
    List<PromptTemplateEntity> findABTestTemplates(@Param("name") String name);

    /**
     * 停用指定名称的所有版本
     */
    @Modifying
    @Query("UPDATE PromptTemplateEntity p SET p.active = false WHERE p.name = :name")
    void deactivateAllVersions(@Param("name") String name);

    /**
     * 移除指定名称的生产标记
     */
    @Modifying
    @Query("UPDATE PromptTemplateEntity p SET p.production = false WHERE p.name = :name")
    void removeProductionFlag(@Param("name") String name);

    /**
     * 统计指定名称的版本数量
     */
    long countByName(String name);

    /**
     * 检查名称和版本是否存在
     */
    boolean existsByNameAndVersion(String name, String version);
}
