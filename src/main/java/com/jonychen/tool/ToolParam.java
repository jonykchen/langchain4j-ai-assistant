package com.jonychen.tool;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 工具参数注解 用于描述工具方法的参数
 *
 * @author jonychen
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolParam {
    /** 参数名称 */
    String name() default "";

    /** 参数描述 */
    String description() default "";

    /** 是否必需 */
    boolean required() default true;

    /** 默认值 */
    String defaultValue() default "";

    /** 枚举值（用于限制参数取值范围） */
    String[] enumValues() default {};
}
