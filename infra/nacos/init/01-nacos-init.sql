-- ================================================================================
-- Nacos 配置中心 - 数据库初始化脚本
-- ================================================================================
-- 数据库类型：MySQL 8.0+
-- 字符集：utf8mb4（完整支持 Unicode，包括 emoji）
-- 排序规则：utf8mb4_unicode_ci
-- ================================================================================

-- 设置客户端字符集，确保中文注释正确存储
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- 创建 Nacos 数据库
CREATE DATABASE IF NOT EXISTS `nacos`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE `nacos`;

/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/******************************************/
/*   表名称 = config_info                  */
/******************************************/
CREATE TABLE `config_info` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
  `data_id` varchar(255) NOT NULL COMMENT 'data_id',
  `group_id` varchar(128) DEFAULT NULL COMMENT 'group_id',
  `content` longtext NOT NULL COMMENT 'content',
  `md5` varchar(32) DEFAULT NULL COMMENT 'md5',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
  `src_user` text COMMENT 'source user',
  `src_ip` varchar(50) DEFAULT NULL COMMENT 'source ip',
  `app_name` varchar(128) DEFAULT NULL COMMENT 'app_name',
  `tenant_id` varchar(128) DEFAULT '' COMMENT '租户字段',
  `c_desc` varchar(256) DEFAULT NULL COMMENT 'configuration description',
  `c_use` varchar(64) DEFAULT NULL COMMENT 'configuration usage',
  `effect` varchar(64) DEFAULT NULL COMMENT '配置生效的描述',
  `type` varchar(64) DEFAULT NULL COMMENT '配置的类型',
  `c_schema` text COMMENT '配置的模式',
  `encrypted_data_key` text NOT NULL COMMENT '密钥',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_configinfo_datagrouptenant` (`data_id`,`group_id`,`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='config_info';

/******************************************/
/*   表名称 = config_info_aggr             */
/******************************************/
CREATE TABLE `config_info_aggr` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
  `data_id` varchar(255) NOT NULL COMMENT 'data_id',
  `group_id` varchar(128) NOT NULL COMMENT 'group_id',
  `datum_id` varchar(255) NOT NULL COMMENT 'datum_id',
  `content` longtext NOT NULL COMMENT '内容',
  `gmt_modified` datetime NOT NULL COMMENT '修改时间',
  `app_name` varchar(128) DEFAULT NULL COMMENT 'app_name',
  `tenant_id` varchar(128) DEFAULT '' COMMENT '租户字段',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_configinfoaggr_datagrouptenantdatum` (`data_id`,`group_id`,`tenant_id`,`datum_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='增加租户字段';


/******************************************/
/*   表名称 = config_info_beta             */
/******************************************/
CREATE TABLE `config_info_beta` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
  `data_id` varchar(255) NOT NULL COMMENT 'data_id',
  `group_id` varchar(128) NOT NULL COMMENT 'group_id',
  `app_name` varchar(128) DEFAULT NULL COMMENT 'app_name',
  `content` longtext NOT NULL COMMENT 'content',
  `beta_ips` varchar(1024) DEFAULT NULL COMMENT 'betaIps',
  `md5` varchar(32) DEFAULT NULL COMMENT 'md5',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
  `src_user` text COMMENT 'source user',
  `src_ip` varchar(50) DEFAULT NULL COMMENT 'source ip',
  `tenant_id` varchar(128) DEFAULT '' COMMENT '租户字段',
  `encrypted_data_key` text NOT NULL COMMENT '密钥',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_configinfobeta_datagrouptenant` (`data_id`,`group_id`,`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='config_info_beta';

/******************************************/
/*   表名称 = config_info_tag              */
/******************************************/
CREATE TABLE `config_info_tag` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
  `data_id` varchar(255) NOT NULL COMMENT 'data_id',
  `group_id` varchar(128) NOT NULL COMMENT 'group_id',
  `tenant_id` varchar(128) DEFAULT '' COMMENT 'tenant_id',
  `tag_id` varchar(128) NOT NULL COMMENT 'tag_id',
  `app_name` varchar(128) DEFAULT NULL COMMENT 'app_name',
  `content` longtext NOT NULL COMMENT 'content',
  `md5` varchar(32) DEFAULT NULL COMMENT 'md5',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
  `src_user` text COMMENT 'source user',
  `src_ip` varchar(50) DEFAULT NULL COMMENT 'source ip',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_configinfotag_datagrouptenanttag` (`data_id`,`group_id`,`tenant_id`,`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='config_info_tag';

