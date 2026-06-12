package com.smartenergy.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserUpsertRequest {

    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[12]\\d{3}\\d{2}(?!0000)\\d{4}$",
            message = "账号格式必须为：入职年份 + 两位身份标识 + 四位顺序号，例如 2026010001")
    private String username;

    @Size(max = 72, message = "密码不能超过 72 个字符")
    private String password;

    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "^(ADMIN|HR_MANAGER|MANAGER|OPERATOR|DEVICE_MANAGER|MAINTENANCE_ENGINEER)$",
            message = "角色不合法")
    private String role;

    @Size(max = 64, message = "姓名不能超过 64 个字符")
    private String nickname;

    @Size(max = 64, message = "部门不能超过 64 个字符")
    private String department;

    @Size(max = 32, message = "手机号不能超过 32 个字符")
    private String phone;

    @Size(max = 128, message = "邮箱不能超过 128 个字符")
    private String email;

    @Valid
    private MaintenanceProfileRequest maintenanceProfile;
}
