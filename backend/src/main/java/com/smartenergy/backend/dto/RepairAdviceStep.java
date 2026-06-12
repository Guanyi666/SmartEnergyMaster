package com.smartenergy.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "AI 维修建议的单个步骤（含与 SOP 的源引用）")
public class RepairAdviceStep {

    @Schema(description = "步骤序号（从 1 开始）")
    private Integer order;

    @Schema(description = "建议动作文本")
    private String action;

    @Schema(description = "来源 SOP ID（null = AI 推断，无对应 SOP 步骤）")
    private Long sourceSopId;

    @Schema(description = "来源 SOP 编号")
    private String sourceSopCode;

    @Schema(description = "来源 SOP 在 steps 数组中的下标（从 0 开始），-1 = AI 推断")
    private Integer sourceStepIndex;

    @Schema(description = "0~1 的置信度")
    private Double confidence;

    @Schema(description = "是否 AI 衍生（非 SOP 原文）")
    private Boolean aiDerived;

    @Schema(description = "AI 给出该步骤的理由（仅 isAiDerived=true 时填）")
    private String rationale;
}