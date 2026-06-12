package com.smartenergy.backend.vo;

import com.smartenergy.backend.dto.RepairAdviceStep;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "AI 维修建议响应（含与 SOP 的逐条对照）")
public class RepairAdviceVO {

    @Schema(description = "关联工单 ID（可空）")
    private Long workOrderId;

    @Schema(description = "命中的 SOP（按 deviceType+faultType 最佳匹配）")
    private SOPDetailVO matchedSop;

    @Schema(description = "建议步骤列表（含每步的 SOP 源引用）")
    private List<RepairAdviceStep> steps;

    @Schema(description = "整体置信度 0~1")
    private Double overallConfidence;

    @Schema(description = "生成策略：LLM / DETERMINISTIC（纯 SOP）")
    private String strategy;

    @Schema(description = "AI 总结说明")
    private String summary;

    @Schema(description = "生成时间")
    private LocalDateTime generatedAt;
}