package com.stellar.common;

import cn.dev33.satoken.exception.NotLoginException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：统一捕获 Controller 层异常，记录请求上下文（方法/URI）后转 {@link Result} 信封。
 * <p>traceId 由 {@link com.stellar.interceptor.RequestLogInterceptor} 放入 MDC，日志格式自动输出，无需手动拼接。
 *
 * @author stellar
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("业务异常 {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return Result.failed(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(NotLoginException.class)
    public Result<Void> handleNotLoginException(NotLoginException e, HttpServletRequest request) {
        log.warn("未登录访问 {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return Result.failed(ResultCode.UNAUTHORIZED);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e, HttpServletRequest request) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "参数错误";
        log.warn("参数校验失败 {} {}: {}", request.getMethod(), request.getRequestURI(), message);
        return Result.failed(ResultCode.PARAM_ERROR.getCode(), message);
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e, HttpServletRequest request) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "参数错误";
        log.warn("参数绑定失败 {} {}: {}", request.getMethod(), request.getRequestURI(), message);
        return Result.failed(ResultCode.PARAM_ERROR.getCode(), message);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常 {} {}: ", request.getMethod(), request.getRequestURI(), e);
        return Result.failed("系统异常，请联系管理员");
    }
}
