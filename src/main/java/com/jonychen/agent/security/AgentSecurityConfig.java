package com.jonychen.agent.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.jonychen.auth.JwtAccessDeniedHandler;
import com.jonychen.auth.JwtAuthenticationEntryPoint;
import com.jonychen.auth.JwtAuthenticationFilter;

import lombok.RequiredArgsConstructor;

/**
 * Agent 独立安全配置
 *
 * <p>为 Agent 路径配置独立的安全链，优先级高于主 SecurityConfig。
 *
 * <h2>路径权限</h2>
 *
 * <ul>
 *   <li>/api/agent/list - 所有认证用户可访问
 *   <li>/api/agent/history - 所有认证用户可访问
 *   <li>/api/agent/execute - 所有认证用户可访问
 *   <li>/api/agent/confirm - 所有认证用户可访问
 *   <li>/api/agent/cancel/** - 所有认证用户可访问
 *   <li>/api/admin/agent/** - 仅 ADMIN 角色可访问
 * </ul>
 *
 * @author jonychen
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Order(1) // 优先于主 SecurityConfig
public class AgentSecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    /**
     * Agent 路径安全过滤器链
     */
    @Bean
    public SecurityFilterChain agentSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                // 仅匹配 Agent 路径
                .securityMatcher("/api/agent/**", "/api/admin/agent/**")

                // 禁用 CSRF（使用 JWT 无状态认证）
                .csrf(AbstractHttpConfigurer::disable)

                // 无状态 Session
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 配置请求授权
                .authorizeHttpRequests(
                        auth -> auth
                                // Agent 列表接口 - 所有认证用户可访问
                                .requestMatchers("/api/agent/list")
                                .authenticated()

                                // Agent 执行历史 - 所有认证用户可访问
                                .requestMatchers("/api/agent/history")
                                .authenticated()

                                // Agent 执行历史详情 - 所有认证用户可访问
                                .requestMatchers("/api/agent/history/**")
                                .authenticated()

                                // Agent 执行接口 - 所有认证用户可访问
                                .requestMatchers("/api/agent/execute")
                                .authenticated()

                                // Agent 确认接口 - 所有认证用户可访问
                                .requestMatchers("/api/agent/confirm")
                                .authenticated()

                                // Agent 取消执行 - 所有认证用户可访问
                                .requestMatchers("/api/agent/cancel/**")
                                .authenticated()

                                // Agent 管理接口 - 仅 ADMIN
                                .requestMatchers("/api/admin/agent/**")
                                .hasRole("ADMIN"))

                // 异常处理
                .exceptionHandling(
                        exception -> exception
                                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                                .accessDeniedHandler(jwtAccessDeniedHandler))

                // 添加 JWT 过滤器
                .addFilterBefore(
                        jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
