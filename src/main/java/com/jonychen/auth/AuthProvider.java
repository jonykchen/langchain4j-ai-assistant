package com.jonychen.auth;

/** 认证提供商枚举 */
public enum AuthProvider {
    /** GitHub OAuth 登录 */
    GITHUB("GitHub"),

    /** GitLab OAuth 登录 */
    GITLAB("GitLab"),

    /** 自定义登录（用户名密码） */
    CUSTOM("自定义");

    private final String displayName;

    AuthProvider(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
