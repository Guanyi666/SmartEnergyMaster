package com.smartenergy.backend.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 人员管理合并视图（v6 改造）：
 * 主表 sys_user，LEFT JOIN maintenance_personnel（员工档案）和 workorder_maintenance_personnel（维修排班）
 * 统一展示账号、身份、档案、维修人员信息
 */
@Data
@Schema(description = "人员管理合并视图（账号 + 维修人员档案 + 排班）")
public class UserWithPersonnelVO {

    // ===== 来自 sys_user =====
    @Schema(description = "用户 ID")
    private Integer id;

    @Schema(description = "登录账号（也是 employee_no）", example = "2026030001")
    private String username;

    @Schema(description = "姓名（sys_user.nickname）")
    private String nickname;

    @Schema(description = "角色")
    private String role;

    @Schema(description = "部门")
    private String department;

    @Schema(description = "手机号（sys_user.phone）")
    private String phone;

    @Schema(description = "邮箱（sys_user.email）")
    private String email;

    @Schema(description = "账号状态：ACTIVE / DISABLED / LOCKED")
    private String status;

    @Schema(description = "最后登录时间")
    private LocalDateTime lastLoginAt;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    // ===== 是否维修人员的标志 =====
    @Schema(description = "是否是维修人员（role=MAINTENANCE_ENGINEER 或有维修档案/排班）")
    private Boolean isMaintenance;

    // ===== 来自 maintenance_personnel（员工档案）=====
    @Schema(description = "工号（maintenance_personnel.employee_no）")
    private String employeeNo;

    @Schema(description = "姓名（maintenance_personnel.name）")
    private String archiveName;

    @Schema(description = "手机号（maintenance_personnel.phone）")
    private String archivePhone;

    @Schema(description = "邮箱（maintenance_personnel.email）")
    private String archiveEmail;

    @Schema(description = "技能标签 JSON 字符串")
    private String specializations;

    @Schema(description = "技能等级：JUNIOR/INTERMEDIATE/SENIOR/EXPERT")
    private String skillLevel;

    @Schema(description = "证书描述")
    private String certification;

    // ===== 来自 workorder_maintenance_personnel（维修排班）=====
    @Schema(description = "头像底色")
    private String avatarColor;

    @Schema(description = "当前在处理工单数")
    private Integer currentWorkload;

    @Schema(description = "最大并行处理数")
    private Integer maxWorkload;

    @Schema(description = "负载率（百分比）")
    private Integer workloadRate;

    @Schema(description = "是否在岗")
    private Boolean isOnDuty;
}
