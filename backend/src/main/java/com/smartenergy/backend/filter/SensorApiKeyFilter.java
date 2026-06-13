package com.smartenergy.backend.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * ★ NH2 修复: 传感器上传接口的 X-Api-Key 认证过滤器
 *
 * 旧实现把 /api/sensor/upload 放在 permitAll 白名单,任何外网用户可注入伪造遥测。
 * 现要求请求必须携带 X-Api-Key header,且值匹配 app.sensor.api-key 配置项。
 *
 * 失败-关闭:若 app.sensor.api-key 未配置或为空,所有 /api/sensor/upload 请求一律拒绝。
 *
 * 数据泵端(data_pump.py)需要同步增加 X-Api-Key header,见 SENSOR_API_KEY 环境变量。
 */
@Slf4j
@Component
public class SensorApiKeyFilter extends OncePerRequestFilter {

    private static final String SENSOR_UPLOAD_PATH = "/api/sensor/upload";
    private static final String API_KEY_HEADER = "X-Api-Key";

    @Value("${app.sensor.api-key:}")
    private String expectedApiKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (uri == null || !uri.startsWith(SENSOR_UPLOAD_PATH)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 服务端未配置 API Key → 拒绝(防止配置遗漏导致裸奔)
        if (!StringUtils.hasText(expectedApiKey)) {
            log.warn("[SensorApiKey] app.sensor.api-key 未配置, 拒绝所有传感器上传请求");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Sensor upload disabled: server API key missing");
            return;
        }

        String providedKey = request.getHeader(API_KEY_HEADER);
        if (!expectedApiKey.equals(providedKey)) {
            log.warn("[SensorApiKey] 上传请求 API Key 不匹配, remote={}, key=[{}]",
                    request.getRemoteAddr(),
                    providedKey == null ? "<null>" : "<provided>");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid X-Api-Key");
            return;
        }

        // 通过 → 设置匿名"sensor"身份, 避开后续 JWT 检查同时保留审计痕迹
        Authentication auth = new AnonymousAuthenticationToken(
                "sensor-api-key",
                "sensor-system",
                List.of(new SimpleGrantedAuthority("ROLE_SENSOR")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        filterChain.doFilter(request, response);
    }
}
