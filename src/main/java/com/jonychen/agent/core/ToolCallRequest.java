package com.jonychen.agent.core;

import java.util.Map;

/**
 * 工具调用请求
 *
 * <p>封装 LLM 决定调用的工具名称和参数。 由 LangChain4j 的 ToolExecutionRequest 转换而来。
 *
 * @param name 工具名称
 * @param params 工具参数（已解析为 Map）
 * @author jonychen
 */
public record ToolCallRequest(String name, Map<String, Object> params) {

    /**
     * 获取字符串参数
     *
     * @param key 参数名
     * @return 参数值，不存在则返回 null
     */
    public String getString(String key) {
        Object value = params.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 获取整数参数
     *
     * @param key 参数名
     * @param defaultValue 默认值
     * @return 参数值
     */
    public int getInt(String key, int defaultValue) {
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取布尔参数
     *
     * @param key 参数名
     * @param defaultValue 默认值
     * @return 参数值
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(value.toString());
    }
}
