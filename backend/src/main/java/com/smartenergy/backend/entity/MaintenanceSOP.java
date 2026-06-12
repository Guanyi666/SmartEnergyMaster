package com.smartenergy.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("maintenance_sop")
@Schema(description = "维修标准操作规程实体")
public class MaintenanceSOP {

    @TableId(type = IdType.AUTO)
    @Schema(description = "SOP ID")
    private Long id;

    @Schema(description = "SOP 编号（业务唯一）")
    private String sopCode;

    @Schema(description = "适用设备类型")
    private String deviceType;

    @Schema(description = "适用故障类型")
    private String faultType;

    @Schema(description = "SOP 标题")
    private String title;

    @Schema(description = "SOP 简介")
    private String summary;

    @Schema(description = "SOP 完整内容（Markdown）")
    private String content;

    @Schema(description = "操作步骤列表（JSON 数组）")

    private String steps;

    @Schema(description = "所需技能（JSON 数组）")

    private String requiredSkills;

    @Schema(description = "所需工具（JSON 数组）")

    private String requiredTools;

    @Schema(description = "所需备件（JSON 数组）")

    private String requiredParts;

    @Schema(description = "预计耗时（分钟）")
    private Integer estimatedMinutes;

    @Schema(description = "版本号，每次更新 +1")
    private Integer version;

    @Schema(description = "是否启用")
    private Boolean isActive;

    @Schema(description = "创建人")
    private String createdBy;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
