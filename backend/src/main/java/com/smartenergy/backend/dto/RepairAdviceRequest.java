package com.smartenergy.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "AI 维修建议请求")
public class RepairAdviceRequest {

    @NotBlank(message = "设备类型不能为空")
    @Schema(description = "设备类型", example = "ARC_FURNACE")
    private String deviceType;

    @NotBlank(message = "故障类型不能为空")
    @Schema(description = "故障类型", example = "MECHANICAL_JAM")
    private String faultType;

    @Schema(description = "现场补充描述（可选，喂给 LLM 做更精准的判断）")
    private String symptoms;

    @Schema(description = "关联工单 ID（可选）")
    private Long workOrderId;

    @Schema(description = "是否允许调用 LLM 增强（默认 true；未配 API key 时自动降级到纯 SOP 路径）")
    private Boolean useLlm = true;
}