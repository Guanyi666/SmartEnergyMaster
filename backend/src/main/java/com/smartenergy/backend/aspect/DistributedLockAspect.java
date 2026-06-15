package com.smartenergy.backend.aspect;

import com.smartenergy.backend.annotation.DistributedLock;
import com.smartenergy.backend.exception.LockAcquireException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/**
 * 分布式锁切面 (Epic 11-3). 加锁用 SET NX PX，解锁用 Lua 比对 token 再删，
 * 避免误删别人的锁；锁带 TTL，持锁线程崩溃也能自动释放。
 */
@Slf4j
@Aspect
@Component
@Order(2)
public class DistributedLockAspect {

    // 只有 token 匹配才删除，保证"解铃还须系铃人"
    private static final String UNLOCK_LUA = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """;

    private final RedisScript<Long> unlockScript = new DefaultRedisScript<>(UNLOCK_LUA, Long.class);
    private final StringRedisTemplate stringRedisTemplate;

    public DistributedLockAspect(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Around("@annotation(distributedLock)")
    public Object around(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {
        String lockKey = "lock:" + distributedLock.name() + ":"
                + SpelKeyResolver.resolve(joinPoint, distributedLock.key());
        String token = UUID.randomUUID().toString();

        if (!acquire(lockKey, token, distributedLock)) {
            log.warn("抢锁失败 key={}", lockKey);
            throw new LockAcquireException(distributedLock.message());
        }

        try {
            return joinPoint.proceed();
        } finally {
            release(lockKey, token);
        }
    }

    /**
     * 自旋尝试获取锁。当前业务场景 waitMillis ≤ 200ms（最多自旋 ~10 次，总阻塞 < 200ms）。
     * NOTE: 如果未来有接口配置 waitMillis > 2000，需改用 Redis Pub/Sub 或 Redisson
     * RLock.tryLock() 避免长时间阻塞 Tomcat worker 线程。
     */
    private boolean acquire(String lockKey, String token, DistributedLock lock) throws InterruptedException {
        Duration lease = Duration.ofMillis(lock.leaseMillis());
        long deadline = System.currentTimeMillis() + lock.waitMillis();
        do {
            Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, token, lease);
            if (Boolean.TRUE.equals(ok)) {
                return true;
            }
            if (lock.waitMillis() <= 0) {
                return false;
            }
            // waitMillis 较短（< 500ms）时自旋是合理的，避免引入额外的消息队列依赖
            Thread.sleep(20);
        } while (System.currentTimeMillis() < deadline);
        return false;
    }

    private void release(String lockKey, String token) {
        try {
            stringRedisTemplate.execute(unlockScript, Collections.singletonList(lockKey), token);
        } catch (DataAccessException | IllegalArgumentException e) {
            log.warn("释放锁失败 key={}: {}", lockKey, e.getMessage());
        }
    }
}
