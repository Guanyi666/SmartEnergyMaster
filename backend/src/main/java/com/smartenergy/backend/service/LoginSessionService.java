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
     *
     * ★ 架构#1 修复 (2026-06-13): Redis 故障改为 fail-closed (旧版 fail-open 让已登出 token
     *   在 Redis 故障期仍可继续使用). 修复后:
     *   - Redis 正常 → 严格校验 active token 匹配
     *   - Redis 故障 → 返回 false → 强制重新登录 (保护已登出会话不被复活)
     *   代价: Redis 故障时所有用户被踢, 但这是安全 vs 可用性的正确权衡
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
            // ★ 架构#1: fail-closed — Redis 故障时拒绝, 避免已登出 token 在故障窗口复活
            log.error("登录会话校验失败 (Redis 异常), fail-closed 拒绝请求 username={}: {}",
                    username, exception.getMessage());
            return false;
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
