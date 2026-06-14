package com.smartenergy.backend.controller;

import com.smartenergy.backend.exception.LockAcquireException;
import com.smartenergy.backend.exception.RateLimitException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("参数校验失败");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", message));
    }

    /**
     * ★ M1 修复 (2026-06-13): @PathVariable 类型不匹配 (如 /api/devices/abc) →
     * 旧版返回 HTML 错误页, 现返回结构化 JSON.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "参数 '" + ex.getName() + "' 格式错误,期望类型: "
                        + (ex.getRequiredType() == null ? "未知" : ex.getRequiredType().getSimpleName())));
    }

    /**
     * ★ M1 配套: 必填 @PathVariable 缺失
     */
    @ExceptionHandler(MissingPathVariableException.class)
    public ResponseEntity<Map<String, String>> handleMissingPath(MissingPathVariableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "路径参数 '" + ex.getVariableName() + "' 缺失"));
    }

    /**
     * ★ M1 配套: 必填 @RequestParam 缺失
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, String>> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "请求参数 '" + ex.getParameterName() + "' 缺失"));
    }

    /**
     * ★ M1 配套: 请求体 JSON 解析失败 (如格式错误)
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "请求体格式错误,请检查 JSON 是否合法"));
    }

    @ExceptionHandler(LockAcquireException.class)
    public ResponseEntity<Map<String, String>> handleLock(LockAcquireException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<Map<String, String>> handleRateLimit(RateLimitException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", "当前账号无权执行此操作"));
    }

    /**
     * ★ 批次 4 H3 预留: 乐观锁冲突
     * (当前 entity 无 @Version, 此 handler 不会被触发, 等批次 4 加 @Version 后生效)
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> handleOptLock(OptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", "数据已被其他用户修改,请刷新后重试"));
    }

    /**
     * ★ C6 修复 (2026-06-13): 兜底异常不再泄漏 exception.getMessage().
     * 旧版直接把 SQL 错误 / 堆栈细节 / NPE 内容返回客户端 → 信息泄漏安全漏洞。
     * 修复后:
     *   - 服务端: log.error 记完整堆栈 + 关联 errorId, 供运维排查
     *   - 客户端: 只返回通用提示 + errorId (便于用户报错时关联日志)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception exception) {
        String errorId = UUID.randomUUID().toString().substring(0, 8);
        log.error("[500][{}] unhandled exception", errorId, exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "message", "服务器内部错误,请联系管理员",
                        "errorId", errorId
                ));
    }
}
