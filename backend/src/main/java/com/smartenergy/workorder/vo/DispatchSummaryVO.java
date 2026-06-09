package com.smartenergy.workorder.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Schema(description = "调度总览（在岗数 / 平均负载 / 技能覆盖）")
public class DispatchSummaryVO {

    @Schema(description = "总工单数")
    private Long totalOrders;

    @Schema(description = "PENDING 工单数")
    private Long pendingOrders;

    @Schema(description = "IN_PROGRESS 工单数")
    private Long inProgressOrders;

    @Schema(description = "RESOLVED 工单数")
    private Long resolvedOrders;

    @Schema(description = "总人员数")
    private Long totalPersonnel;

    @Schema(description = "在岗人员数")
    private Long onDutyPersonnel;

    @Schema(description = "离岗人员数")
    private Long offDutyPersonnel;

    @Schema(description = "平均负载率（百分比，0-100）")
    private Integer avgWorkloadRate;

    @Schema(description = "技能覆盖：{技能名 → 在岗人数}")
    private Map<String, Long> skillCoverage;
}
