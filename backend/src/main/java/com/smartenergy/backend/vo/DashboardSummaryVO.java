package com.smartenergy.backend.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Schema(description = "仪表盘聚合总览")
public class DashboardSummaryVO {

    @Schema(description = "全厂总功耗（kWh）")
    private BigDecimal totalUsageKwh;

    @Schema(description = "全厂累计碳排放（kg）")
    private BigDecimal totalCo2Emission;

    @Schema(description = "当前电价区间")
    private String currentPriceTier;

    @Schema(description = "运行中设备数")
    private long runningDeviceCount;

    @Schema(description = "离线设备数")
    private long offlineDeviceCount;

    @Schema(description = "活跃告警数")
    private long activeAlertCount;

    @Schema(description = "焦点设备编码")
    private String focusDeviceCode;

    @Schema(description = "焦点设备名称")
    private String focusDeviceName;

    @Schema(description = "调度建议")
    private DispatchAdviceVO dispatchAdvice;

    @Schema(description = "活跃告警列表")
    private List<WorkOrderVO> activeAlerts;

    /** 焦点设备未来 +15/+30min 能耗预测（含 95% 置信区间），Epic 6-3。 */
    private List<ForecastPointVO> forecast;
}
