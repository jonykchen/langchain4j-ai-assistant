package com.jonychen.tool.config;

import com.jonychen.tool.DefaultToolRegistry;
import com.jonychen.tool.ToolRegistry;
import com.jonychen.tool.builtin.CalculatorTools;
import com.jonychen.tool.builtin.DateTimeTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 工具系统配置
 *
 * @author jonychen
 */
@Slf4j
@Configuration
public class ToolConfig {

    /**
     * 默认工具注册中心 Bean
     */
    @Bean
    @ConditionalOnMissingBean
    public ToolRegistry toolRegistry() {
        return new DefaultToolRegistry();
    }

    /**
     * 注册内置工具
     */
    @Bean
    public ToolRegistryInitializer toolRegistryInitializer(
            ToolRegistry toolRegistry,
            DateTimeTools dateTimeTools,
            CalculatorTools calculatorTools
    ) {
        return new ToolRegistryInitializer(toolRegistry, dateTimeTools, calculatorTools);
    }

    /**
     * 工具注册初始化器
     */
    public static class ToolRegistryInitializer {

        public ToolRegistryInitializer(
                ToolRegistry toolRegistry,
                DateTimeTools dateTimeTools,
                CalculatorTools calculatorTools
        ) {
            log.info("Registering builtin tools...");

            // 注册日期时间工具
            toolRegistry.registerAnnotatedTools(dateTimeTools);

            // 注册计算器工具
            toolRegistry.registerAnnotatedTools(calculatorTools);

            log.info("Registered {} builtin tools", toolRegistry.size());
        }
    }
}
