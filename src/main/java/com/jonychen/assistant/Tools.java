package com.jonychen.assistant;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * AI 工具类
 * 使用 @Tool 注解定义 AI 可调用的方法
 *
 * @author 30240
 */
@Component
public class Tools {

    /**
     * 获取当前时间
     * AI 可以调用此工具来获取实时时间
     */
    @Tool("获取当前的日期和时间")
    public String getCurrentDateTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * 计算器工具
     * AI 可以调用此工具进行数学计算
     */
    @Tool("执行数学计算，支持加减乘除")
    public double calculate(double a, String operation, double b) {
        return switch (operation) {
            case "+" -> a + b;
            case "-" -> a - b;
            case "*" -> a * b;
            case "/" -> a / b;
            default -> throw new IllegalArgumentException("不支持的操作: " + operation);
        };
    }
}
