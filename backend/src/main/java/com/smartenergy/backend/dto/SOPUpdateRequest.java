package com.smartenergy.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "更新 SOP 请求")
public class SOPUpdateRequest {

    @NotBlank(message = "SOP 标题不能为空")
    @Schema(description = "SOP 标题")
    private String title;

    @Schema(description = "SOP 简介")
    private String summary;

    @NotBlank(message = "SOP 内容不能为空")
    @Schema(description = "SOP 完整内容")
    private String content;

    @Schema(description = "操作步骤列表")
    private List<String> steps;

    @Schema(description = "所需技能")
    private List<String> requiredSkills;

    @Schema(description = "所需工具")
    private List<String> requiredTools;

    @Schema(description = "所需备件")
    private List<String> requiredParts;

    @Schema(description = "预计耗时（分钟）")
    private Integer estimatedMinutes;

    @Schema(description = "是否启用")
    private Boolean isActive;
}