package com.smartenergy.backend.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Data
public class WorkOrderVO {

    private Long id;

    private String orderNo;

    private Integer deviceId;

    private String deviceCode;

    private String deviceName;

    private String title;

    private String faultType;

    private String description;

    private String status;

    private String priority;

    private String assignee;

    private OffsetDateTime sourceTime;

    private LocalDateTime acceptedAt;

    private LocalDateTime resolvedAt;

    private BigDecimal latestTemperature;

    private BigDecimal latestVibration;

    private BigDecimal latestPressure;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
