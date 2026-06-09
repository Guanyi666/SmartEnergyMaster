package com.smartenergy.workorder.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "调度看板（按技能分组的负载矩阵）")
public class DispatchBoardVO {

    @Schema(description = "技能分组列表（每组内含人员负载信息）")
    private List<SkillGroupVO> skillGroups;

    @Schema(description = "未在岗人员数（汇总行）")
    private Long offDutyCount;
}
