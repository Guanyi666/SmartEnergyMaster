package com.smartenergy.backend.exception;

/** 触发限流时抛出，由 GlobalExceptionHandler 映射为 HTTP 429。 */
public class RateLimitException extends RuntimeException {
    public RateLimitException(String message) {
        super(message);
    }
}
