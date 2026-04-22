package com.jonychen.tool;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 工具方法注解 用于标记可以作为 AI 工具调用的方法
 *
 * @author jonychen
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AgentTool {
    /** 工具名称（默认使用方法名） */
    String name() default "";

    /** 功能描述 */
    String description();

    /** 工具分类 */
    ToolCategory category() default ToolCategory.CUSTOM;

    /** 所需权限 */
    String[] requiredPermissions() default {};

    /** 超时时间（毫秒） */
    long timeoutMs() default 30000;

    /** 最大重试次数 */
    int maxRetries() default 2;
}
