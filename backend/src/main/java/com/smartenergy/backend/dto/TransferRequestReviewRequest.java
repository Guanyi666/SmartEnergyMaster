package com.smartenergy.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TransferRequestReviewRequest {

    @NotNull(message = "审批结果不能为空")
    private Boolean approved;

    private Long newPersonnelId;

    @Size(max = 500, message = "审批备注不能超过 500 字")
    private String reviewNote;
}
