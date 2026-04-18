-- ================================================================================
-- 增量配置：common.properties（如果已存在则跳过）
-- ================================================================================
-- 使用方式：
--   docker exec -i langchain4j-db mysql -uroot -pREDACTED_ROOT_PASSWORD nacos < infra/nacos/init/02-common-config.sql
-- 或在 Nacos 控制台手动创建配置
-- ================================================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- 插入 common.properties（忽略已存在）
INSERT IGNORE INTO config_info (data_id, group_id, content, md5, gmt_create, gmt_modified, src_user, src_ip, app_name, tenant_id, c_desc, c_use, effect, type, c_schema, encrypted_data_key)
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
    MD5('common-config-v1'),
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
