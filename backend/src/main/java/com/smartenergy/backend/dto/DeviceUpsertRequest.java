package com.smartenergy.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 设备创建/更新请求.
 * ★ NM6 修复 (2026-06-13): 所有字符串字段补 @Size(max=) 防止 GB 级 payload 入库
 */
@Data
@Schema(description = "设备创建/更新请求")
public class DeviceUpsertRequest {

    @NotBlank(message = "设备编码不能为空")
    @Size(max = 64, message = "设备编码不能超过 64 个字符")
    @Schema(description = "设备编码（唯一）", example = "EAF-01")
    private String deviceCode;

    @NotBlank(message = "设备名称不能为空")
    @Size(max = 128, message = "设备名称不能超过 128 个字符")
    @Schema(description = "设备名称", example = "1号电弧炉")
    private String deviceName;

    @Size(max = 64, message = "设备类型不能超过 64 个字符")
    @Schema(description = "设备类型", example = "电弧炉")
    private String deviceType;

    @Size(max = 32, message = "设备状态不能超过 32 个字符")
    @Schema(description = "设备状态", example = "STOPPED")
    private String status;

    @Size(max = 128, message = "安装位置不能超过 128 个字符")
    @Schema(description = "安装位置", example = "炼钢车间A区")
    private String location;

    @Size(max = 64, message = "维护负责人不能超过 64 个字符")
    @Schema(description = "维护负责人", example = "张三")
    private String maintainer;

    @Size(max = 1024, message = "设备描述不能超过 1024 个字符")
    @Schema(description = "设备描述", example = "100吨交流电弧炉，用于废钢熔炼")
    private String description;
}
