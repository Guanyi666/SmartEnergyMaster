package com.smartenergy.backend.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录响应")
public class LoginVO {

    @Schema(description = "JWT Token（24h 有效）", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String token;

    @Schema(description = "账号", example = "2026010001")
    private String username;

    @Schema(description = "角色（ADMIN / OPERATOR）", example = "ADMIN")
    private String role;

    @Schema(description = "姓名")
    private String nickname;

    @Schema(description = "所属部门")
    private String department;
}
