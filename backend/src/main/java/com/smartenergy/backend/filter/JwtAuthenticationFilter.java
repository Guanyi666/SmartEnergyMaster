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

import java.io.IOException;

/**
 * @author Duan Guanyi
 * @version 1.0.0
 * @date 2026/3/18
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private UserDetailsService userDetailsService; // 注入我们自己写的 UserDetailsServiceImpl

    private static final byte[] JWT_KEY = "SmartEnergyMasterSecretKey".getBytes();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. 从请求头提取 Authorization 字段
        String header = request.getHeader("Authorization");
        String token = null;

        // 规范：前端传 Token 时需要加上 "Bearer " 前缀
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            token = header.substring(7);
        }

        // 2. 校验 Token
        if (StringUtils.hasText(token) && JWTUtil.verify(token, JWT_KEY)) {
            // 解析 Token 中的 payload
            JWT jwt = JWTUtil.parseToken(token);
            String username = (String) jwt.getPayload("username");

            // 如果上下文中还没有认证信息，则进行手动装配
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // 去数据库加载用户的权限信息
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // 构造合法的身份认证令牌
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // 将令牌存入安全上下文，Spring Security 就知道这个请求是合法用户发出的了
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        // 3. 放行请求，继续执行后续 Filter 或 Controller
        filterChain.doFilter(request, response);
    }
}
