package com.jonychen.tool;

import java.util.Map;

/**
 * 工具执行器接口
 *
 * @author jonychen
 */
@FunctionalInterface
public interface ToolExecutor {

    /**
     * 执行工具
     *
     * @param params 工具参数
     * @return 执行结果
     */
    ToolResult execute(Map<String, Object> params);

    /**
     * 验证参数（可选实现）
     *
     * @param params 工具参数
     * @return 验证结果，null 表示通过
     */
    default String validate(Map<String, Object> params) {
        return null;
    }
}
