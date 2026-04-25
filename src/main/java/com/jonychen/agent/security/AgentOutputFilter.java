package com.jonychen.agent.security;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Agent 输出过滤器
 *
 * <p>对 Agent 输出进行敏感数据脱敏，防止泄露 API Key、密码等信息。 核心职责：
 *
 * <ul>
 *   <li>API Key 脱敏（匹配常见前缀如 sk-、key- 等）
 *   <li>密码字段脱敏
 *   <li>IP 地址部分脱敏（可选）
 *   <li>Map/List 递归脱敏（结构化数据中的敏感字段）
 * </ul>
 *
 * @author jonychen
 */
@Component
public class AgentOutputFilter {

    private static final Logger log = LoggerFactory.getLogger(AgentOutputFilter.class);

    /** API Key 模式（匹配 sk-xxx、key-xxx 等常见前缀） */
    private static final List<Pattern> API_KEY_PATTERNS =
            List.of(
                    Pattern.compile("(sk-[a-zA-Z0-9]{20,})"),
                    Pattern.compile("(key-[a-zA-Z0-9]{16,})"),
                    Pattern.compile("(api[_-]?key[\"'\\s:=]+)([a-zA-Z0-9]{16,})"),
                    Pattern.compile("(token[\"'\\s:=]+)([a-zA-Z0-9\\-_.]{20,})"));

    /** 密码字段模式 */
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile(
                    "(password|passwd|pwd)[\"'\\s:=]+([\\S]{4,})", Pattern.CASE_INSENSITIVE);

    /** 数据库连接串模式 */
    private static final Pattern DB_URL_PATTERN =
            Pattern.compile("(jdbc:\\w+://[^\\s]+/[^\\s?]+[?]?[^\\s]*password=)([^&\\s]+)");

    /** 结构化数据中需要脱敏的字段名（小写匹配） */
    private static final List<String> SENSITIVE_FIELD_NAMES =
            List.of(
                    "password",
                    "passwd",
                    "pwd",
                    "secret",
                    "token",
                    "api_key",
                    "apikey",
                    "access_token",
                    "refresh_token",
                    "private_key",
                    "credential",
                    "auth_code",
                    "authorization",
                    "session_id",
                    "sessionid",
                    "credit_card",
                    "card_number",
                    "cvv",
                    "ssn",
                    "social_security");

    /** 脱敏后的替换值 */
    private static final String MASKED_VALUE = "****";

    /**
     * 过滤 Agent 输出中的敏感数据
     *
     * <p>对输出内容进行脱敏处理，将敏感信息替换为遮掩字符。
     *
     * @param output 原始输出
     * @return 脱敏后的输出
     */
    public String filterResult(String output) {
        if (output == null || output.isEmpty()) {
            return output;
        }

        String filtered = output;

        // API Key 脱敏
        for (Pattern pattern : API_KEY_PATTERNS) {
            filtered =
                    pattern.matcher(filtered)
                            .replaceAll(
                                    mr ->
                                            mr.group(1) != null
                                                    ? mr.group(1)
                                                                    .substring(
                                                                            0,
                                                                            Math.min(
                                                                                    6,
                                                                                    mr.group(1)
                                                                                            .length()))
                                                            + "***REDACTED***"
                                                    : "***REDACTED***");
        }

        // 密码字段脱敏
        filtered = PASSWORD_PATTERN.matcher(filtered).replaceAll(mr -> mr.group(1) + "=****");

        // 数据库连接串密码脱敏
        filtered = DB_URL_PATTERN.matcher(filtered).replaceAll(mr -> mr.group(1) + "****");

        if (!filtered.equals(output)) {
            log.info("[AgentOutputFilter] 输出已脱敏处理");
        }

        return filtered;
    }

    /**
     * 递归过滤结构化数据（Map/List）中的敏感字段
     *
     * <p>遍历 Map 和 List 结构，对匹配敏感字段名的值进行脱敏。
     *
     * @param data 原始结构化数据
     * @return 脱敏后的数据
     */
    @SuppressWarnings("unchecked")
    public Object filterStructuredData(Object data) {
        if (data == null) {
            return null;
        }

        if (data instanceof Map<?, ?> map) {
            return filterMap((Map<String, Object>) map);
        } else if (data instanceof List<?> list) {
            return filterList((List<Object>) list);
        } else if (data instanceof String str) {
            return filterResult(str);
        }

        return data;
    }

    /** 递归过滤 Map 中的敏感字段 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> filterMap(Map<String, Object> map) {
        Map<String, Object> filtered = new HashMap<>();

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (isSensitiveField(key)) {
                filtered.put(key, MASKED_VALUE);
                log.debug("[AgentOutputFilter] Map 字段脱敏: {}", key);
            } else if (value instanceof Map<?, ?>) {
                filtered.put(key, filterMap((Map<String, Object>) value));
            } else if (value instanceof List<?>) {
                filtered.put(key, filterList((List<Object>) value));
            } else if (value instanceof String str) {
                filtered.put(key, filterResult(str));
            } else {
                filtered.put(key, value);
            }
        }

        return filtered;
    }

    /** 递归过滤 List 中的敏感数据 */
    @SuppressWarnings("unchecked")
    private List<Object> filterList(List<Object> list) {
        List<Object> filtered = new ArrayList<>();

        for (Object item : list) {
            if (item instanceof Map<?, ?>) {
                filtered.add(filterMap((Map<String, Object>) item));
            } else if (item instanceof List<?>) {
                filtered.add(filterList((List<Object>) item));
            } else if (item instanceof String str) {
                filtered.add(filterResult(str));
            } else {
                filtered.add(item);
            }
        }

        return filtered;
    }

    /** 判断字段名是否为敏感字段 */
    private boolean isSensitiveField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String lower = fieldName.toLowerCase().replace("-", "_");
        return SENSITIVE_FIELD_NAMES.contains(lower);
    }

    /**
     * 检查输出是否包含敏感信息
     *
     * @param output 输出内容
     * @return 是否包含敏感信息
     */
    public boolean containsSensitiveData(String output) {
        if (output == null || output.isEmpty()) {
            return false;
        }

        for (Pattern pattern : API_KEY_PATTERNS) {
            if (pattern.matcher(output).find()) {
                return true;
            }
        }

        if (PASSWORD_PATTERN.matcher(output).find()) {
            return true;
        }

        return DB_URL_PATTERN.matcher(output).find();
    }
}
