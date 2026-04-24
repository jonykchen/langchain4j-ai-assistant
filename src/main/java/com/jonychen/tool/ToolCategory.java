package com.jonychen.tool;

/**
 * 工具分类枚举
 *
 * @author jonychen
 */
public enum ToolCategory {
    SYSTEM("系统工具", "时间、计算器等系统内置工具"),
    SEARCH("搜索工具", "网络搜索、文档搜索"),
    DATABASE("数据库工具", "查询数据库"),
    FILE("文件工具", "读写文件"),
    MODEL_STATE("模型状态", "查询和调整模型状态"),
    PROMPT("Prompt", "Prompt 版本管理"),
    TEST("测试", "测试生成和执行"),
    EXTERNAL("外部服务", "API 调用"),
    CUSTOM("自定义工具", "用户自定义工具");

    private final String displayName;
    private final String description;

    ToolCategory(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
