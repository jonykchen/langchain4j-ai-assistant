-- ================================================================================
-- LangChain4j AI Chat 应用 - 数据库初始化脚本
-- ================================================================================
-- 数据库类型：MySQL 8.0+
-- 字符集：utf8mb4（支持 emoji 和特殊字符）
-- 排序规则：utf8mb4_unicode_ci（精确的 Unicode 排序）
-- 创建时间：2026-04-18
-- ================================================================================

-- ==================== 创建数据库 ================================

CREATE DATABASE IF NOT EXISTS `langchain4j`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE `langchain4j`;

-- ==================== 用户认证相关表 ==========================

-- ------------------------------------------------------------------------------
-- 用户表 (users)
-- 存储所有通过 OAuth（GitHub/GitLab）登录的用户信息
-- ------------------------------------------------------------------------------
-- 字段说明：
--   id           - 用户唯一标识（UUID），主键
--   username     - 用户名（唯一），来自 OAuth 提供商或自动生成
--   email        - 用户邮箱（唯一，可为空，部分 OAuth 不返回邮箱）
--   nickname     - 昵称/显示名，来自 OAuth 提供商
--   avatar       - 头像 URL，来自 OAuth 提供商
--   provider     - 认证提供商：GITHUB / GITLAB
--   provider_id  - OAuth 提供商侧的用户 ID，与 provider 组合唯一
--   role         - 用户角色：USER（普通用户）/ ADMIN（管理员）
--                  首个注册用户自动成为 ADMIN
--   created_at   - 注册时间
--   last_login_at - 最后登录时间，每次 OAuth 登录时更新
--   version      - 乐观锁版本号，防止并发更新冲突（JPA @Version）
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `users` (
    `id`            VARCHAR(64)     NOT NULL                    COMMENT '用户唯一标识（UUID）',
    `username`      VARCHAR(100)    NOT NULL                    COMMENT '用户名（唯一）',
    `email`         VARCHAR(255)    DEFAULT NULL                COMMENT '邮箱（唯一，可为空）',
    `nickname`      VARCHAR(100)    DEFAULT NULL                COMMENT '昵称/显示名',
    `avatar`        VARCHAR(500)    DEFAULT NULL                COMMENT '头像 URL',
    `provider`      VARCHAR(20)     NOT NULL                    COMMENT '认证提供商：GITHUB / GITLAB',
    `provider_id`   VARCHAR(100)    DEFAULT NULL                COMMENT 'OAuth 提供商用户 ID',
    `role`          VARCHAR(20)     NOT NULL DEFAULT 'USER'     COMMENT '用户角色：USER / ADMIN',
    `created_at`    DATETIME        NOT NULL                    COMMENT '注册时间',
    `last_login_at` DATETIME        DEFAULT NULL                COMMENT '最后登录时间',
    `version`       BIGINT          DEFAULT 0                   COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`),
    KEY `idx_provider` (`provider`, `provider_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ------------------------------------------------------------------------------
-- Token 黑名单表 (token_blacklist)
-- 用于用户登出时将 JWT 加入黑名单，使 Token 提前失效
-- ------------------------------------------------------------------------------
-- 字段说明：
--   id           - 自增主键
--   token_hash   - JWT 的 SHA-256 哈希值（不存储原始 Token，安全考虑）
--   user_id      - 关联用户 ID
--   expires_at   - Token 原始过期时间，用于定期清理已过期的黑名单记录
--   created_at   - 加入黑名单的时间
-- ------------------------------------------------------------------------------
-- 清理策略：
--   可通过定时任务删除 expires_at < NOW() 的记录
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `token_blacklist` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT      COMMENT '自增主键',
    `token_hash`    VARCHAR(128)    NOT NULL                    COMMENT 'Token 的 SHA-256 哈希值',
    `user_id`       VARCHAR(64)     NOT NULL                    COMMENT '关联用户 ID',
    `expires_at`    DATETIME        NOT NULL                    COMMENT 'Token 原始过期时间',
    `created_at`    DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '加入黑名单时间',
    PRIMARY KEY (`id`),
    KEY `idx_token_hash` (`token_hash`),
    KEY `idx_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Token 黑名单表';
