package com.smartenergy.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 维修排班业务档案（v4：去掉冗余字段 name/phone/email/specializations/skill_level/certification，
 * 这些已迁移到 maintenance_personnel 表；新增 user_id 关联 sys_user）。
 */
@Data
@TableName(value = "workorder_maintenance_personnel", autoResultMap = false)
public class MaintenancePersonnel {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键 ID（自增）")
    private Long id;

    @Schema(description = "工号（唯一，登录账号）", example = "2026030001")
    private String employeeNo;

    @Schema(description = "头像底色（hex）", example = "#52c8ff")
    private String avatarColor;

    @Schema(description = "当前在处理工单数", example = "0")
    private Integer currentWorkload;

    @Schema(description = "最大并行处理数", example = "5")
    private Integer maxWorkload;

    @Schema(description = "是否在岗", example = "true")
    private Boolean isOnDuty;

    @Schema(description = "关联 sys_user.id（v4 新增，可空，E002-E006 暂未建账号）")
    private Integer userId;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
