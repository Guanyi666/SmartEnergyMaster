package com.smartenergy.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AccountSettingsRequest {

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱不能超过 128 个字符")
    private String email;

    @Size(max = 72, message = "当前密码不能超过 72 个字符")
    private String currentPassword;

    @Size(max = 72, message = "新密码不能超过 72 个字符")
    private String newPassword;
}
