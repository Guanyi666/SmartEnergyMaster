package com.smartenergy.backend.dto;

/**
 * @author Duan Guanyi
 * @version 1.0.0
 * @date 2026/3/19
 */

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.cglib.core.Local;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * 创建接受数据的DTO
 */

@Data
public class SensorDataDTO {

    @NotBlank(message = "设备编号不能为kong")
    private String deviceCode;

    @NotNull(message = "耗电量不能为空")
    private BigDecimal usageKwh;
    private BigDecimal co2Emission;
    private Integer nsm;
    private Integer weekStatus;
    private String dayOfWeek;
    private String loadType;

    // 西安市电价
    private String xianPriceTier;

    private BigDecimal temperature;
    private BigDecimal vibration;
    private BigDecimal pressure;
    private Integer operatingStatus;

    // 数据产生时间（如果网关没传，后端会自动补全当前时间）
    private OffsetDateTime time;
}
