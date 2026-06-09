package com.smartenergy.backend.exception;

/** 抢分布式锁失败时抛出，由 GlobalExceptionHandler 映射为 HTTP 423。 */
public class LockAcquireException extends RuntimeException {
    public LockAcquireException(String message) {
        super(message);
    }
}
