package com.smartenergy.backend.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 分布式锁 (Epic 11-3). 基于 Redis SET NX，释放用 Lua 保证只删自己的锁。
 * 抢锁失败抛 {@code LockAcquireException}(HTTP 423)。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    /** 逻辑名，用于拼 Redis key 前缀 lock:{name}:{key}。 */
    String name();

    /** 锁粒度，对方法入参求值的 SpEL，如 "#sensorDataDTO.deviceCode"。 */
    String key();

    /** 抢锁最大等待时间（毫秒），期间自旋重试；0 表示拿不到立刻失败。 */
    long waitMillis() default 0;

    /** 锁的持有时长/自动过期（毫秒），防止持锁线程崩溃导致死锁。 */
    long leaseMillis() default 5000;

    /** 抢锁失败提示语。 */
    String message() default "操作正在处理中，请勿重复提交";
}
