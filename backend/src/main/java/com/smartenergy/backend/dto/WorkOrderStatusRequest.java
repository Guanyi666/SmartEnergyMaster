package com.smartenergy.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WorkOrderStatusRequest {

    @NotBlank(message = "工单状态不能为空")
    private String status;

    private String assignee;

    private String note;
}
