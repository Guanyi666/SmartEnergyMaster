package com.smartenergy.backend.vo;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "SOP 详情视图")
public class SOPDetailVO {

    @Schema(description = "SOP ID")
    private Long id;

    @Schema(description = "SOP 编号")
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

    @Schema(description = "操作步骤列表（v4：从子表读，List<String>）")
    private List<String> steps;

    @Schema(description = "所需技能（JSON 数组，仍是 JsonNode）")
    private JsonNode requiredSkills;

    @Schema(description = "所需工具（JSON 数组，仍是 JsonNode）")
    private JsonNode requiredTools;

    @Schema(description = "所需备件列表（v4：从子表读，List<String> 即 part_code 列表）")
    private List<String> requiredParts;

    @Schema(description = "预计耗时（分钟）")
    private Integer estimatedMinutes;

    @Schema(description = "版本号")
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