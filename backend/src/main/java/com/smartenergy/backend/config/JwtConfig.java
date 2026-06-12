package com.smartenergy.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;

/**
 * JWT 统一配置 — 供签发端（UserServiceImpl）和校验端（JwtAuthenticationFilter）共用。
 * 绑定 application.yml 中 jwt.* 配置项，支持环境变量覆盖。
 */
@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtConfig {

    /** JWT 签名密钥（默认 SmartEnergyMasterSecretKey，生产通过 JWT_SECRET 环境变量覆盖） */
    private String secret = "SmartEnergyMasterSecretKey";

    /** Token 有效期（毫秒），默认 24h */
    private long expirationMs = 86400000L;

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

    /** 返回密钥的 UTF-8 字节数组（Hutool JWT 签名/验签使用） */
    public byte[] getKeyBytes() {
        return secret.getBytes(StandardCharsets.UTF_8);
    }
}
