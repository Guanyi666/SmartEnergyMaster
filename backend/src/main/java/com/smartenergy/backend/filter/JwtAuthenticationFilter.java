package com.smartenergy.backend.filter;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import com.smartenergy.backend.service.LoginSessionService;

import java.io.IOException;

/**
 * JWT 认证过滤器 — 校验 Token 签名 + 过期时间，装配 SecurityContext。
 *
 * JWT 密钥由 {@link com.smartenergy.backend.config.JwtConfig} 统一管理，
 * 确保签发端（UserServiceImpl）和校验端使用同一密钥。
 *
 * @author Duan Guanyi
 * @version 1.2.0
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private com.smartenergy.backend.config.JwtConfig jwtConfig;

    @Autowired
    private LoginSessionService loginSessionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);

        if (StringUtils.hasText(token)) {
            try {
                if (JWTUtil.verify(token, jwtConfig.getKeyBytes())) {
                    JWT jwt = JWTUtil.parseToken(token);

                    // ★ NM5 修复: 优先校验标准 exp (秒级), 回退到旧版 expire_time (毫秒)
                    //   exp 由 NM5 后的新 token 携带, expire_time 由旧 token 携带, 兼容 1 个版本周期
                    long nowMs = System.currentTimeMillis();
                    Object expStd = jwt.getPayload("exp");
                    Object expLegacy = jwt.getPayload("expire_time");
                    boolean expired;
                    if (expStd instanceof Number) {
                        // 标准 exp 是秒级
                        long expSec = ((Number) expStd).longValue();
                        expired = expSec * 1000L < nowMs;
                    } else if (expLegacy instanceof Number) {
                        // 旧 expire_time 是毫秒级
                        long expMs = ((Number) expLegacy).longValue();
                        expired = expMs < nowMs;
                    } else {
                        // 两个声明都没有 → 视为过期
                        expired = true;
                    }
                    if (expired) {
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token 已过期，请重新登录");
                        return;
                    }

                    String username = (String) jwt.getPayload("username");
                    if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                        if (!loginSessionService.validateAndRefresh(username, token)) {
                            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "该登录会话已失效，请重新登录");
                            return;
                        }
                        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                } else {
                    // ★ NC1 修复: JWTUtil.verify() 返回 false (签名无效/被篡改) 时显式拒绝,
                    //    防止 fall-through 到 filterChain.doFilter() 让伪造 token 继续未认证执行
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token 签名无效");
                    return;
                }
            } catch (Exception e) {
                // 签名错误 / 格式异常 / 解析失败 → 401
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token 无效");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
