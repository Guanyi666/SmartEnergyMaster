package com.smartenergy.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
@Schema(description = "备件领用请求")
public class SparePartUsageRequest {

    @NotNull(message = "备件 ID 不能为空")
    @Schema(description = "备件 ID")
    private Long partId;

    @NotNull(message = "领用数量必须大于 0")
    @Positive(message = "领用数量必须大于 0")
    @Schema(description = "领用数量")
    private Integer quantity;

    @Schema(description = "关联工单 ID（可空）")
    private Long workOrderId;

    @Schema(description = "领用人")
    private String userName;

    @Schema(description = "备注")
    private String note;
}