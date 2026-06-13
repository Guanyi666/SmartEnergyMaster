package com.smartenergy.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
@Schema(description = "系统用户实体")
public class SysUser {

    @TableId(type = IdType.AUTO)
    @Schema(description = "用户 ID（自增主键）")
    private Integer id;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "密码（BCrypt 加密）")
    private String password;

    @Schema(description = "角色")
    private String role;

    @Schema(description = "姓名")
    private String nickname;

    @Schema(description = "所属部门")
    private String department;

    private String phone;
    private String email;
    private String avatarUrl;
    private String status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
