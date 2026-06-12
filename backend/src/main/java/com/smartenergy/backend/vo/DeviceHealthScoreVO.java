package com.smartenergy.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DeviceHealthScoreVO {

    private Integer deviceId;
    private String deviceCode;
    private String deviceName;

    private int overallScore;

    private int runtimeScore;
    private int faultCountScore;
    private int vibrationScore;
    private int temperatureScore;
    private int maintenanceScore;

    private LocalDateTime evaluatedAt;
}
