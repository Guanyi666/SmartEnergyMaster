package com.smartenergy.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 维修人员员工档案（v4 P0-1 新增：映射 maintenance_personnel 表）。
 * 用于保存人员的人事系统信息（姓名/电话/邮箱/技能/等级/证书），
 * 与 {@link MaintenancePersonnel}（排班业务档案）通过 user_id 关联 sys_user 形成两套档案。
 */
@Data
@TableName(value = "maintenance_personnel", autoResultMap = false)
public class MaintenancePersonnelArchive {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键 ID（自增）")
    private Long id;

    @Schema(description = "关联 sys_user.id")
    private Integer userId;

    @Schema(description = "工号（唯一）", example = "E001")
    private String employeeNo;

    @Schema(description = "姓名")
    private String name;

    @Schema(description = "联系电话")
    private String phone;

    @Schema(description = "邮箱")
    private String email;

    @TableField(typeHandler = com.smartenergy.backend.handler.JsonbTypeHandler.class)
    @Schema(description = "技能标签 JSON 字符串", hidden = true)
    private String specializations;

    @Schema(description = "技能等级：JUNIOR/INTERMEDIATE/SENIOR/EXPERT", example = "EXPERT")
    private String skillLevel;

    @Schema(description = "证书描述")
    private String certification;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
