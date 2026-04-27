package com.jonychen.auth;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** JWT 认证过滤器 从请求头中提取 JWT Token 并进行验证 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    /** 跳过 JWT 验证的公开路径（与 SecurityConfig permitAll 保持一致） */
    private static final List<String> EXCLUDED_PATHS = Arrays.asList(
            "/actuator/**",
            "/auth/**",
            "/",
            "/index.html",
            "/favicon.ico",
            "/assets/**",
            "/*.js",
            "/*.css",
            "/*.png",
            "/*.svg",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/openapi.yml");

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return EXCLUDED_PATHS.stream().anyMatch(pattern -> pathMatcher.match(pattern, uri));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();
        log.info("[JwtAuthenticationFilter] 处理请求: {}, Authorization: {}",
            uri, request.getHeader("Authorization") != null ? "存在" : "不存在");

        // 1. 从请求头中提取 Token
        String token = resolveToken(request);

        // 2. 验证 Token 并设置认证信息
        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            String userId = jwtTokenProvider.getUserIdFromToken(token);
            String role = jwtTokenProvider.getRoleFromToken(token);

            log.info("[JwtAuthenticationFilter] Token 验证成功, userId={}, role={}", userId, role);

            if (userId != null && role != null) {
                // 创建认证对象
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userId,
                                null,
                                Collections.singletonList(
                                        new SimpleGrantedAuthority("ROLE_" + role)));

                // 设置到安全上下文
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("[JwtAuthenticationFilter] 认证成功, userId={}, role={}", userId, role);
            }
        } else {
            log.warn("[JwtAuthenticationFilter] Token 无效或不存在, uri={}", uri);
        }

        // 3. 继续过滤器链
        filterChain.doFilter(request, response);
    }

    /** 从请求头中解析 Token */
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
