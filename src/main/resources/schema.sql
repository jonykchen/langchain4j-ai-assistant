-- ================================================================================
-- LangChain4j AI Chat 应用 - 数据库 Schema（MySQL 8.0+）
-- ================================================================================
-- 说明：此文件由 Spring Boot 在启动时自动执行（defer-datasource-initialization=true）
-- 实际生产环境建议使用 infra/mysql/init/01-langchain4j-init.sql 初始化
-- ================================================================================

-- 用户表
CREATE TABLE IF NOT EXISTS `users` (
    `id`            VARCHAR(64)     NOT NULL,
    `username`      VARCHAR(100)    NOT NULL,
    `email`         VARCHAR(255)    DEFAULT NULL,
    `nickname`      VARCHAR(100)    DEFAULT NULL,
    `avatar`        VARCHAR(500)    DEFAULT NULL,
    `provider`      VARCHAR(20)     NOT NULL,
    `provider_id`   VARCHAR(100)    DEFAULT NULL,
    `role`          VARCHAR(20)     NOT NULL DEFAULT 'USER',
    `created_at`    DATETIME        NOT NULL,
    `last_login_at` DATETIME        DEFAULT NULL,
    `version`       BIGINT          DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`),
    KEY `idx_provider` (`provider`, `provider_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Token 黑名单表
CREATE TABLE IF NOT EXISTS `token_blacklist` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT,
    `token_hash`    VARCHAR(128)    NOT NULL,
    `user_id`       VARCHAR(64)     NOT NULL,
    `expires_at`    DATETIME        NOT NULL,
    `created_at`    DATETIME        DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_token_hash` (`token_hash`),
    KEY `idx_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
