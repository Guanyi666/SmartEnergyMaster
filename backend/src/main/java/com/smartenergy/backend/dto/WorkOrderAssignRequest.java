package com.smartenergy.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "工单指派请求")
public class WorkOrderAssignRequest {

    @NotNull(message = "人员 ID 不能为空")
    @Schema(description = "被指派人员 ID", example = "1")
    private Long personnelId;

    @Pattern(regexp = "^(PRIMARY|ASSIST)$", message = "role 必须为 PRIMARY 或 ASSIST")
    @Schema(description = "指派角色：PRIMARY 主负责人 / ASSIST 协助", example = "PRIMARY",
            allowableValues = {"PRIMARY", "ASSIST"})
    private String role = "PRIMARY";

    @Schema(description = "备注", example = "现场已沟通 10 分钟内到场")
    private String note;
}
