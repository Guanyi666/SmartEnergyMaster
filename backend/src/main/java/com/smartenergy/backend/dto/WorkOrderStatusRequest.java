package com.smartenergy.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "工单状态更新请求")
public class WorkOrderStatusRequest {

    @NotBlank(message = "工单状态不能为空")
    @Schema(description = "目标状态（PENDING / IN_PROGRESS / RESOLVED）", example = "IN_PROGRESS")
    private String status;

    @Schema(description = "处理人", example = "张三")
    private String assignee;

    @Schema(description = "处理备注", example = "已确认故障，正在安排维修")
    private String note;
}
