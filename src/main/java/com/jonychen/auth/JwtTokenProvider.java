package com.jonychen.auth;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

/** JWT Token 提供者 负责生成、验证和解析 JWT Token */
@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret:REDACTED_JWT_SECRET}")
    private String secret;

    @Value("${jwt.access-token-expiration:3600}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration:604800}")
    private long refreshTokenExpiration;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        // 确保密钥长度足够
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            // 如果密钥长度不足，进行填充
            byte[] paddedKey = new byte[32];
            System.arraycopy(keyBytes, 0, paddedKey, 0, Math.min(keyBytes.length, 32));
            keyBytes = paddedKey;
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /** 生成访问令牌和刷新令牌 */
    public TokenResponse generateToken(User user) {
        long now = System.currentTimeMillis();

        // 生成访问令牌
        String accessToken =
                Jwts.builder()
                        .subject(user.getId())
                        .claim("username", user.getUsername())
                        .claim("role", user.getRole().name())
                        .issuedAt(new Date(now))
                        .expiration(new Date(now + accessTokenExpiration * 1000))
                        .signWith(secretKey)
                        .compact();

        // 生成刷新令牌
        String refreshToken =
                Jwts.builder()
                        .subject(user.getId())
                        .claim("type", "refresh")
                        .issuedAt(new Date(now))
                        .expiration(new Date(now + refreshTokenExpiration * 1000))
                        .signWith(secretKey)
                        .compact();

        return TokenResponse.of(accessToken, refreshToken, accessTokenExpiration);
    }

    /** 验证 Token 是否有效 */
    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT Token 验证失败: {}", e.getMessage());
            return false;
        }
    }

    /** 从 Token 中获取用户 ID */
    public String getUserIdFromToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
        } catch (JwtException e) {
            log.warn("从 Token 获取用户 ID 失败: {}", e.getMessage());
            return null;
        }
    }

    /** 从 Token 中获取用户名 */
    public String getUsernameFromToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .get("username", String.class);
        } catch (JwtException e) {
            log.warn("从 Token 获取用户名失败: {}", e.getMessage());
            return null;
        }
    }

    /** 从 Token 中获取角色 */
    public String getRoleFromToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .get("role", String.class);
        } catch (JwtException e) {
            log.warn("从 Token 获取角色失败: {}", e.getMessage());
            return null;
        }
    }

    /** 检查 Token 是否为刷新令牌 */
    public boolean isRefreshToken(String token) {
        try {
            String type =
                    Jwts.parser()
                            .verifyWith(secretKey)
                            .build()
                            .parseSignedClaims(token)
                            .getPayload()
                            .get("type", String.class);
            return "refresh".equals(type);
        } catch (JwtException e) {
            return false;
        }
    }
}
