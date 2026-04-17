package com.jonychen.exception;

import com.jonychen.model.ApiResponse;
import com.jonychen.model.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GlobalExceptionHandler 单元测试
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("处理业务异常 - 应返回正确的错误响应")
    void handleBusinessException_shouldReturnCorrectResponse() {
        BusinessException exception = new BusinessException(ErrorCode.MESSAGE_EMPTY);

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(exception);

        // 注意：getHttpStatus 方法返回的是基于错误码的状态
        assertNotNull(response.getBody());
        assertEquals(ErrorCode.MESSAGE_EMPTY.getCode(), response.getBody().code());
    }

    @Test
    @DisplayName("处理缺少参数异常 - 应返回参数错误")
    void handleMissingParamException_shouldReturnParamError() {
        org.springframework.web.bind.MissingServletRequestParameterException exception =
                new org.springframework.web.bind.MissingServletRequestParameterException("message", "String");

        ResponseEntity<ApiResponse<Void>> response = handler.handleMissingParamException(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ErrorCode.PARAM_MISSING.getCode(), response.getBody().code());
    }

    @Test
    @DisplayName("处理未知异常 - 应返回服务器错误")
    void handleGenericException_shouldReturnServerError() {
        Exception exception = new RuntimeException("Unknown error");

        ResponseEntity<ApiResponse<Void>> response = handler.handleGenericException(exception);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ErrorCode.INTERNAL_ERROR.getCode(), response.getBody().code());
    }
}