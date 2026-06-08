package com.smartenergy.backend.controller;

import com.smartenergy.backend.dto.LoginRequest;
import com.smartenergy.backend.dto.RegisterRequest;
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

@RestController
@RequestMapping("api/auth")
@RequiredArgsConstructor
@Tag(name = "认证管理", description = "用户注册与登录")
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "注册新用户，默认角色为 OPERATOR")
    public ResponseEntity<String> register(@Validated @RequestBody RegisterRequest registerRequest) {
        userService.register(registerRequest);
        return ResponseEntity.ok("注册成功");
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "验证用户名密码，返回 JWT Token（24h 有效）")
    public ResponseEntity<LoginVO> login(@Validated @RequestBody LoginRequest loginRequest) {
        return ResponseEntity.ok(userService.login(loginRequest));
    }
}
