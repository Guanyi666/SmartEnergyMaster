package com.smartenergy.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "创建维修案例请求")
public class CaseCreateRequest {

    @NotBlank(message = "案例编号不能为空")
    @Schema(description = "案例编号", example = "RC-2025-001")
    private String caseCode;

    @NotBlank(message = "案例标题不能为空")
    @Schema(description = "案例标题")
    private String title;

    @NotBlank(message = "设备类型不能为空")
    @Schema(description = "设备类型")
    private String deviceType;

    @NotBlank(message = "故障类型不能为空")
    @Schema(description = "故障类型")
    private String faultType;

    @Schema(description = "故障现象")
    private String faultSymptom;

    @Schema(description = "根因分析")
    private String rootCause;

    @Schema(description = "维修过程")
    private String repairProcess;

    @Schema(description = "维修结果")
    private String repairResult;

    @Schema(description = "维修耗时（分钟）")
    private Integer durationMinutes;

    @Schema(description = "维修人员")
    private String technician;

    @Schema(description = "关键词（逗号分隔）")
    private String keywords;

    @Schema(description = "关联工单 ID")
    private Long relatedWorkOrderId;

    @Schema(description = "发生时间")
    private LocalDateTime occurredAt;
}