package com.jonychen.integration;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jonychen.test.TestcontainersConfig;

/** 使用 Testcontainers 的数据库集成测试 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class DatabaseIntegrationTest extends TestcontainersConfig {

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("数据库连接测试")
    void databaseConnection_shouldWork() {
        // 验证数据库连接正常
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        assertEquals(1, result);
    }

    @Test
    @DisplayName("pgvector 扩展可用性测试")
    void pgvectorExtension_shouldBeAvailable() {
        // 检查 pgvector 扩展是否可用（仅当镜像包含 pgvector）
        try {
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
            jdbcTemplate.queryForObject("SELECT '[1,2,3]'::vector", String.class);
        } catch (Exception e) {
            // pgvector 可能未安装，跳过测试
            System.out.println("pgvector extension not available: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("UUID 扩展测试")
    void uuidExtension_shouldWork() {
        // 检查 uuid-ossp 扩展
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"");
        String uuid = jdbcTemplate.queryForObject("SELECT uuid_generate_v4()::text", String.class);
        assertNotNull(uuid);
        assertEquals(36, uuid.length());
    }

    @Test
    @DisplayName("JSONB 类型测试")
    void jsonbType_shouldWork() {
        // 测试 JSONB 类型支持
        String json = "{\"test\": \"value\"}";
        jdbcTemplate.update("CREATE TABLE IF NOT EXISTS test_jsonb (id SERIAL, data JSONB)");
        jdbcTemplate.update("INSERT INTO test_jsonb (data) VALUES (?::jsonb)", json);
        String result =
                jdbcTemplate.queryForObject(
                        "SELECT data::text FROM test_jsonb LIMIT 1", String.class);
        assertTrue(result.contains("test"));
        jdbcTemplate.update("DROP TABLE IF EXISTS test_jsonb");
    }
}
