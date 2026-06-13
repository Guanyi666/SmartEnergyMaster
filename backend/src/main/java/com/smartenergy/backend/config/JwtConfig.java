package com.smartenergy.backend.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * JWT 统一配置 — 供签发端（UserServiceImpl）和校验端（JwtAuthenticationFilter）共用。
 * 绑定 application.yml 中 jwt.* 配置项，支持环境变量覆盖。
 *
 * ★ NH1 修复 (2026-06-13): 启动时强制校验密钥强度,防止默认弱密钥进入生产环境。
 * 校验失败立即 fail-fast 抛 IllegalStateException 阻止应用启动。
 */
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtConfig {

    /** ★ NH1: 默认密钥仅用于本地开发,启动校验会拒绝它进入生产 */
    private static final String DEFAULT_DEV_SECRET = "SmartEnergyMasterSecretKey";

    /** ★ NH1: HS256 推荐密钥长度至少 256 bit (32 字节),HS384/HS512 更长 */
    private static final int MIN_SECRET_BYTES = 32;

    /** ★ NH1: 已知弱密钥黑名单 — 防止"123456"之类的明显薄弱 */
    private static final Set<String> KNOWN_WEAK_SECRETS = Set.of(
            DEFAULT_DEV_SECRET,
            "secret", "changeme", "password", "123456",
            "smart-energy-secret", "smart_energy_secret"
    );

    /** JWT 签名密钥（默认仅开发用，生产必须通过 JWT_SECRET 环境变量覆盖为 >= 32 字节随机串） */
    private String secret = DEFAULT_DEV_SECRET;

    /** Token 有效期（毫秒），默认 24h */
    private long expirationMs = 86400000L;

    /** ★ NH1: 是否允许弱密钥(仅本地开发可设 true), 默认 false 强制 fail-fast */
    private boolean allowWeakSecret = false;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public void setExpirationMs(long expirationMs) {
        this.expirationMs = expirationMs;
    }

    public boolean isAllowWeakSecret() {
        return allowWeakSecret;
    }

    public void setAllowWeakSecret(boolean allowWeakSecret) {
        this.allowWeakSecret = allowWeakSecret;
    }

    /** 返回密钥的 UTF-8 字节数组（Hutool JWT 签名/验签使用） */
    public byte[] getKeyBytes() {
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * ★ NH1: 启动时校验 JWT 密钥强度。
     * 校验规则:
     *   1. 密钥不能为空
     *   2. UTF-8 字节长度 >= 32 (HS256 推荐)
     *   3. 不能命中已知弱密钥黑名单
     * 任何规则失败 → 抛 IllegalStateException 阻止应用启动。
     * 若必须使用弱密钥(仅本地开发),可设置 jwt.allow-weak-secret=true 跳过校验。
     */
    @PostConstruct
    public void validate() {
        if (allowWeakSecret) {
            log.warn("[JwtConfig] ⚠ jwt.allow-weak-secret=true, 已跳过密钥强度校验. " +
                    "此配置仅供本地开发, 生产环境绝不能启用!");
            return;
        }
        if (secret == null || secret.isEmpty()) {
            throw new IllegalStateException(
                    "[JwtConfig] jwt.secret 未配置 — 请通过 JWT_SECRET 环境变量提供 >= 32 字节的随机密钥");
        }
        int byteLen = secret.getBytes(StandardCharsets.UTF_8).length;
        if (byteLen < MIN_SECRET_BYTES) {
            throw new IllegalStateException(String.format(
                    "[JwtConfig] jwt.secret 长度不足 (%d 字节, 需要 >= %d 字节) — " +
                    "请通过 JWT_SECRET 环境变量提供更长的随机密钥. " +
                    "推荐生成方式: openssl rand -base64 48",
                    byteLen, MIN_SECRET_BYTES));
        }
        if (KNOWN_WEAK_SECRETS.contains(secret)) {
            throw new IllegalStateException(
                    "[JwtConfig] jwt.secret 命中已知弱密钥黑名单 — " +
                    "请通过 JWT_SECRET 环境变量提供独立的随机密钥. " +
                    "推荐生成方式: openssl rand -base64 48");
        }
        log.info("[JwtConfig] JWT 密钥强度校验通过 ({} bytes)", byteLen);
    }
}
