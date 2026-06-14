package com.smartenergy.backend.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 维修人员完整视图（v4 P0-1 新增）。
 * Service 层通过两个 Mapper 分别查 workorder_maintenance_personnel 和 maintenance_personnel，
 * 然后用 BeanUtils.copyProperties 组装到本 VO。
 *
 * 包含：
 * - 排班业务字段（来自 MaintenancePersonnel）
 * - 员工档案字段（来自 MaintenancePersonnelArchive）
 */
@Data
@Schema(description = "维修人员完整视图（双表 JOIN）")
public class MaintenancePersonnelFullVO {

    @Schema(description = "主键 ID")
    private Long id;

    @Schema(description = "关联 sys_user.id")
    private Integer userId;

    // ===== 来自 maintenance_personnel（员工档案）=====
    @Schema(description = "姓名")
    private String name;

    @Schema(description = "联系电话")
    private String phone;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "技能标签 JSON 字符串", hidden = true)
    private String specializations;

    @Schema(description = "技能等级")
    private String skillLevel;

    @Schema(description = "证书描述")
    private String certification;

    // ===== 来自 workorder_maintenance_personnel（排班业务档案）=====
    @Schema(description = "头像底色")
    private String avatarColor;

    @Schema(description = "当前在处理工单数")
    private Integer currentWorkload;

    @Schema(description = "最大并行处理数")
    private Integer maxWorkload;

    @Schema(description = "是否在岗")
    private Boolean isOnDuty;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
