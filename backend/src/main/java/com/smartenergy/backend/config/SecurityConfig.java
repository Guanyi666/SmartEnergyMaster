package com.smartenergy.backend.config;

import com.smartenergy.backend.filter.JwtAuthenticationFilter;
import com.smartenergy.backend.filter.SensorApiKeyFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final SensorApiKeyFilter sensorApiKeyFilter;

    /**
     * ★ NH3: Swagger 公开开关. 默认 false(prod 安全).
     * dev/test 环境可通过 app.security.swagger-public=true 打开
     */
    @Value("${app.security.swagger-public:false}")
    private boolean swaggerPublic;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    // ★ NH3: Swagger UI & API docs — 默认要求 ADMIN, dev profile 可打开 swaggerPublic 公开
                    if (swaggerPublic) {
                        auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html").permitAll()
                            .requestMatchers("/v3/api-docs/**", "/swagger-resources/**", "/webjars/**").permitAll();
                    } else {
                        auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html").hasRole("ADMIN")
                            .requestMatchers("/v3/api-docs/**", "/swagger-resources/**", "/webjars/**").hasRole("ADMIN");
                    }
                    auth
                            // ★ 健康检查端点 — 公开,供 Docker/K8s healthcheck (轻量,不暴露业务数据)
                            .requestMatchers("/api/health", "/api/health/**").permitAll()
                            // 仅登录接口允许匿名访问，账号由人事管理员或系统管理员创建
                            .requestMatchers("/api/auth/login").permitAll()
                            // v5: logout 也允许匿名，因为 sendBeacon 不能设 Authorization header
                            .requestMatchers("/api/auth/logout").permitAll()
                            // ★ NH2: /api/sensor/upload 仍 permitAll, 由 SensorApiKeyFilter 在更早层做 X-Api-Key 校验
                            //   未携带 / 错误的 key 在过滤器层就被 401 拒绝, 不会进 Controller
                            .requestMatchers("/api/sensor/upload").permitAll()
                            // ★ NH2: /api/sensor/latest/** 和 /api/sensor/history/** 改为需要 JWT 认证
                            //   前端调用时本来就带 Bearer Token, 影响面 = 数据泵之外的"裸读"路径被堵
                            // 🆕 合并 workorder-backend: 删除了 PATCH /api/work-orders/*/status 的 permitAll
                            //   原因：8081 合并后不再有跨进程 HTTP 调用，"信任区"前提消失
                            //   改由 8080 内部的 WorkOrderSyncService 本地同步，无 HTTP 入口
                            // 🆕 /api/workorder/** 不在白名单，自动走 anyRequest().authenticated() 走 JWT
                            .anyRequest().authenticated();
                })
                // ★ NH2: SensorApiKeyFilter 必须比 JwtAuthenticationFilter 先执行
                //   Spring Security 的 addFilterBefore 要求第二个参数是已注册的标准过滤器,
                //   因此两个自定义 filter 都用 UsernamePasswordAuthenticationFilter 作为锚点,
                //   按注册顺序在链中排列: sensorApiKeyFilter 先注册 → 链中先执行
                .addFilterBefore(sensorApiKeyFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Api-Key"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
