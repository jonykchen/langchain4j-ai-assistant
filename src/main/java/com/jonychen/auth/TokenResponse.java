package com.jonychen.auth;

/**
 * Token 响应结构
 */
public record TokenResponse(
    /**
     * 访问令牌
     */
    String accessToken,

    /**
     * 刷新令牌
     */
    String refreshToken,

    /**
     * 令牌类型（Bearer）
     */
    String tokenType,

    /**
     * 过期时间（秒）
     */
    long expiresIn,

    /**
     * 权限范围
     */
    String scope
) {
    /**
     * 创建 Bearer 类型的 Token 响应
     */
    public static TokenResponse of(String accessToken, String refreshToken, long expiresIn) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresIn, "read write");
    }
}
