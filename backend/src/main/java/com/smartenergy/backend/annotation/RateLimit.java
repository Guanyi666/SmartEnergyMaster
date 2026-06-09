package com.smartenergy.backend.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流 (Epic 11-3). 基于 Redis 滑动窗口，超限抛 {@code RateLimitException}(HTTP 429)。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 限流维度。 */
    enum Dimension {
        /** 按调用方 IP 限流（登录防爆破）。 */
        IP,
        /** 按 SpEL 表达式求值结果限流（如 upload 按 deviceCode）。 */
        SPEL,
        /** 全局，不区分调用方。 */
        GLOBAL
    }

    /** 逻辑名，用于拼 Redis key 前缀 rate:{name}:{dimensionValue}。 */
    String name();

    /** 窗口内允许的最大请求数。 */
    int limit();

    /** 滑动窗口长度（秒）。 */
    int window();

    Dimension dimension() default Dimension.IP;

    /** dimension=SPEL 时的取值表达式，对方法入参求值，如 "#sensorDataDTO.deviceCode"。 */
    String key() default "";

    /** 超限提示语。 */
    String message() default "请求过于频繁，请稍后再试";
}
