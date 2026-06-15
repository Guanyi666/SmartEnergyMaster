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

    @Schema(description = "故障/维修中设备数（存在活跃工单的设备）")
    private long faultDeviceCount;

    @Schema(description = "停机设备数（operatingStatus=0且无活跃工单）")
    private long stoppedDeviceCount;

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

    // ── 能效指标（Epic 能效分析）──

    @Schema(description = "吨钢碳排放强度（kg CO2 / kWh 电力消耗），衡量单位能耗的环保水平")
    private BigDecimal carbonIntensity;

    @Schema(description = "电弧炉负荷率（%），当前功率 / 额定最大功率 × 100%")
    private BigDecimal eafLoadRate;

    @Schema(description = "全厂设备综合利用率（%），运行中设备数 / 总设备数 × 100%")
    private BigDecimal equipmentUtilization;

    @Schema(description = "当前小时预计吨钢电耗（kWh/吨钢），基于电弧炉功率估算")
    private BigDecimal estimatedKwhPerTon;

    @Schema(description = "节能建议摘要（基于当前电价与负荷的优化方向）")
    private String efficiencyTip;
}
