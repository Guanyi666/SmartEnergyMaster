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

@RestController
@RequestMapping("api/auth")
@RequiredArgsConstructor
@Tag(name = "认证管理", description = "用户登录与退出")
public class AuthController {

    private final UserService userService;

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "验证用户名密码，返回 JWT Token（24h 有效）")
    @RateLimit(name = "login", limit = 3, window = 60, dimension = RateLimit.Dimension.IP,
            message = "登录尝试过于频繁，请 1 分钟后再试")
    public ResponseEntity<LoginVO> login(@Validated @RequestBody LoginRequest loginRequest) {
        return ResponseEntity.ok(userService.login(loginRequest));
    }

    @PostMapping("/logout")
    @Operation(summary = "退出登录", description = "释放当前账号的在线会话")
    public ResponseEntity<String> logout(
            Principal principal,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7) : "";
        userService.logout(principal == null ? null : principal.getName(), token);
        return ResponseEntity.ok("退出成功");
    }
}
