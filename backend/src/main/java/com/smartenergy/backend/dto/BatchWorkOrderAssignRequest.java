package com.smartenergy.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "批量指派请求：一次把多个人指到同一工单")
public class BatchWorkOrderAssignRequest {

    @NotEmpty(message = "人员 ID 列表不能为空")
    @Schema(description = "被指派人员 ID 列表", example = "[1, 2, 3]")
    private List<Long> personnelIds;

    @Pattern(regexp = "^(PRIMARY|ASSIST)$", message = "role 必须为 PRIMARY 或 ASSIST")
    @Schema(description = "指派角色", example = "PRIMARY", allowableValues = {"PRIMARY", "ASSIST"})
    private String role = "PRIMARY";

    @Schema(description = "备注", example = "现场紧急，需要 3 人协同")
    private String note;
}
