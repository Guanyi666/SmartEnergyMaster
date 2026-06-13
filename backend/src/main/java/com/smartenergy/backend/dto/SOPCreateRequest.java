package com.smartenergy.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "创建 SOP 请求")
public class SOPCreateRequest {

    @NotBlank(message = "SOP 编号不能为空")
    @Schema(description = "SOP 编号（业务唯一）", example = "SOP-ARC-MECH-001")
    private String sopCode;

    @NotBlank(message = "设备类型不能为空")
    @Schema(description = "适用设备类型", example = "ARC_FURNACE")
    private String deviceType;

    @NotBlank(message = "故障类型不能为空")
    @Schema(description = "适用故障类型", example = "MECHANICAL_JAM")
    private String faultType;

    @NotBlank(message = "SOP 标题不能为空")
    @Schema(description = "SOP 标题")
    private String title;

    @Schema(description = "SOP 简介")
    private String summary;

    @NotBlank(message = "SOP 内容不能为空")
    @Schema(description = "SOP 完整内容（Markdown）")
    private String content;

    @Schema(description = "操作步骤列表")
    private List<String> steps;

    @Schema(description = "所需技能")
    private List<String> requiredSkills;

    @Schema(description = "所需工具")
    private List<String> requiredTools;

    @Schema(description = "所需备件")
    private List<String> requiredParts;

    @Positive(message = "预计耗时必须为正数")
    @Schema(description = "预计耗时（分钟）", example = "120")
    private Integer estimatedMinutes;

    @Schema(description = "是否启用", example = "true")
    private Boolean isActive;

    @Schema(description = "创建人账号", example = "2026010001")
    private String createdBy;
}
