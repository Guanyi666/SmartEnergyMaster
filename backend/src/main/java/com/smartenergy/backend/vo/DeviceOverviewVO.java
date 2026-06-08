package com.smartenergy.backend.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Data
@Schema(description = "设备概览（列表项）")
public class DeviceOverviewVO {

    @Schema(description = "设备 ID")
    private Integer id;

    @Schema(description = "设备编码")
    private String deviceCode;

    @Schema(description = "设备名称")
    private String deviceName;

    @Schema(description = "设备类型")
    private String deviceType;

    @Schema(description = "当前状态（STOPPED / IDLE / RUNNING / HIGH_LOAD / FAULT / MAINTENANCE / OFFLINE）")
    private String status;

    @Schema(description = "安装位置")
    private String location;

    @Schema(description = "维护负责人")
    private String maintainer;

    @Schema(description = "设备描述")
    private String description;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    @Schema(description = "最新数据时间")
    private OffsetDateTime latestTime;

    @Schema(description = "最新有功功率（kWh）")
    private BigDecimal usageKwh;

    @Schema(description = "最新 CO₂排放量（kg）")
    private BigDecimal co2Emission;

    @Schema(description = "最新温度（℃）")
    private BigDecimal temperature;

    @Schema(description = "最新振动（mm/s）")
    private BigDecimal vibration;

    @Schema(description = "最新压力（kPa）")
    private BigDecimal pressure;

    @Schema(description = "运行状态（0=停机，1=空转，2=运行中，3=高负荷）")
    private Integer operatingStatus;

    @Schema(description = "当前电价时段")
    private String xianPriceTier;

    @Schema(description = "活跃工单数")
    private Long activeWorkOrderCount;

    @Schema(description = "最新工单标题")
    private String latestWorkOrderTitle;
}
