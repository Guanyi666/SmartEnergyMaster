package com.smartenergy.workorder.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "自动匹配单个候选")
public class MatchCandidateVO {

    @Schema(description = "人员 ID")
    private Long personnelId;

    @Schema(description = "工号")
    private String employeeNo;

    @Schema(description = "姓名")
    private String name;

    @Schema(description = "头像底色")
    private String avatarColor;

    @Schema(description = "技能标签")
    private List<String> specializations;

    @Schema(description = "技能等级")
    private String skillLevel;

    @Schema(description = "当前负载 / 最大负载")
    private Integer currentWorkload;

    @Schema(description = "最大负载")
    private Integer maxWorkload;

    @Schema(description = "负载率（0-100）")
    private Integer workloadRate;

    @Schema(description = "匹配分（0-100）")
    private Integer matchScore;

    @Schema(description = "命中的技能")
    private List<String> matchedSkills;
}
