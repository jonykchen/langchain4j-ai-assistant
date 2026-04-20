package com.jonychen.test;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Testcontainers 测试配置基类
 *
 * 使用 Testcontainers 启动 PostgreSQL 容器进行集成测试
 */
@Testcontainers
public abstract class TestcontainersConfig {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("langchain4j_test")
            .withUsername("test")
            .withPassword("test");

    static {
        // 设置测试数据库连接属性
        System.setProperty("spring.datasource.url", postgres.getJdbcUrl());
        System.setProperty("spring.datasource.username", postgres.getUsername());
        System.setProperty("spring.datasource.password", postgres.getPassword());
    }
}
