package com.smartenergy.backend.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "按技能分组的人员负载组")
public class SkillGroupVO {

    @Schema(description = "技能名", example = "电气")
    private String skill;

    @Schema(description = "本技能组人员数")
    private Long personnelCount;

    @Schema(description = "本技能组当前总负载")
    private Integer totalCurrentWorkload;

    @Schema(description = "本技能组最大总容量")
    private Integer totalMaxWorkload;

    @Schema(description = "本技能组平均负载率（0-100）")
    private Integer avgWorkloadRate;

    @Schema(description = "本技能组人员列表")
    private List<MaintenancePersonnelVO> personnel;
}
