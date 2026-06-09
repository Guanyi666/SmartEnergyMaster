package com.smartenergy.workorder.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "维修人员档案响应 VO")
public class MaintenancePersonnelVO {

    @Schema(description = "主键 ID")
    private Long id;

    @Schema(description = "工号")
    private String employeeNo;

    @Schema(description = "姓名")
    private String name;

    @Schema(description = "联系电话")
    private String phone;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "头像底色")
    private String avatarColor;

    @Schema(description = "技能标签数组")
    private List<String> specializations;

    @Schema(description = "技能等级")
    private String skillLevel;

    @Schema(description = "证书描述")
    private String certification;

    @Schema(description = "当前在处理工单数")
    private Integer currentWorkload;

    @Schema(description = "最大并行处理数")
    private Integer maxWorkload;

    @Schema(description = "是否在岗")
    private Boolean isOnDuty;

    @Schema(description = "负载率（百分比，已 *100 取整）", example = "60")
    private Integer workloadRate;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
