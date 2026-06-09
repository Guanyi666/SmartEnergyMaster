package com.smartenergy.backend.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
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
public class RedisCacheServiceImpl implements CacheService {

    /** 缓存重建互斥锁的默认等待时间 */
    private static final Duration REBUILD_LOCK_TTL = Duration.ofSeconds(5);

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    public RedisCacheServiceImpl(RedisTemplate<String, Object> redisTemplate,
                                  StringRedisTemplate stringRedisTemplate) {
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // ==================== 基础读写 ====================

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
            // NOTE: KEYS 是 O(N) 阻塞命令。当前缓存 key 极少（~10 个），无性能风险。
            // key 数量 > 1000 后需改用 SCAN 游标迭代，避免阻塞 Redis 主线程。
            Set<String> keys = redisTemplate.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("缓存按前缀删除失败 prefix={}: {}", prefix, e.getMessage());
        }
    }

    // ==================== 读穿透 + 缓存击穿保护 ====================

    @Override
    public <T> T getOrLoad(String key, Duration ttl, Supplier<T> loader) {
        T cached = get(key);
        if (cached != null) {
            return cached;
        }

        // 用 Redis SET NX 做互斥重建锁，防止并发 miss 时多个线程同时回源 DB。
        String rebuildLockKey = "cache:rebuild:" + key;
        boolean locked = tryAcquireRebuildLock(rebuildLockKey);
        if (locked) {
            try {
                // double-check：持锁后再次读缓存（上一持锁者可能刚写完）
                T doubleCheck = get(key);
                if (doubleCheck != null) {
                    return doubleCheck;
                }
                T value = loader.get();
                if (value != null) {
                    set(key, value, ttl);
                }
                return value;
            } finally {
                releaseRebuildLock(rebuildLockKey);
            }
        }

        // 未抢到重建锁 → 等待一小段时间让持锁者完成重建，再试一次缓存
        try {
            Thread.sleep(60);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        T retry = get(key);
        if (retry != null) {
            return retry;
        }
        // 最终兜底：持锁者可能失败了，自己再回源一次
        T value = loader.get();
        if (value != null) {
            set(key, value, ttl);
        }
        return value;
    }

    private boolean tryAcquireRebuildLock(String lockKey) {
        try {
            Boolean ok = stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", REBUILD_LOCK_TTL);
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            log.warn("缓存重建锁获取失败 lockKey={}: {}", lockKey, e.getMessage());
            // 锁获取异常时降级为直接回源
            return true;
        }
    }

    private void releaseRebuildLock(String lockKey) {
        try {
            stringRedisTemplate.delete(lockKey);
        } catch (Exception e) {
            log.warn("缓存重建锁释放失败 lockKey={}: {}", lockKey, e.getMessage());
        }
    }
}
