package com.smartenergy.backend.controller;

import com.smartenergy.backend.dto.AccountSettingsRequest;
import com.smartenergy.backend.service.UserService;
import com.smartenergy.backend.vo.AccountSettingsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.security.Principal;

@RestController
@RequestMapping("/api/account-settings")
@RequiredArgsConstructor
@Tag(name = "账号设置", description = "当前登录用户维护自己的联系方式和密码")
public class AccountSettingsController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "查询当前账号设置")
    public AccountSettingsVO get(Principal principal) {
        return userService.getAccountSettings(principal.getName());
    }

    @PutMapping
    @Operation(summary = "更新当前账号设置", description = "修改密码后当前登录会话立即失效")
    public AccountSettingsVO update(
            Principal principal,
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody AccountSettingsRequest request) {
        AccountSettingsVO result = userService.updateAccountSettings(principal.getName(), request);
        if (result.isPasswordChanged()) {
            String token = StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")
                    ? authorization.substring(7)
                    : null;
            userService.logout(principal.getName(), token);
        }
        return result;
    }
}
