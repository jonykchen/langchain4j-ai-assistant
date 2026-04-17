package com.jonychen.tool.builtin;

import com.jonychen.tool.AgentTool;
import com.jonychen.tool.ToolCategory;
import com.jonychen.tool.ToolParam;
import com.jonychen.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 日期时间工具
 *
 * @author jonychen
 */
@Component
public class DateTimeTools {

    private static final DateTimeFormatter DEFAULT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * 获取当前时间
     */
    @AgentTool(
            name = "get_current_time",
            description = "获取当前的日期和时间，支持指定时区",
            category = ToolCategory.SYSTEM
    )
    public ToolResult getCurrentTime(
            @ToolParam(name = "timezone", description = "时区，如 Asia/Shanghai, UTC", required = false)
            String timezone,
            @ToolParam(name = "format", description = "输出格式：default 或 iso", required = false)
            String format
    ) {
        try {
            ZoneId zone = parseTimezone(timezone);
            ZonedDateTime now = ZonedDateTime.now(zone);

            DateTimeFormatter formatter = "iso".equalsIgnoreCase(format)
                    ? ISO_FORMATTER
                    : DEFAULT_FORMATTER;

            String formatted = now.format(formatter);
            return ToolResult.success(Map.of(
                    "datetime", formatted,
                    "timezone", zone.toString(),
                    "timestamp", now.toInstant().toEpochMilli()
            ));
        } catch (Exception e) {
            return ToolResult.failure("获取时间失败: " + e.getMessage());
        }
    }

    /**
     * 获取当前日期
     */
    @AgentTool(
            name = "get_current_date",
            description = "获取当前日期（年月日）",
            category = ToolCategory.SYSTEM
    )
    public ToolResult getCurrentDate(
            @ToolParam(name = "timezone", description = "时区，如 Asia/Shanghai", required = false)
            String timezone
    ) {
        try {
            ZoneId zone = parseTimezone(timezone);
            LocalDateTime now = LocalDateTime.now(zone);
            return ToolResult.success(Map.of(
                    "date", now.toLocalDate().toString(),
                    "year", now.getYear(),
                    "month", now.getMonthValue(),
                    "day", now.getDayOfMonth()
            ));
        } catch (Exception e) {
            return ToolResult.failure("获取日期失败: " + e.getMessage());
        }
    }

    /**
     * 时间格式转换
     */
    @AgentTool(
            name = "format_datetime",
            description = "将时间戳转换为可读的日期时间格式",
            category = ToolCategory.SYSTEM
    )
    public ToolResult formatDatetime(
            @ToolParam(name = "timestamp", description = "Unix 时间戳（毫秒）")
            Long timestamp,
            @ToolParam(name = "format", description = "输出格式，如 yyyy-MM-dd HH:mm:ss", required = false)
            String format
    ) {
        try {
            DateTimeFormatter formatter = format != null && !format.isBlank()
                    ? DateTimeFormatter.ofPattern(format)
                    : DEFAULT_FORMATTER;

            String formatted = LocalDateTime.ofEpochSecond(
                    timestamp / 1000,
                    (int) ((timestamp % 1000) * 1_000_000),
                    java.time.ZoneOffset.UTC
            ).format(formatter);

            return ToolResult.success(Map.of(
                    "formatted", formatted,
                    "timestamp", timestamp
            ));
        } catch (Exception e) {
            return ToolResult.failure("格式转换失败: " + e.getMessage());
        }
    }

    private ZoneId parseTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(timezone);
        } catch (Exception e) {
            return ZoneId.systemDefault();
        }
    }
}
