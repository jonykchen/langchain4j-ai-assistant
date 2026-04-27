package com.jonychen.tool.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.jonychen.tool.DefaultToolRegistry;
import com.jonychen.tool.ToolRegistry;
import com.jonychen.tool.builtin.CalculatorTools;
import com.jonychen.tool.builtin.ChartTools;
import com.jonychen.tool.builtin.DatabaseTools;
import com.jonychen.tool.builtin.DateTimeTools;
import com.jonychen.tool.builtin.EvaluationTools;
import com.jonychen.tool.builtin.ExportTools;
import com.jonychen.tool.builtin.SourceCodeTools;
import com.jonychen.tool.builtin.TestRunnerTools;

import lombok.extern.slf4j.Slf4j;

/**
 * 工具系统配置
 *
 * @author jonychen
 */
@Slf4j
@Configuration
public class ToolConfig {

    /** 默认工具注册中心 Bean */
    @Bean
    @ConditionalOnMissingBean
    public ToolRegistry toolRegistry() {
        return new DefaultToolRegistry();
    }

    /** 注册内置工具 */
    @Bean
    public ToolRegistryInitializer toolRegistryInitializer(
            ToolRegistry toolRegistry,
            DateTimeTools dateTimeTools,
            CalculatorTools calculatorTools,
            DatabaseTools databaseTools,
            ChartTools chartTools,
            ExportTools exportTools,
            EvaluationTools evaluationTools,
            SourceCodeTools sourceCodeTools,
            TestRunnerTools testRunnerTools) {
        return new ToolRegistryInitializer(
                toolRegistry,
                dateTimeTools,
                calculatorTools,
                databaseTools,
                chartTools,
                exportTools,
                evaluationTools,
                sourceCodeTools,
                testRunnerTools);
    }

    /** 工具注册初始化器 */
    public static class ToolRegistryInitializer {

        public ToolRegistryInitializer(
                ToolRegistry toolRegistry,
                DateTimeTools dateTimeTools,
                CalculatorTools calculatorTools,
                DatabaseTools databaseTools,
                ChartTools chartTools,
                ExportTools exportTools,
                EvaluationTools evaluationTools,
                SourceCodeTools sourceCodeTools,
                TestRunnerTools testRunnerTools) {
            log.info("Registering builtin tools...");

            // 注册日期时间工具
            toolRegistry.registerAnnotatedTools(dateTimeTools);

            // 注册计算器工具
            toolRegistry.registerAnnotatedTools(calculatorTools);

            // 注册数据库工具（list_tables, describe_table, execute_query）
            toolRegistry.registerAnnotatedTools(databaseTools);

            // 注册图表工具（generate_chart）
            toolRegistry.registerAnnotatedTools(chartTools);

            // 注册导出工具（export_data）
            toolRegistry.registerAnnotatedTools(exportTools);

            // 注册 Prompt 评测工具
            toolRegistry.registerAnnotatedTools(evaluationTools);

            // 注册源代码工具
            toolRegistry.registerAnnotatedTools(sourceCodeTools);

            // 注册测试执行工具
            toolRegistry.registerAnnotatedTools(testRunnerTools);

            log.info("Registered {} builtin tools", toolRegistry.size());
        }
    }
}
