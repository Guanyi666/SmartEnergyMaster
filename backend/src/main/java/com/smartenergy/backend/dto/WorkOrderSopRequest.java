package com.smartenergy.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkOrderSopRequest {

    @NotNull(message = "维修流程不能为空")
    private Long sopId;
}
