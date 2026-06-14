package com.smartenergy.backend.aspect;

import com.smartenergy.backend.annotation.RateLimit;
import com.smartenergy.backend.exception.RateLimitException;
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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.UUID;

/**
 * 限流切面 (Epic 11-3). 滑动窗口日志算法：用 ZSET 存每次请求时间戳，
 * 窗口内计数 < limit 才放行，整段逻辑在 Lua 里原子执行。
 */
@Slf4j
@Aspect
@Component
@Order(1)
public class RateLimitAspect {

    // KEYS[1]=zsetKey ARGV[1]=now(ms) ARGV[2]=window(ms) ARGV[3]=limit ARGV[4]=member
    private static final String LUA = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
            local count = redis.call('ZCARD', key)
            if count < limit then
                redis.call('ZADD', key, now, ARGV[4])
                redis.call('PEXPIRE', key, window)
                return 1
            end
            return 0
            """;

    private final RedisScript<Long> script = new DefaultRedisScript<>(LUA, Long.class);
    private final StringRedisTemplate stringRedisTemplate;

    public RateLimitAspect(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String dimensionValue = resolveDimensionValue(joinPoint, rateLimit);
        String key = "rate:" + rateLimit.name() + ":" + dimensionValue;

        long now = System.currentTimeMillis();
        long windowMillis = rateLimit.window() * 1000L;
        String member = now + "-" + UUID.randomUUID();

        Long allowed;
        try {
            allowed = stringRedisTemplate.execute(script, Collections.singletonList(key),
                    String.valueOf(now), String.valueOf(windowMillis),
                    String.valueOf(rateLimit.limit()), member);
        } catch (DataAccessException | IllegalArgumentException e) {
            // 限流组件异常时放行，不因 Redis 抖动阻断正常业务
            log.warn("限流脚本执行失败，放行 key={}: {}", key, e.getMessage());
            return joinPoint.proceed();
        }

        if (allowed == null || allowed == 0L) {
            log.warn("触发限流 key={} limit={}/{}s", key, rateLimit.limit(), rateLimit.window());
            throw new RateLimitException(rateLimit.message());
        }
        return joinPoint.proceed();
    }

    private String resolveDimensionValue(ProceedingJoinPoint joinPoint, RateLimit rateLimit) {
        return switch (rateLimit.dimension()) {
            case IP -> clientIp();
            case SPEL -> SpelKeyResolver.resolve(joinPoint, rateLimit.key());
            case GLOBAL -> "all";
        };
    }

    private String clientIp() {
        RequestAttributes rawAttrs = RequestContextHolder.getRequestAttributes();
        if (!(rawAttrs instanceof ServletRequestAttributes attrs)) {
            return "unknown";
        }
        HttpServletRequest request = attrs.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
