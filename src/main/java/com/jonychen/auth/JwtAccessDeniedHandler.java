package com.jonychen.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jonychen.model.ApiResponse;
import com.jonychen.model.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 访问拒绝处理器
 * 处理权限不足的请求，返回 403 错误响应
 */
@Slf4j
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {

        log.warn("访问被拒绝: {} - {}", request.getRequestURI(), accessDeniedException.getMessage());

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);

        ApiResponse<Void> apiResponse = ApiResponse.error(
                ErrorCode.FORBIDDEN.getCode(),
                "权限不足，无法访问该资源"
        );

        objectMapper.writeValue(response.getOutputStream(), apiResponse);
    }
}
