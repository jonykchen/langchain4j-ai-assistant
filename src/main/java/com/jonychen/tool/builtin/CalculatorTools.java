package com.jonychen.tool.builtin;

import com.jonychen.tool.AgentTool;
import com.jonychen.tool.ToolCategory;
import com.jonychen.tool.ToolParam;
import com.jonychen.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 计算器工具
 *
 * @author jonychen
 */
@Component
public class CalculatorTools {

    /**
     * 基础数学计算
     */
    @AgentTool(
            name = "calculate",
            description = "执行基础数学计算：加减乘除",
            category = ToolCategory.SYSTEM
    )
    public ToolResult calculate(
            @ToolParam(name = "a", description = "第一个操作数")
            Double a,
            @ToolParam(name = "operation", description = "运算符：+、-、*、/")
            String operation,
            @ToolParam(name = "b", description = "第二个操作数")
            Double b
    ) {
        try {
            if (a == null || b == null) {
                return ToolResult.failure("操作数不能为空");
            }

            double result = switch (operation) {
                case "+" -> a + b;
                case "-" -> a - b;
                case "*" -> a * b;
                case "/" -> {
                    if (b == 0) {
                        throw new ArithmeticException("除数不能为零");
                    }
                    yield a / b;
                }
                default -> throw new IllegalArgumentException("不支持的运算符: " + operation);
            };

            // 格式化结果，去除不必要的小数位
            String expression = formatNumber(a) + " " + operation + " " + formatNumber(b);
            return ToolResult.success(Map.of(
                    "result", formatResult(result),
                    "expression", expression
            ));
        } catch (ArithmeticException | IllegalArgumentException e) {
            return ToolResult.failure(e.getMessage());
        } catch (Exception e) {
            return ToolResult.failure("计算失败: " + e.getMessage());
        }
    }

    /**
     * 表达式计算
     */
    @AgentTool(
            name = "evaluate_expression",
            description = "计算数学表达式的值，支持加减乘除和括号",
            category = ToolCategory.SYSTEM
    )
    public ToolResult evaluateExpression(
            @ToolParam(name = "expression", description = "数学表达式，如 2+3*4 或 (10-5)/2")
            String expression
    ) {
        try {
            if (expression == null || expression.isBlank()) {
                return ToolResult.failure("表达式不能为空");
            }

            // 简单的表达式求值（支持加减乘除和括号）
            double result = evaluateSimpleExpression(expression);

            return ToolResult.success(Map.of(
                    "result", formatResult(result),
                    "expression", expression
            ));
        } catch (Exception e) {
            return ToolResult.failure("表达式计算失败: " + e.getMessage());
        }
    }

    /**
     * 百分比计算
     */
    @AgentTool(
            name = "calculate_percentage",
            description = "计算百分比相关数值",
            category = ToolCategory.SYSTEM
    )
    public ToolResult calculatePercentage(
            @ToolParam(name = "value", description = "数值")
            Double value,
            @ToolParam(name = "percentage", description = "百分比值，如 20 表示 20%")
            Double percentage,
            @ToolParam(name = "operation", description = "操作类型：of(求百分比值)、add(增加百分比)、subtract(减少百分比)")
            String operation
    ) {
        try {
            if (value == null || percentage == null) {
                return ToolResult.failure("数值和百分比不能为空");
            }

            double result;
            String description;

            switch (operation.toLowerCase()) {
                case "of" -> {
                    result = value * percentage / 100;
                    description = formatNumber(value) + " 的 " + formatNumber(percentage) + "%";
                }
                case "add" -> {
                    result = value * (1 + percentage / 100);
                    description = formatNumber(value) + " 增加 " + formatNumber(percentage) + "%";
                }
                case "subtract" -> {
                    result = value * (1 - percentage / 100);
                    description = formatNumber(value) + " 减少 " + formatNumber(percentage) + "%";
                }
                default -> throw new IllegalArgumentException("不支持的操作类型: " + operation);
            }

            return ToolResult.success(Map.of(
                    "result", formatResult(result),
                    "description", description
            ));
        } catch (Exception e) {
            return ToolResult.failure("百分比计算失败: " + e.getMessage());
        }
    }

    /**
     * 单位转换
     */
    @AgentTool(
            name = "convert_units",
            description = "单位转换：长度、重量、温度等",
            category = ToolCategory.SYSTEM
    )
    public ToolResult convertUnits(
            @ToolParam(name = "value", description = "数值")
            Double value,
            @ToolParam(name = "from_unit", description = "原单位，如 km, kg, celsius")
            String fromUnit,
            @ToolParam(name = "to_unit", description = "目标单位，如 mile, lb, fahrenheit")
            String toUnit
    ) {
        try {
            if (value == null || fromUnit == null || toUnit == null) {
                return ToolResult.failure("参数不能为空");
            }

            Map<String, Object> result = convert(value, fromUnit.toLowerCase(), toUnit.toLowerCase());
            return ToolResult.success(result);
        } catch (Exception e) {
            return ToolResult.failure("单位转换失败: " + e.getMessage());
        }
    }

