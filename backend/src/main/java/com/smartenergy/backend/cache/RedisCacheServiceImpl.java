package com.smartenergy.backend.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 基于 Redis 的 {@link CacheService} 实现。
 * 任何缓存层异常都降级为"直接回源"，缓存故障不影响主流程可用性。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisCacheServiceImpl implements CacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void set(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            log.warn("缓存写入失败 key={}: {}", key, e.getMessage());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        try {
            return (T) redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("缓存读取失败 key={}: {}", key, e.getMessage());
            return null;
        }
    }

    @Override
    public void evict(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("缓存删除失败 key={}: {}", key, e.getMessage());
        }
    }

    @Override
    public void evictByPrefix(String prefix) {
        try {
            Set<String> keys = redisTemplate.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("缓存按前缀删除失败 prefix={}: {}", prefix, e.getMessage());
        }
    }

    @Override
    public <T> T getOrLoad(String key, Duration ttl, Supplier<T> loader) {
        T cached = get(key);
        if (cached != null) {
            return cached;
        }
        T value = loader.get();
        if (value != null) {
            set(key, value, ttl);
        }
        return value;
    }
}
