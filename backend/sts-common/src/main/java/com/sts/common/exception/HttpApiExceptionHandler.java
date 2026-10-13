package com.sts.common.exception;

import com.sts.common.api.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class HttpApiExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException ex) {
        return ResponseEntity
            .status(ex.getStatus())
            .body(Result.fail(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValid(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining("; "));
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(Result.fail(1001, msg));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBind(BindException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining("; "));
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(Result.fail(1001, msg));
    }

    /** 处理 @Validated 控制器上方法参数级校验（如 @Min/@Pattern）失败，返回 400 且不泄露堆栈。 */
    @ExceptionHandler(org.springframework.web.method.annotation.HandlerMethodValidationException.class)
    public ResponseEntity<Result<Void>> handleMethodValidation(
        org.springframework.web.method.annotation.HandlerMethodValidationException ex) {
        String msg = ex.getAllValidationResults().stream()
            .flatMap(r -> {
                String name = r.getMethodParameter().getParameterName();
                return r.getResolvableErrors().stream()
                    .map(e -> (name == null ? "" : name + ": ") + e.getDefaultMessage());
            })
            .collect(Collectors.joining("; "));
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(Result.fail(1001, msg));
    }

    /** 处理方法参数或普通 Bean 校验抛出的约束冲突，统一 400。 */
    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(
        jakarta.validation.ConstraintViolationException ex) {
        String msg = ex.getConstraintViolations().stream()
            .map(v -> v.getPropertyPath() + ": " + v.getMessage())
            .collect(Collectors.joining("; "));
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(Result.fail(1001, msg));
    }

    @ExceptionHandler(cn.dev33.satoken.exception.NotLoginException.class)
    public ResponseEntity<Result<Void>> handleNotLogin(cn.dev33.satoken.exception.NotLoginException ex) {
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(Result.fail(1002, "未登录: " + ex.getMessage()));
    }

    @ExceptionHandler(cn.dev33.satoken.exception.NotPermissionException.class)
    public ResponseEntity<Result<Void>> handleNotPermission(cn.dev33.satoken.exception.NotPermissionException ex) {
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(Result.fail(1003, "权限不足: " + ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception ex) {
        log.error("系统未知错误", ex);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Result.fail(9999, "系统未知错误: " + ex.getMessage()));
    }
}
