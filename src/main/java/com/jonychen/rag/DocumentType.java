package com.jonychen.rag;

/**
 * 文档类型枚举
 *
 * @author jonychen
 */
public enum DocumentType {
    PDF("pdf", "PDF 文档"),
    TXT("txt", "纯文本"),
    MD("md", "Markdown 文档"),
    HTML("html", "HTML 文档"),
    DOCX("docx", "Word 文档"),
    XLSX("xlsx", "Excel 文档"),
    JSON("json", "JSON 数据"),
    CSV("csv", "CSV 数据"),
    UNKNOWN("unknown", "未知类型");

    private final String extension;
    private final String description;

    DocumentType(String extension, String description) {
        this.extension = extension;
        this.description = description;
    }

    public String getExtension() {
        return extension;
    }

    public String getDescription() {
        return description;
    }

    /** 根据文件扩展名判断文档类型 */
    public static DocumentType fromExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return UNKNOWN;
        }

        String ext = filename.toLowerCase();
        if (ext.contains(".")) {
            ext = ext.substring(ext.lastIndexOf(".") + 1);
        }

        for (DocumentType type : values()) {
            if (type.extension.equals(ext)) {
                return type;
            }
        }

        return UNKNOWN;
    }

    /** 判断是否支持解析 */
    public boolean isSupported() {
        return this != UNKNOWN;
    }
}
