package com.smartenergy.workorder.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "工单指派记录")
public class WorkOrderAssignmentVO {

    @Schema(description = "指派记录 ID")
    private Long id;

    @Schema(description = "工单 ID")
    private Long workOrderId;

    @Schema(description = "人员 ID")
    private Long personnelId;

    @Schema(description = "人员姓名")
    private String personnelName;

    @Schema(description = "工号")
    private String employeeNo;

    @Schema(description = "头像底色")
    private String avatarColor;

    @Schema(description = "角色：PRIMARY / ASSIST")
    private String role;

    @Schema(description = "指派时间")
    private LocalDateTime assignedAt;

    @Schema(description = "释放时间（null=进行中）")
    private LocalDateTime releasedAt;

    @Schema(description = "备注")
    private String note;
}
