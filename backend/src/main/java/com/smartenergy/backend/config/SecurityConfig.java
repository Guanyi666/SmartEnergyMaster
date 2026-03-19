package com.smartenergy.backend.config;

/**
 * @author Duan Guanyi
 * @version 1.0.0
 * @date 2026/3/18
 */

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 访问控制类
 */

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. 关闭 CSRF 防护（前后端分离项目使用 Token，不需要这个）
                .csrf(AbstractHttpConfigurer::disable)
                // 2. 禁用 Session（因为我们要用无状态的 JWT Token）
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 3. 配置拦截规则
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll() // 放行登录和注册接口

                        .requestMatchers("/api/sensor/upload").permitAll()  // 放行硬件数据上传接口

                        // 测试用
                        .requestMatchers("/api/sensor/latest/**", "/api/sensor/history/**").permitAll()

                        .anyRequest().authenticated()                // 其他所有接口都必须带 Token 才能访问
                );

        return http.build();
    }

    // 配置密码加密器 (采用强散列哈希加密)
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 暴露 AuthenticationManager，后面登录接口要用它来做身份认证
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
