package com.smartenergy.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginSessionService {

    // v5: 30 → 15 分钟，作为"用户关闭页面后未调 logout"的兜底过期时间
    private static final Duration SESSION_TTL = Duration.ofMinutes(15);
    private static final String KEY_PREFIX = "login:session:";

    private final StringRedisTemplate redisTemplate;

    public boolean claim(String username, String token) {
        try {
            Boolean claimed = redisTemplate.opsForValue()
                    .setIfAbsent(key(username), token, SESSION_TTL);
            return Boolean.TRUE.equals(claimed);
        } catch (Exception exception) {
            log.error("登录会话互斥检查失败 username={}: {}", username, exception.getMessage());
            throw new IllegalStateException("登录会话服务暂不可用，请稍后重试");
        }
    }

    /**
     * v5 改造：只校验不续期。
     * - 原因：旧版每次 API 调用都 expire() 续期，导致用户关闭页面后 session 永不超时
     * - 改后：session 严格 15 分钟自然过期；前端通过 beforeunload + sendBeacon 主动 logout
     * - Redis 异常时降级为 JWT 校验（保持原行为，避免 Redis 故障导致全员登出）
     */
    public boolean validateAndRefresh(String username, String token) {
        try {
            String activeToken = redisTemplate.opsForValue().get(key(username));
            if (!token.equals(activeToken)) {
                return false;
            }
            // ⚠️ 不再 expire() 续期，让 TTL 自然到期
            return true;
        } catch (Exception exception) {
            log.warn("登录会话校验失败，降级为 JWT 校验 username={}: {}", username, exception.getMessage());
            return true;
        }
    }

    public void release(String username, String token) {
        try {
            String key = key(username);
            String activeToken = redisTemplate.opsForValue().get(key);
            if (token.equals(activeToken)) {
                redisTemplate.delete(key);
            }
        } catch (Exception exception) {
            log.warn("登录会话释放失败 username={}: {}", username, exception.getMessage());
        }
    }

    private String key(String username) {
        return KEY_PREFIX + username;
    }
}
