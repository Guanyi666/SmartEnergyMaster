package com.smartenergy.backend.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class DashboardSummaryVO {

    private BigDecimal totalUsageKwh;

    private BigDecimal totalCo2Emission;

    private String currentPriceTier;

    private long runningDeviceCount;

    private long offlineDeviceCount;

    private long activeAlertCount;

    private String focusDeviceCode;

    private String focusDeviceName;

    private DispatchAdviceVO dispatchAdvice;

    private List<WorkOrderVO> activeAlerts;
}
