package com.smartenergy.backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * 健康检查端点 (公开,无需认证).
 *
 * ★ 用途: 供 Docker / K8s healthcheck 使用. 旧版健康检查曾用 /api/sensor/latest/**,
 *   NH2 修复后该端点改为需要 JWT,导致 docker healthcheck 拿不到 200,容器永远 starting.
 *   本控制器提供独立的轻量端点,只确认 Spring 容器存活,不暴露任何业务数据.
 */
@RestController
@RequestMapping("/api/health")
@Tag(name = "Health", description = "服务存活探针")
public class HealthController {

    @GetMapping
    @Operation(summary = "存活探针", description = "始终返回 200 + 时间戳,供 Docker/K8s healthcheck 使用")
    public Map<String, Object> liveness() {
        return Map.of(
                "status", "UP",
                "service", "smart-energy-backend",
                "timestamp", Instant.now().toString()
        );
    }
}
