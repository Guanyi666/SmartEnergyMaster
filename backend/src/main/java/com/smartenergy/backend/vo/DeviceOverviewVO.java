package com.smartenergy.backend.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Data
public class DeviceOverviewVO {

    private Integer id;

    private String deviceCode;

    private String deviceName;

    private String deviceType;

    private String status;

    private String location;

    private String maintainer;

    private String description;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private OffsetDateTime latestTime;

    private BigDecimal usageKwh;

    private BigDecimal co2Emission;

    private BigDecimal temperature;

    private BigDecimal vibration;

    private BigDecimal pressure;

    private Integer operatingStatus;

    private String xianPriceTier;

    private Long activeWorkOrderCount;

    private String latestWorkOrderTitle;
}
