package com.smartenergy.workorder.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "自动匹配推荐结果")
public class DispatchMatchVO {

    @Schema(description = "工单 ID")
    private Long workOrderId;

    @Schema(description = "故障类型英文常量")
    private String faultType;

    @Schema(description = "该故障需要的技能（从 faultType 解析得出）")
    private List<String> requiredSkills;

    @Schema(description = "推荐的人员列表（按分数降序）")
    private List<MatchCandidateVO> candidates;
}
