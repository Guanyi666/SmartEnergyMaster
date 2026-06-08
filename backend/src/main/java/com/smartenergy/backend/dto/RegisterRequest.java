package com.smartenergy.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "用户注册请求")
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Schema(description = "用户名", example = "operator01")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, message = "密码长度至少为 6 位")
    @Schema(description = "密码，至少 6 位", example = "123456")
    private String password;

    @Email(message = "邮箱格式不正确")
    @Schema(description = "邮箱", example = "operator@example.com")
    private String email;
}
