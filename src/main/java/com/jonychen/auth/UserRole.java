package com.jonychen.auth;

/**
 * 用户角色枚举
 */
public enum UserRole {
    /**
     * 普通用户
     */
    USER("普通用户"),

    /**
     * 管理员
     */
    ADMIN("管理员");

    private final String displayName;

    UserRole(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
