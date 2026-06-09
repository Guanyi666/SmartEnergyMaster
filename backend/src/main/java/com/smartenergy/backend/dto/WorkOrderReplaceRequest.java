package com.smartenergy.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "工单指派替换请求：把某个人从工单上换掉")
public class WorkOrderReplaceRequest {

    @NotNull(message = "新人员 ID 不能为空")
    @Schema(description = "替换上去的新人员 ID", example = "2")
    private Long newPersonnelId;

    @Schema(description = "备注", example = "李工临时换人")
    private String note;
}
