package com.bjjw.rule.example.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 *
 * 统一处理规则执行过程中可能出现的各种异常：
 * - IllegalArgumentException: 参数校验失败（如缺少必填参数）
 * - RuntimeException: 规则执行失败（如规则未找到、编译错误）
 * - Exception: 未预期的系统异常
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("参数校验失败: {}", e.getMessage());
        return buildError(400, e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleRuntime(RuntimeException e) {
        log.error("规则执行异常: {}", e.getMessage(), e);
        return buildError(500, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleException(Exception e) {
        log.error("系统异常", e);
        return buildError(500, "系统内部错误，请联系管理员");
    }

    private Map<String, Object> buildError(int code, String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("code", code);
        result.put("message", message);
        return result;
    }
}
