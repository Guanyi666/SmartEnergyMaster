package com.smartenergy.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TransferRequestCreateRequest {

    @NotBlank(message = "转工单缘由不能为空")
    @Size(max = 500, message = "转工单缘由不能超过 500 字")
    private String reason;
}
