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

    private static final Duration SESSION_TTL = Duration.ofMinutes(30);
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

    public boolean validateAndRefresh(String username, String token) {
        try {
            String activeToken = redisTemplate.opsForValue().get(key(username));
            if (!token.equals(activeToken)) {
                return false;
            }
            redisTemplate.expire(key(username), SESSION_TTL);
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
