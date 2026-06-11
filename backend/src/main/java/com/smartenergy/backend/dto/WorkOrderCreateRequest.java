package com.smartenergy.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 手动创建工单请求体
 * <p>
 * 由操作员在"维修指挥中心 → 新建工单"对话框提交，与故障自动创建的工单结构一致
 * （设备快照 + SOP 自动匹配），区别是不携带触发时刻的传感器数据——后端会在创建时
 * 从 sensor_data 拉该设备最新一条自动填充。
 */
@Data
@Schema(description = "手动创建工单请求")
public class WorkOrderCreateRequest {

    @NotNull(message = "请选择设备")
    @Schema(description = "设备 ID", example = "1")
    private Integer deviceId;

    @NotBlank(message = "请输入工单标题")
    @Size(max = 128, message = "标题不能超过 128 字符")
    @Schema(description = "工单标题", example = "巡检发现轴承异响")
    private String title;

    @NotBlank(message = "请选择故障类型")
    @Size(max = 64)
    @Schema(description = "故障类型（参考 faultTypeMeta 枚举值）",
            example = "MECHANICAL_JAM",
            allowableValues = {"MECHANICAL_JAM", "COOLING_INTERRUPT",
                    "ELECTRICAL_OVERLOAD", "SENSOR_DRIFT",
                    "BEARING_WEAR", "INTERMITTENT_JAM"})
    private String faultType;

    @NotBlank(message = "请选择优先级")
    @Pattern(regexp = "^(CRITICAL|HIGH|MEDIUM|LOW)$",
            message = "优先级仅支持 CRITICAL / HIGH / MEDIUM / LOW")
    @Schema(description = "优先级", example = "HIGH",
            allowableValues = {"CRITICAL", "HIGH", "MEDIUM", "LOW"})
    private String priority;

    @NotBlank(message = "请输入故障描述")
    @Size(max = 500, message = "描述不能超过 500 字符")
    @Schema(description = "故障描述", example = "巡检时发现轴承位置有周期性异响，建议排查")
    private String description;
}
