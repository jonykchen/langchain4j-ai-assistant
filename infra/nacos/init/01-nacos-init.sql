-- ==================== Nacos 数据库初始化 ====================
--
-- 创建 Nacos 所需的数据库表结构

-- 创建配置表
CREATE TABLE IF NOT EXISTS config_info (
    id BIGSERIAL PRIMARY KEY,
    data_id VARCHAR(255) NOT NULL,
    group_id VARCHAR(128),
    content TEXT,
    md5 VARCHAR(32),
    gmt_create TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    gmt_modified TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    src_user VARCHAR(128),
    src_ip VARCHAR(50),
    app_name VARCHAR(128),
    tenant_id VARCHAR(128),
    c_desc VARCHAR(256),
    c_use VARCHAR(64),
    effect VARCHAR(512),
    type VARCHAR(64),
    c_schema TEXT,
    encrypted_data_key TEXT
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_config_info_datagrouptenant ON config_info(data_id, group_id, tenant_id);

-- 创建配置历史表
CREATE TABLE IF NOT EXISTS config_info_aggr (
    id BIGSERIAL PRIMARY KEY,
    data_id VARCHAR(255) NOT NULL,
    group_id VARCHAR(128),
    datum_id VARCHAR(255),
    content TEXT,
    gmt_modified TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    app_name VARCHAR(128),
    tenant_id VARCHAR(128)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_config_info_aggr_datagrouptenantdatum ON config_info_aggr(data_id, group_id, tenant_id, datum_id);

-- 创建配置标签表
CREATE TABLE IF NOT EXISTS config_info_beta (
    id BIGSERIAL PRIMARY KEY,
    data_id VARCHAR(255),
    group_id VARCHAR(128),
    app_name VARCHAR(128),
    content TEXT,
    beta_ips VARCHAR(1024),
    md5 VARCHAR(32),
    gmt_create TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    gmt_modified TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    src_user VARCHAR(128),
    src_ip VARCHAR(50),
    tenant_id VARCHAR(128),
    encrypted_data_key TEXT
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_config_info_beta_datagrouptenant ON config_info_beta(data_id, group_id, tenant_id);

-- 创建配置标签表
CREATE TABLE IF NOT EXISTS config_info_tag (
    id BIGSERIAL PRIMARY KEY,
    data_id VARCHAR(255),
    group_id VARCHAR(128),
    tenant_id VARCHAR(128),
    tag_id VARCHAR(128),
    app_name VARCHAR(128),
    content TEXT,
    md5 VARCHAR(32),
    gmt_create TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    gmt_modified TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    src_user VARCHAR(128),
    src_ip VARCHAR(50)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_config_info_tag_datagrouptenanttag ON config_info_tag(data_id, group_id, tenant_id, tag_id);

-- 创建配置关系表
CREATE TABLE IF NOT EXISTS config_tags_relation (
    id BIGSERIAL,
    tag_name VARCHAR(128),
    tag_id VARCHAR(128),
    data_id VARCHAR(255),
    group_id VARCHAR(128),
    tenant_id VARCHAR(128),
    nid BIGSERIAL,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_config_tags_relation_tagid ON config_tags_relation(tag_id);

-- 创建组容量表
CREATE TABLE IF NOT EXISTS group_capacity (
    id BIGSERIAL PRIMARY KEY,
    group_id VARCHAR(128) NOT NULL,
    quota BIGINT,
    usage BIGINT,
    max_size BIGINT,
    max_aggr_count BIGINT,
    max_aggr_size BIGINT,
    gmt_create TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    gmt_modified TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_group_capacity_groupid ON group_capacity(group_id);

-- 创建租户容量表
CREATE TABLE IF NOT EXISTS tenant_capacity (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    quota BIGINT,
    usage BIGINT,
    max_size BIGINT,
    max_aggr_count BIGINT,
    max_aggr_size BIGINT,
    gmt_create TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    gmt_modified TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_tenant_capacity_tenantid ON tenant_capacity(tenant_id);

-- 创建租户信息表
CREATE TABLE IF NOT EXISTS tenant_info (
    id BIGSERIAL PRIMARY KEY,
    kp BIGINT NOT NULL,
    tenant_id VARCHAR(128),
    tenant_name VARCHAR(128),
    tenant_desc VARCHAR(256),
    create_source VARCHAR(32),
    gmt_create BIGINT,
    gmt_modified BIGINT
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_tenant_info_tenantid ON tenant_info(tenant_id);

-- 创建命名空间表
CREATE TABLE IF NOT EXISTS namespaces (
    id BIGSERIAL PRIMARY KEY,
    namespace_id VARCHAR(128),
    namespace_name VARCHAR(128),
    type INT DEFAULT 0,
    config_count INT DEFAULT 0
);

-- 创建用户表
CREATE TABLE IF NOT EXISTS users (
    username VARCHAR(50) PRIMARY KEY,
    password VARCHAR(500),
    enabled BOOLEAN DEFAULT TRUE
);

-- 创建角色表
CREATE TABLE IF NOT EXISTS roles (
    username VARCHAR(50) REFERENCES users(username),
    role VARCHAR(50),
    PRIMARY KEY (username, role)
);

-- 创建权限表
CREATE TABLE IF NOT EXISTS permissions (
    role VARCHAR(50) NOT NULL,
    resource VARCHAR(128) NOT NULL,
    action VARCHAR(8) NOT NULL,
    PRIMARY KEY (role, resource, action)
);

-- 插入默认用户（密码：nacos，BCrypt加密）
INSERT INTO users (username, password, enabled) VALUES ('nacos', '$2a$10$EuWPZHzz32dJN7jexM34MOeY9Dl0S8c5JYhqTO6Y5a2Y5oX7Y5oX7', TRUE) ON CONFLICT (username) DO NOTHING;
INSERT INTO roles (username, role) VALUES ('nacos', 'ROLE_ADMIN') ON CONFLICT DO NOTHING;

-- 插入默认命名空间
INSERT INTO namespaces (namespace_id, namespace_name, type) VALUES ('dev', '开发环境', 0) ON CONFLICT DO NOTHING;
INSERT INTO namespaces (namespace_id, namespace_name, type) VALUES ('prod', '生产环境', 0) ON CONFLICT DO NOTHING;

-- 授权
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO nacos;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO nacos;
