-- ==================== 用户认证相关数据表 ====================

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(64) PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE,
    nickname VARCHAR(100),
    avatar VARCHAR(500),
    provider VARCHAR(20) NOT NULL,
    provider_id VARCHAR(100),
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP NOT NULL,
    last_login_at TIMESTAMP,
    version BIGINT DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_users_provider ON users(provider, provider_id);
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

-- Token 黑名单（用于登出时使 Token 失效）
CREATE TABLE IF NOT EXISTS token_blacklist (
    id SERIAL PRIMARY KEY,
    token_hash VARCHAR(128) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_blacklist_token ON token_blacklist(token_hash);
CREATE INDEX IF NOT EXISTS idx_blacklist_expires ON token_blacklist(expires_at);