    private double evaluateSimpleExpression(String expression) {
        // 移除空格
        String expr = expression.replaceAll("\\s+", "");

        // 简单递归下降解析
        return new Object() {
            int pos = -1, ch;

            void nextChar() {
                ch = (++pos < expr.length()) ? expr.charAt(pos) : -1;
            }

            boolean eat(int charToEat) {
                while (ch == ' ') nextChar();
                if (ch == charToEat) {
                    nextChar();
                    return true;
                }
                return false;
            }

            double parse() {
                nextChar();
                double x = parseExpression();
                if (pos < expr.length()) throw new RuntimeException("Unexpected: " + (char) ch);
                return x;
            }

            double parseExpression() {
                double x = parseTerm();
                for (; ; ) {
                    if (eat('+')) x += parseTerm();
                    else if (eat('-')) x -= parseTerm();
                    else return x;
                }
            }

            double parseTerm() {
                double x = parseFactor();
                for (; ; ) {
                    if (eat('*')) x *= parseFactor();
                    else if (eat('/')) x /= parseFactor();
                    else return x;
                }
            }

            double parseFactor() {
                if (eat('+')) return parseFactor();
                if (eat('-')) return -parseFactor();

                double x;
                int startPos = this.pos;
                if (eat('(')) {
                    x = parseExpression();
                    eat(')');
                } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                    while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                    x = Double.parseDouble(expr.substring(startPos, this.pos));
                } else {
                    throw new RuntimeException("Unexpected: " + (char) ch);
                }

                return x;
            }
        }.parse();
    }

    private Map<String, Object> convert(double value, String from, String to) {
        Map<String, Object> result = new HashMap<>();

        // 长度转换（基准：米）
        Map<String, Double> lengthUnits = Map.ofEntries(
                Map.entry("m", 1.0), Map.entry("meter", 1.0), Map.entry("meters", 1.0),
                Map.entry("km", 1000.0), Map.entry("kilometer", 1000.0), Map.entry("kilometers", 1000.0),
                Map.entry("cm", 0.01), Map.entry("centimeter", 0.01), Map.entry("centimeters", 0.01),
                Map.entry("mm", 0.001), Map.entry("millimeter", 0.001), Map.entry("millimeters", 0.001),
                Map.entry("mile", 1609.344), Map.entry("miles", 1609.344),
                Map.entry("ft", 0.3048), Map.entry("foot", 0.3048), Map.entry("feet", 0.3048),
                Map.entry("in", 0.0254), Map.entry("inch", 0.0254), Map.entry("inches", 0.0254)
        );

        // 重量转换（基准：千克）
        Map<String, Double> weightUnits = Map.ofEntries(
                Map.entry("kg", 1.0), Map.entry("kilogram", 1.0), Map.entry("kilograms", 1.0),
                Map.entry("g", 0.001), Map.entry("gram", 0.001), Map.entry("grams", 0.001),
                Map.entry("lb", 0.453592), Map.entry("pound", 0.453592), Map.entry("pounds", 0.453592),
                Map.entry("oz", 0.0283495), Map.entry("ounce", 0.0283495), Map.entry("ounces", 0.0283495)
        );

        double metersValue;
        if (lengthUnits.containsKey(from)) {
            metersValue = value * lengthUnits.get(from);
            if (lengthUnits.containsKey(to)) {
                double converted = metersValue / lengthUnits.get(to);
                result.put("result", formatResult(converted));
                result.put("from", value + " " + from);
                result.put("to", formatResult(converted) + " " + to);
                result.put("type", "length");
                return result;
            }
        }

        if (weightUnits.containsKey(from)) {
            metersValue = value * weightUnits.get(from);
            if (weightUnits.containsKey(to)) {
                double converted = metersValue / weightUnits.get(to);
                result.put("result", formatResult(converted));
                result.put("from", value + " " + from);
                result.put("to", formatResult(converted) + " " + to);
                result.put("type", "weight");
                return result;
            }
        }

        // 温度转换
        if (from.equals("celsius") || from.equals("c")) {
            if (to.equals("fahrenheit") || to.equals("f")) {
                double converted = value * 9 / 5 + 32;
                result.put("result", formatResult(converted));
                result.put("from", value + "°C");
                result.put("to", formatResult(converted) + "°F");
                result.put("type", "temperature");
                return result;
            } else if (to.equals("kelvin") || to.equals("k")) {
                double converted = value + 273.15;
                result.put("result", formatResult(converted));
                result.put("from", value + "°C");
                result.put("to", formatResult(converted) + "K");
                result.put("type", "temperature");
                return result;
            }
        }

        if (from.equals("fahrenheit") || from.equals("f")) {
            if (to.equals("celsius") || to.equals("c")) {
                double converted = (value - 32) * 5 / 9;
                result.put("result", formatResult(converted));
                result.put("from", value + "°F");
                result.put("to", formatResult(converted) + "°C");
                result.put("type", "temperature");
                return result;
            }
        }

        throw new IllegalArgumentException("不支持的单位转换: " + from + " -> " + to);
    }

    private String formatNumber(Double value) {
        if (value == null) return "0";
        if (value == value.longValue()) {
            return String.valueOf(value.longValue());
        }
        return String.valueOf(value);
    }

    private Object formatResult(double result) {
        if (result == (long) result) {
            return (long) result;
        }
        return Math.round(result * 1000000.0) / 1000000.0;  // 保留6位小数
    }
}
