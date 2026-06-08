package com.smartenergy.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "设备创建/更新请求")
public class DeviceUpsertRequest {

    @NotBlank(message = "设备编码不能为空")
    @Schema(description = "设备编码（唯一）", example = "EAF-01")
    private String deviceCode;

    @NotBlank(message = "设备名称不能为空")
    @Schema(description = "设备名称", example = "1号电弧炉")
    private String deviceName;

    @Schema(description = "设备类型", example = "电弧炉")
    private String deviceType;

    @Schema(description = "设备状态", example = "STOPPED")
    private String status;

    @Schema(description = "安装位置", example = "炼钢车间A区")
    private String location;

    @Schema(description = "维护负责人", example = "张三")
    private String maintainer;

    @Schema(description = "设备描述", example = "100吨交流电弧炉，用于废钢熔炼")
    private String description;
}
