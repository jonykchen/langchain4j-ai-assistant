package com.jonychen.exception;

import com.jonychen.model.ApiResponse;
import com.jonychen.model.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 *
 * 统一处理所有 Controller 抛出的异常，返回标准化的错误响应格式
 *
 * 异常处理优先级：
 * 1. BusinessException - 业务异常，返回具体错误码和消息
 * 2. 参数校验异常 - 返回参数错误提示
 * 3. 未知异常 - 返回通用服务器错误，记录详细日志
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常
     *
     * 业务代码抛出的可预期异常，返回具体错误信息
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());

        return ResponseEntity
                .status(getHttpStatus(e.getCode()))
                .body(ApiResponse.error(e.getErrorCode(), e.getMessage()));
    }

    /**
     * 处理参数校验异常（@Valid 校验失败）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        // 提取所有字段错误信息
        String message = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));

        log.warn("参数校验失败: {}", message);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.PARAM_INVALID, message));
    }

    /**
     * 处理缺少必需参数异常
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParamException(MissingServletRequestParameterException e) {
        log.warn("缺少必需参数: {}", e.getParameterName());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.PARAM_MISSING, "缺少必需参数: " + e.getParameterName()));
    }

    /**
     * 处理参数类型转换异常
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatchException(MethodArgumentTypeMismatchException e) {
        String message = String.format("参数 '%s' 类型不正确，期望类型: %s",
                e.getName(), e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "未知");

        log.warn("参数类型错误: {}", message);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.PARAM_INVALID, message));
    }

    /**
     * 处理 IllegalArgumentException
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("非法参数: {}", e.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.BAD_REQUEST, e.getMessage()));
    }

    /**
     * 处理静态资源未找到异常（如 favicon.ico）
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(NoResourceFoundException e) {
        log.debug("静态资源未找到: {}", e.getResourcePath());

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ErrorCode.NOT_FOUND, "资源未找到: " + e.getResourcePath()));
    }

    /**
     * 处理所有未捕获的异常
     *
     * 这是最后的兜底处理，记录完整堆栈信息，返回通用错误提示
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception e) {
        // 记录完整的错误堆栈
        log.error("未处理的异常", e);

        // 生产环境不应暴露具体的错误信息
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR));
    }

    /**
     * 根据错误码映射 HTTP 状态码
     */
    private HttpStatus getHttpStatus(int code) {
        if (code >= 200 && code < 300) {
            return HttpStatus.OK;
        } else if (code >= 400 && code < 500) {
            return HttpStatus.valueOf(code);
        } else if (code >= 500 && code < 600) {
            return HttpStatus.valueOf(code);
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