/******************************************/
/*   表名称 = config_tags_relation         */
/******************************************/
CREATE TABLE `config_tags_relation` (
  `id` bigint(20) NOT NULL COMMENT 'id',
  `tag_name` varchar(128) NOT NULL COMMENT 'tag_name',
  `tag_type` varchar(64) DEFAULT NULL COMMENT 'tag_type',
  `data_id` varchar(255) NOT NULL COMMENT 'data_id',
  `group_id` varchar(128) NOT NULL COMMENT 'group_id',
  `tenant_id` varchar(128) DEFAULT '' COMMENT 'tenant_id',
  `nid` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'nid, 自增长标识',
  PRIMARY KEY (`nid`),
  UNIQUE KEY `uk_configtagrelation_configidtag` (`id`,`tag_name`,`tag_type`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='config_tag_relation';

/******************************************/
/*   表名称 = group_capacity               */
/******************************************/
CREATE TABLE `group_capacity` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `group_id` varchar(128) NOT NULL DEFAULT '' COMMENT 'Group ID，空字符表示整个集群',
  `quota` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '配额，0表示使用默认值',
  `usage` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '使用量',
  `max_size` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '单个配置大小上限，单位为字节，0表示使用默认值',
  `max_aggr_count` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '聚合子配置最大个数，0表示使用默认值',
  `max_aggr_size` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '单个聚合数据的子配置大小上限，单位为字节，0表示使用默认值',
  `max_history_count` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '最大变更历史数量',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_id` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='集群、各Group容量信息表';

/******************************************/
/*   表名称 = his_config_info              */
/******************************************/
CREATE TABLE `his_config_info` (
  `id` bigint(20) unsigned NOT NULL COMMENT 'id',
  `nid` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'nid, 自增标识',
  `data_id` varchar(255) NOT NULL COMMENT 'data_id',
  `group_id` varchar(128) NOT NULL COMMENT 'group_id',
  `app_name` varchar(128) DEFAULT NULL COMMENT 'app_name',
  `content` longtext NOT NULL COMMENT 'content',
  `md5` varchar(32) DEFAULT NULL COMMENT 'md5',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
  `src_user` text COMMENT 'source user',
  `src_ip` varchar(50) DEFAULT NULL COMMENT 'source ip',
  `op_type` char(10) DEFAULT NULL COMMENT 'operation type',
  `tenant_id` varchar(128) DEFAULT '' COMMENT '租户字段',
  `encrypted_data_key` text NOT NULL COMMENT '密钥',
  PRIMARY KEY (`nid`),
  KEY `idx_gmt_create` (`gmt_create`),
  KEY `idx_gmt_modified` (`gmt_modified`),
  KEY `idx_did` (`data_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='多租户改造';


/******************************************/
/*   表名称 = tenant_capacity              */
/******************************************/
CREATE TABLE `tenant_capacity` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_id` varchar(128) NOT NULL DEFAULT '' COMMENT 'Tenant ID',
  `quota` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '配额，0表示使用默认值',
  `usage` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '使用量',
  `max_size` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '单个配置大小上限，单位为字节，0表示使用默认值',
  `max_aggr_count` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '聚合子配置最大个数',
  `max_aggr_size` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '单个聚合数据的子配置大小上限，单位为字节，0表示使用默认值',
  `max_history_count` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '最大变更历史数量',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户容量信息表';


CREATE TABLE `tenant_info` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
  `kp` varchar(128) NOT NULL COMMENT 'kp',
  `tenant_id` varchar(128) default '' COMMENT 'tenant_id',
  `tenant_name` varchar(128) default '' COMMENT 'tenant_name',
  `tenant_desc` varchar(256) DEFAULT NULL COMMENT 'tenant_desc',
  `create_source` varchar(32) DEFAULT NULL COMMENT 'create_source',
  `gmt_create` bigint(20) NOT NULL COMMENT '创建时间',
  `gmt_modified` bigint(20) NOT NULL COMMENT '修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_info_kptenantid` (`kp`,`tenant_id`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='tenant_info';

CREATE TABLE `users` (
    `username` varchar(50) NOT NULL PRIMARY KEY COMMENT '用户名',
    `password` varchar(500) NOT NULL COMMENT '密码',
    `enabled` boolean NOT NULL COMMENT '是否启用'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

CREATE TABLE `roles` (
    `username` varchar(50) NOT NULL COMMENT '用户名',
    `role` varchar(50) NOT NULL COMMENT '角色',
    UNIQUE INDEX `idx_user_role` (`username` ASC, `role` ASC) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

CREATE TABLE `permissions` (
    `role` varchar(50) NOT NULL COMMENT '角色',
    `resource` varchar(128) NOT NULL COMMENT '资源',
    `action` varchar(8) NOT NULL COMMENT '操作',
    UNIQUE INDEX `uk_role_permission` (`role`,`resource`,`action`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限表';

INSERT INTO users (username, password, enabled) VALUES ('nacos', '$2a$10$EuWPZHzz32dJN7jexM34MOeYirDdFAZm2kuWj7VEOJhhZkDrxfvUu', TRUE);

INSERT INTO roles (username, role) VALUES ('nacos', 'ROLE_ADMIN');

-- ================================================================================
-- 重新设置字符集，确保后续 INSERT 语句正确处理中文
-- ================================================================================
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- ================================================================================
-- 导入预置配置：common.properties 跨应用共享配置
-- ================================================================================
INSERT INTO config_info (data_id, group_id, content, md5, gmt_create, gmt_modified, src_user, src_ip, app_name, tenant_id, c_desc, c_use, effect, type, c_schema, encrypted_data_key)
VALUES (
    'common.properties',
    'DEFAULT_GROUP',
    '# ==================== 共享配置（所有应用通用） ====================
#
# Data ID: common.properties
# Group: DEFAULT_GROUP
#
# 此配置为所有微服务共享的通用配置，适合放置跨应用的公共参数
# 应用特有配置放在 langchain4j-chat.properties 中
#

# ==================== Jackson 序列化配置 ====================
spring.jackson.default-property-inclusion=non_null
spring.jackson.date-format=yyyy-MM-dd HH:mm:ss
spring.jackson.time-zone=Asia/Shanghai

# ==================== HTTP 客户端基础配置 ====================
spring.http.client.connect-timeout=10s
spring.http.client.read-timeout=60s

# ==================== 文件上传限制 ====================
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB',
    MD5('# ==================== 共享配置（所有应用通用） ====================
#
# Data ID: common.properties
# Group: DEFAULT_GROUP
#
# 此配置为所有微服务共享的通用配置，适合放置跨应用的公共参数
# 应用特有配置放在 langchain4j-chat.properties 中
#

# ==================== Jackson 序列化配置 ====================
spring.jackson.default-property-inclusion=non_null
spring.jackson.date-format=yyyy-MM-dd HH:mm:ss
spring.jackson.time-zone=Asia/Shanghai

# ==================== HTTP 客户端基础配置 ====================
spring.http.client.connect-timeout=10s
spring.http.client.read-timeout=60s

# ==================== 文件上传限制 ====================
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB'),
    NOW(),
    NOW(),
    'nacos',
    '127.0.0.1',
    'langchain4j-chat',
    '',
    '跨应用共享配置',
    'shared',
    'all',
    'properties',
    '',
    ''
);

-- ================================================================================
-- 导入预置配置：langchain4j-chat.properties 公共配置（所有环境共享）
-- ================================================================================
INSERT INTO config_info (data_id, group_id, content, md5, gmt_create, gmt_modified, src_user, src_ip, app_name, tenant_id, c_desc, c_use, effect, type, c_schema, encrypted_data_key)
VALUES (
    'langchain4j-chat.properties',
    'DEFAULT_GROUP',
    '# ==================== 公共配置（所有环境共享） ====================
#
# Data ID: langchain4j-chat.properties
# Group: DEFAULT_GROUP
#
# 此配置为所有环境共享的基础配置，环境特定配置在 langchain4j-chat-{profile}.properties 中
#

# ==================== 数据库配置 ====================
# 应用业务数据库使用 PostgreSQL（支持 pgvector 向量检索和 Row Level Security）
# Nacos 元数据仍使用 MySQL，与业务数据隔离
spring.datasource.url=${DATABASE_URL:jdbc:postgresql://localhost:5432/langchain4j?currentSchema=public}
spring.datasource.username=${DATABASE_USERNAME:langchain4j}
spring.datasource.password=${DATABASE_PASSWORD:REDACTED_DB_PASSWORD}
spring.datasource.driver-class-name=org.postgresql.Driver

# Hikari 连接池配置
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.idle-timeout=300000
spring.datasource.hikari.connection-timeout=20000
spring.datasource.hikari.max-lifetime=1200000

# ==================== JPA 配置 ====================
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=${JPA_SHOW_SQL:false}
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.defer-datasource-initialization=true
# PostgreSQL 方言
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect

# ==================== Redis 基础配置 ====================
spring.data.redis.database=0

# ==================== 安全配置 ====================
# Spring Security 基础用户（用于开发测试）
spring.security.user.name=admin
spring.security.user.password=admin
spring.security.user.roles=ADMIN

# ==================== JWT 认证配置 ====================
# JWT 密钥（通过环境变量注入，要求至少 32 字符）
jwt.secret=${JWT_SECRET:REDACTED_JWT_SECRET}
# 访问令牌过期时间（秒）- 默认 1 小时
jwt.access-token-expiration=${JWT_ACCESS_TOKEN_EXPIRATION:3600}
# 刷新令牌过期时间（秒）- 默认 7 天
jwt.refresh-token-expiration=${JWT_REFRESH_TOKEN_EXPIRATION:604800}

# ==================== OAuth 配置 ====================
# GitHub OAuth（密钥通过环境变量注入）
oauth.github.client-id=${GITHUB_CLIENT_ID:}
oauth.github.client-secret=${GITHUB_CLIENT_SECRET:}

# GitLab OAuth
oauth.gitlab.client-id=${GITLAB_CLIENT_ID:}
oauth.gitlab.client-secret=${GITLAB_CLIENT_SECRET:}
oauth.gitlab.url=${GITLAB_URL:https://gitlab.com}

# HTTP 代理（访问 GitHub/GitLab API）
oauth.proxy.host=${OAUTH_PROXY_HOST:127.0.0.1}
oauth.proxy.port=${OAUTH_PROXY_PORT:7890}

# ==================== 多模型基础配置 ====================
# API Key 通过环境变量注入，这里仅配置连接信息和模型参数

# 主模型：阿里云 DashScope
model.providers.dashscope.base-url=https://dashscope.aliyuncs.com/compatible-mode/v1
model.providers.dashscope.api-key=${DASHSCOPE_API_KEY:}
model.providers.dashscope.model-name=qwen-plus
model.providers.dashscope.weight=50
model.providers.dashscope.priority=1
model.providers.dashscope.enabled=true

# 备用模型 1：智谱 GLM
model.providers.zhipu.base-url=https://open.bigmodel.cn/api/paas/v4
model.providers.zhipu.api-key=${ZHIPU_API_KEY:}
model.providers.zhipu.model-name=glm-4-flash
model.providers.zhipu.weight=20
model.providers.zhipu.priority=2
model.providers.zhipu.enabled=true

# 备用模型 2：DeepSeek
model.providers.deepseek.base-url=https://api.deepseek.com/v1
model.providers.deepseek.api-key=${DEEPSEEK_API_KEY:}
model.providers.deepseek.model-name=deepseek-chat
model.providers.deepseek.weight=15
model.providers.deepseek.priority=3
model.providers.deepseek.enabled=true

# 备用模型 3：硅基流动
model.providers.siliconflow.base-url=https://api.siliconflow.cn/v1
model.providers.siliconflow.api-key=${SILICONFLOW_API_KEY:}
model.providers.siliconflow.model-name=Qwen/Qwen2.5-7B-Instruct
model.providers.siliconflow.weight=10
model.providers.siliconflow.priority=4
model.providers.siliconflow.enabled=true

# 备用模型 4：本地 Ollama（离线兜底）
model.providers.ollama.base-url=http://localhost:11434/v1
model.providers.ollama.api-key=ollama
model.providers.ollama.model-name=qwen2.5:7b
model.providers.ollama.weight=5
model.providers.ollama.priority=5
model.providers.ollama.enabled=false

# ==================== Resilience4j 基础配置 ====================

# 熔断器基础配置
resilience4j.circuitbreaker.instances.chat.slidingWindowType=COUNT_BASED
resilience4j.circuitbreaker.instances.chat.slidingWindowSize=10
resilience4j.circuitbreaker.instances.chat.failureRateThreshold=50
resilience4j.circuitbreaker.instances.chat.slowCallRateThreshold=50
resilience4j.circuitbreaker.instances.chat.registerHealthIndicator=true

# 重试基础配置
resilience4j.retry.instances.chat.maxAttempts=2
resilience4j.retry.instances.chat.waitDuration=1s
resilience4j.retry.instances.chat.enableExponentialBackoff=true
resilience4j.retry.instances.chat.exponentialBackoffMultiplier=2

# 超时基础配置
resilience4j.timelimiter.instances.chat.timeoutDuration=60s
resilience4j.timelimiter.instances.chat.cancelRunningFuture=true

# ==================== 分布式限流基础配置 ====================

rate.limit.chat.limit=100
rate.limit.chat.period=60
rate.limit.chatStream.limit=150
rate.limit.chatStream.period=60

# ==================== 监控配置 ====================

management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.endpoint.health.show-details=always
management.tracing.sampling.probability=1.0
management.metrics.tags.application=langchain4j-chat
management.prometheus.metrics.export.enabled=true',
    MD5('placeholder_common'),
    NOW(),
    NOW(),
    'nacos',
    '127.0.0.1',
    'langchain4j-chat',
    '',
    'LangChain4j Chat 公共配置',
    'shared',
    'all',
    'properties',
    '',
    ''
);

-- ================================================================================
-- 导入预置配置：langchain4j-chat-dev.properties 开发环境配置
-- ================================================================================
INSERT INTO config_info (data_id, group_id, content, md5, gmt_create, gmt_modified, src_user, src_ip, app_name, tenant_id, c_desc, c_use, effect, type, c_schema, encrypted_data_key)
VALUES (
    'langchain4j-chat-dev.properties',
    'DEFAULT_GROUP',
    '# ==================== 开发环境特定配置 ====================
#
# Data ID: langchain4j-chat-dev.properties
# Group: DEFAULT_GROUP
#
# 此配置覆盖公共配置中的开发环境特定值
#

# ==================== Redis 配置（开发环境） ====================

spring.data.redis.host=${REDIS_HOST:localhost}
spring.data.redis.port=${REDIS_PORT:6379}
spring.data.redis.password=${REDIS_PASSWORD:}
spring.data.redis.timeout=10s
spring.data.redis.lettuce.pool.max-active=8
spring.data.redis.lettuce.pool.max-idle=8
spring.data.redis.lettuce.pool.min-idle=0

# ==================== 日志配置（开发环境详细日志） ====================

logging.level.dev.langchain4j=${LOG_LEVEL_LANGCHAIN4J:DEBUG}
logging.level.com.jonychen=DEBUG
logging.level.io.github.resilience4j=DEBUG
logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n

# ==================== 开发工具配置 ====================

spring.devtools.restart.enabled=true
spring.webresources.cache.period=0

# ==================== Resilience4j 配置（开发环境宽松） ====================

# 限流器配置（本地兜底，分布式使用 Redis）
resilience4j.ratelimiters.instances.chat.limitForPeriod=100
resilience4j.ratelimiters.instances.chat.limitRefreshPeriod=1m
resilience4j.ratelimiters.instances.chat.timeoutDuration=0
resilience4j.ratelimiters.instances.chat.registerHealthIndicator=true

resilience4j.ratelimiters.instances.chatStream.limitForPeriod=150
resilience4j.ratelimiters.instances.chatStream.limitRefreshPeriod=1m
resilience4j.ratelimiters.instances.chatStream.timeoutDuration=0
resilience4j.ratelimiters.instances.chatStream.registerHealthIndicator=true

# 熔断器配置（开发环境宽松）
resilience4j.circuitbreaker.instances.chat.slowCallDurationThreshold=30s
resilience4j.circuitbreaker.instances.chat.waitDurationInOpenState=10s
resilience4j.circuitbreaker.instances.chat.permittedNumberOfCallsInHalfOpenState=3

# ==================== 分布式限流配置（开发环境宽松） ====================

rate.limit.chat.limit=100
rate.limit.chat.period=60
rate.limit.chatStream.limit=150
rate.limit.chatStream.period=60

# ==================== 监控配置（开发环境全部暴露） ====================

management.endpoints.web.exposure.include=*',
    MD5('placeholder_dev'),
    NOW(),
    NOW(),
    'nacos',
    '127.0.0.1',
    'langchain4j-chat',
    '',
    'LangChain4j Chat 开发环境配置',
    'dev',
    'dev',
    'properties',
    '',
    ''
);

-- ================================================================================
-- 导入预置配置：langchain4j-chat-prod.properties 生产环境配置
-- ================================================================================
INSERT INTO config_info (data_id, group_id, content, md5, gmt_create, gmt_modified, src_user, src_ip, app_name, tenant_id, c_desc, c_use, effect, type, c_schema, encrypted_data_key)
VALUES (
    'langchain4j-chat-prod.properties',
    'DEFAULT_GROUP',
    '# ==================== 生产环境特定配置 ====================
#
# Data ID: langchain4j-chat-prod.properties
# Group: DEFAULT_GROUP
#
# 此配置覆盖公共配置中的生产环境特定值
#

# ==================== Redis 配置（生产环境） ====================

spring.data.redis.host=${REDIS_HOST:redis}
spring.data.redis.port=${REDIS_PORT:6379}
spring.data.redis.password=${REDIS_PASSWORD:}
spring.data.redis.timeout=5s
spring.data.redis.lettuce.pool.max-active=16
spring.data.redis.lettuce.pool.max-idle=8
spring.data.redis.lettuce.pool.min-idle=2
spring.data.redis.lettuce.pool.max-wait=5s

# ==================== 日志配置（生产环境精简） ====================

logging.level.dev.langchain4j=INFO
logging.level.com.jonychen=INFO
logging.level.io.github.resilience4j=WARN

logging.file.name=logs/application.log
logging.logback.rollingpolicy.max-file-size=10MB
logging.logback.rollingpolicy.max-history=30

# ==================== 开发工具配置（生产环境禁用） ====================

spring.devtools.restart.enabled=false

# ==================== Resilience4j 配置（生产环境严格） ====================

# 限流器配置
resilience4j.ratelimiters.instances.chat.limitForPeriod=20
resilience4j.ratelimiters.instances.chat.limitRefreshPeriod=1m
resilience4j.ratelimiters.instances.chat.timeoutDuration=5s
resilience4j.ratelimiters.instances.chat.registerHealthIndicator=true

resilience4j.ratelimiters.instances.chatStream.limitForPeriod=30
resilience4j.ratelimiters.instances.chatStream.limitRefreshPeriod=1m
resilience4j.ratelimiters.instances.chatStream.timeoutDuration=5s
resilience4j.ratelimiters.instances.chatStream.registerHealthIndicator=true

# 熔断器配置（生产环境严格）
resilience4j.circuitbreaker.instances.chat.slidingWindowSize=20
resilience4j.circuitbreaker.instances.chat.failureRateThreshold=30
resilience4j.circuitbreaker.instances.chat.slowCallRateThreshold=30
resilience4j.circuitbreaker.instances.chat.slowCallDurationThreshold=10s
resilience4j.circuitbreaker.instances.chat.waitDurationInOpenState=30s
resilience4j.circuitbreaker.instances.chat.permittedNumberOfCallsInHalfOpenState=5

# ==================== 分布式限流配置（生产环境严格） ====================

rate.limit.chat.limit=20
rate.limit.chat.period=60
rate.limit.chatStream.limit=30
rate.limit.chatStream.period=60

# ==================== 监控配置（生产环境精简） ====================

management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.endpoint.health.show-details=when-authorized
management.tracing.sampling.probability=${TRACING_SAMPLING_PROBABILITY:0.1}

# ==================== 性能配置 ====================

spring.http.client.connect-timeout=10s
spring.http.client.read-timeout=60s
spring.jackson.default-property-inclusion=non_null
spring.jackson.date-format=yyyy-MM-dd HH:mm:ss
spring.jackson.time-zone=Asia/Shanghai',
    MD5('placeholder_prod'),
    NOW(),
    NOW(),
    'nacos',
    '127.0.0.1',
    'langchain4j-chat',
    '',
    'LangChain4j Chat 生产环境配置',
    'prod',
    'prod',
    'properties',
    '',
    ''
);
