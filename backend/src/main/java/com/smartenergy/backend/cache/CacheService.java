package com.smartenergy.backend.cache;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 缓存抽象 (Epic 11-2-2). 业务代码只依赖此接口，底层换 Redis/本地不影响调用方。
 */
public interface CacheService {

    /** 写入并设置过期时间。 */
    void set(String key, Object value, Duration ttl);

    /** 读取，未命中或已过期返回 null。返回值已是原始类型（依赖序列化器的类型信息）。 */
    <T> T get(String key);

    /** 删除单个 key。 */
    void evict(String key);

    /** 按前缀批量删除（用于 device:latest:* 这类带通配的失效）。 */
    void evictByPrefix(String prefix);

    /**
     * 读穿透：命中直接返回；未命中调用 loader 回源，结果非 null 时写入缓存。
     * loader 返回 null 不缓存（避免把"无数据"长期固化，简单实现不做空值穿透保护）。
     */
    <T> T getOrLoad(String key, Duration ttl, Supplier<T> loader);
}
