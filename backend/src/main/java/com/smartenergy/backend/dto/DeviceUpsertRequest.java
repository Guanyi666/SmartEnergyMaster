package com.smartenergy.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeviceUpsertRequest {

    @NotBlank(message = "设备编码不能为空")
    private String deviceCode;

    @NotBlank(message = "设备名称不能为空")
    private String deviceName;

    private String deviceType;

    private String status;

    private String location;

    private String maintainer;

    private String description;
}
