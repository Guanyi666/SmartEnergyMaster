package com.smartenergy.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
public class SensorDataDTO {

    @NotBlank(message = "设备编码不能为空")
    private String deviceCode;

    @NotNull(message = "能耗数据不能为空")
    private BigDecimal usageKwh;

    private BigDecimal co2Emission;

    private Integer nsm;

    private Integer weekStatus;

    private String dayOfWeek;

    private String loadType;

    private String xianPriceTier;

    private BigDecimal temperature;

    private BigDecimal vibration;

    private BigDecimal pressure;

    private Integer operatingStatus;

    private OffsetDateTime time;
}
