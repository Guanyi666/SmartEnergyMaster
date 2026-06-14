package com.smartenergy.backend.controller;

import com.smartenergy.backend.annotation.RateLimit;
import com.smartenergy.backend.dto.LoginRequest;
import com.smartenergy.backend.service.UserService;
import com.smartenergy.backend.vo.LoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("api/auth")
@RequiredArgsConstructor
@Tag(name = "认证管理", description = "用户登录与退出")
public class AuthController {

    private final UserService userService;

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "验证用户名密码，返回 JWT Token（24h 有效）")
    // 同 IP 60 秒内最多 30 次登录尝试. 旧版 3 次/60s 对开发场景(连续测多账号)过于严格,
    // 30 次仍能防爆破(假设字典攻击需要数千次,会被快速锁定),但允许日常测试 6+ 个账号通畅切换。
    // prod 部署如需更严格,可通过 RateLimit 注解的 limit 参数调整 (或日后改为读 @Value).
    @RateLimit(name = "login", limit = 30, window = 60, dimension = RateLimit.Dimension.IP,
            message = "登录尝试过于频繁，请 1 分钟后再试")
    public ResponseEntity<LoginVO> login(@Validated @RequestBody LoginRequest loginRequest) {
        return ResponseEntity.ok(userService.login(loginRequest));
    }

    /**
     * v5 改造：logout 同时支持从 Authorization header 和 body 取 token。
     * - 原因：前端 beforeunload 配合 navigator.sendBeacon 调 logout，
     *   sendBeacon 无法设 Authorization header，所以需要从 body 传 token + username
     */
    @PostMapping("/logout")
    @Operation(summary = "退出登录", description = "释放当前账号的在线会话")
    public ResponseEntity<String> logout(
            Principal principal,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) Map<String, String> body) {
        String token = null;
        if (authorization != null && authorization.startsWith("Bearer ")) {
            token = authorization.substring(7);
        } else if (body != null && body.get("token") != null) {
            token = body.get("token");
        }
        // username：优先用 Principal（来自 SecurityContext 鉴权），fallback 到 body
        String username = principal != null ? principal.getName() : null;
        if (username == null && body != null) {
            username = body.get("username");
        }
        userService.logout(username, token);
        return ResponseEntity.ok("退出成功");
    }
}
