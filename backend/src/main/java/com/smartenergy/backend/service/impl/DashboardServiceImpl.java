package com.smartenergy.backend.service.impl;

import com.smartenergy.backend.agent.ForecastService;
import com.smartenergy.backend.cache.CacheKeys;
import com.smartenergy.backend.cache.CacheService;
import com.smartenergy.backend.entity.SensorData;
import com.smartenergy.backend.service.DashboardService;
import com.smartenergy.backend.service.DeviceService;
import com.smartenergy.backend.service.SensorDataService;
import com.smartenergy.backend.service.WorkOrderService;
import com.smartenergy.backend.vo.DashboardSummaryVO;
import com.smartenergy.backend.vo.DeviceOverviewVO;
import com.smartenergy.backend.vo.DispatchAdviceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final DeviceService deviceService;
    private final SensorDataService sensorDataService;
    private final WorkOrderService workOrderService;
    private final CacheService cacheService;
    private final ForecastService forecastService;

    @Override
    public DashboardSummaryVO getSummary(String deviceCode) {
        return cacheService.getOrLoad(CacheKeys.dashboardSummary(deviceCode),
                CacheKeys.DASHBOARD_SUMMARY_TTL, () -> buildSummary(deviceCode));
    }

    private DashboardSummaryVO buildSummary(String deviceCode) {
        List<DeviceOverviewVO> devices = deviceService.listDevices(1, 10000, null, null, null).getRecords();
        DashboardSummaryVO summary = new DashboardSummaryVO();

        BigDecimal totalUsage = BigDecimal.ZERO;
        BigDecimal totalCo2 = BigDecimal.ZERO;
        long runningCount = 0;
        long offlineCount = 0;

        for (DeviceOverviewVO device : devices) {
            if (device.getUsageKwh() != null) {
                totalUsage = totalUsage.add(device.getUsageKwh());
            }
            if (device.getCo2Emission() != null) {
                totalCo2 = totalCo2.add(device.getCo2Emission());
            }
            if ("OFFLINE".equals(device.getStatus())) {
                offlineCount++;
            }
            if (List.of("RUNNING", "HIGH_LOAD", "IDLE").contains(device.getStatus())) {
                runningCount++;
            }
        }

        DeviceOverviewVO focusDevice = devices.stream().findFirst().orElse(null);
        SensorData focusData = null;

        if (deviceCode != null && !deviceCode.isBlank()) {
            focusDevice = devices.stream()
                    .filter(device -> deviceCode.equals(device.getDeviceCode()))
                    .findFirst()
                    .orElse(focusDevice);
        }
        if (focusDevice != null) {
            focusData = sensorDataService.getLatestData(focusDevice.getDeviceCode());
        }

        summary.setTotalUsageKwh(totalUsage);
        summary.setTotalCo2Emission(totalCo2);
        summary.setCurrentPriceTier(focusData != null ? focusData.getXianPriceTier() : "--");
        summary.setRunningDeviceCount(runningCount);
        summary.setOfflineDeviceCount(offlineCount);
        summary.setActiveAlerts(workOrderService.listActiveAlerts(5));
        summary.setActiveAlertCount(summary.getActiveAlerts().size());
        summary.setFocusDeviceCode(focusDevice != null ? focusDevice.getDeviceCode() : null);
        summary.setFocusDeviceName(focusDevice != null ? focusDevice.getDeviceName() : null);
        summary.setDispatchAdvice(getDispatchAdvice(focusDevice != null ? focusDevice.getDeviceCode() : null));
        summary.setForecast(forecastService.getForecast(focusDevice != null ? focusDevice.getDeviceCode() : null));

        // ── 能效指标计算 ──
        // 碳排放强度：kg CO2 / kWh（越低越环保）
        if (totalUsage.compareTo(BigDecimal.ZERO) > 0) {
            summary.setCarbonIntensity(totalCo2.divide(totalUsage, 2, java.math.RoundingMode.HALF_UP));
        } else {
            summary.setCarbonIntensity(BigDecimal.ZERO);
        }

        // 电弧炉负荷率：当前功率 / 额定最大功率(150kW) × 100%
        if (focusDevice != null && focusDevice.getUsageKwh() != null) {
            BigDecimal eafLoad = focusDevice.getUsageKwh().multiply(new BigDecimal("100"))
                    .divide(new BigDecimal("150"), 1, java.math.RoundingMode.HALF_UP);
            summary.setEafLoadRate(eafLoad);
        } else {
            summary.setEafLoadRate(BigDecimal.ZERO);
        }

        // 设备综合利用率：运行中(含IDLE) / 总设备数 × 100%
        long totalDevices = devices.size();
        if (totalDevices > 0) {
            summary.setEquipmentUtilization(
                    new BigDecimal(runningCount).multiply(new BigDecimal("100"))
                            .divide(new BigDecimal(totalDevices), 1, java.math.RoundingMode.HALF_UP));
        } else {
            summary.setEquipmentUtilization(BigDecimal.ZERO);
        }

        // 吨钢电耗估算：电弧炉典型值 ~400 kWh/吨钢
        // 基于电弧炉实际功率反推：功率(kW) / 0.4 = 每小时钢产量(kg/h) ≈ 功率 / 400 (吨/h)
        // 实际吨钢电耗 = 当前功率 × 1h / (当前功率 / 400) ≈ 400 kWh/吨（理想值）
        // 用负荷率调整：负荷率越低，吨钢电耗越高（空载损耗占比大）
        BigDecimal eafPower = (focusDevice != null && focusDevice.getUsageKwh() != null)
                ? focusDevice.getUsageKwh() : BigDecimal.valueOf(80);
        BigDecimal loadFactor = eafPower.divide(BigDecimal.valueOf(150), 3, java.math.RoundingMode.HALF_UP);
        // 吨钢电耗 = 基准400 / 负荷率修正。负荷率100%→400kWh/t，负荷率50%→800kWh/t（空载损耗大）
        if (loadFactor.compareTo(BigDecimal.valueOf(0.05)) > 0) {
            summary.setEstimatedKwhPerTon(
                    BigDecimal.valueOf(400).divide(loadFactor, 0, java.math.RoundingMode.HALF_UP));
        } else {
            summary.setEstimatedKwhPerTon(BigDecimal.ZERO);
        }

        // 节能建议
        summary.setEfficiencyTip(buildEfficiencyTip(summary));

        return summary;
    }

    /** 根据当前运行状态生成节能建议 */
    private String buildEfficiencyTip(DashboardSummaryVO s) {
        StringBuilder tip = new StringBuilder();
        BigDecimal loadRate = s.getEafLoadRate();
        String priceTier = s.getCurrentPriceTier();

        if (loadRate != null && loadRate.compareTo(BigDecimal.valueOf(30)) < 0) {
            tip.append("电弧炉负荷率偏低（").append(loadRate).append("%），空载损耗占比大。");
            if ("VALLEY".equals(priceTier) || "DEEP_VALLEY".equals(priceTier)) {
                tip.append("当前谷电时段，建议加大产能以摊薄吨钢电耗。");
            } else {
                tip.append("建议在谷电窗口集中排产以降低吨钢电耗。");
            }
        } else if (loadRate != null && loadRate.compareTo(BigDecimal.valueOf(80)) > 0) {
            tip.append("电弧炉接近满负荷（").append(loadRate).append("%），运行效率良好。");
            if ("PEAK".equals(priceTier) || "CRITICAL_PEAK".equals(priceTier)) {
                tip.append("但当前峰电时段，建议评估是否可将非关键负荷推迟到谷电时段。");
            }
        } else {
            tip.append("电弧炉运行在正常区间。建议维持当前策略，关注分时电价变化。");
        }

        if (s.getOfflineDeviceCount() > 1) {
            tip.append(" 另有").append(s.getOfflineDeviceCount()).append("台设备离线，建议检查是否影响产能。");
        }

        return tip.toString();
    }

    @Override
    public DispatchAdviceVO getDispatchAdvice(String deviceCode) {
        if (deviceCode == null || deviceCode.isBlank()) {
            return new DispatchAdviceVO("INFO", "暂无调度建议", "请先选择一台设备查看实时策略。");
        }

        SensorData latestData = sensorDataService.getLatestData(deviceCode);
        if (latestData == null) {
            return new DispatchAdviceVO("INFO", "暂无调度建议", "当前没有可用的实时数据，建议先恢复数据采集。");
        }

        boolean hasActiveFault = !workOrderService.listActiveAlerts(20).stream()
                .filter(order -> deviceCode.equals(order.getDeviceCode()))
                .toList()
                .isEmpty();
        if (hasActiveFault) {
            return new DispatchAdviceVO("CRITICAL", "设备故障优先处置",
                    "当前设备存在未闭环维修工单，请先完成检修，再恢复高耗能工序。",
                    "暂停高耗能工序，立即派工检修", "故障停机损失高，优先止损");
        }

        if (List.of("CRITICAL_PEAK", "PEAK").contains(latestData.getXianPriceTier())
                && latestData.getUsageKwh() != null
                && latestData.getUsageKwh().compareTo(new BigDecimal("70")) >= 0) {
            BigDecimal saving = latestData.getUsageKwh().multiply(new BigDecimal("0.8"))
                    .setScale(0, java.math.RoundingMode.HALF_UP);
            return new DispatchAdviceVO("WARN", "建议推迟排产",
                    "当前处于高价时段且负荷偏高，建议将非关键任务顺延 30-60 分钟，优先避峰。",
                    "将非关键负荷顺延 30-60 分钟避峰", "预计避峰节省 ≈ ¥" + saving + "/小时");
        }

        if (List.of("VALLEY", "DEEP_VALLEY").contains(latestData.getXianPriceTier())) {
            return new DispatchAdviceVO("GOOD", "建议加大排产",
                    "当前处于低谷电价窗口，可安排可转移负荷或补充预热工序。",
                    "上调可转移负荷 / 补充预热工序", "低谷电价，单位电费约降低 30%");
        }

        return new DispatchAdviceVO("INFO", "维持当前策略",
                "当前负荷与电价处于可控区间，建议保持现有节奏并持续观察。",
                "保持现有节奏，持续观察", "—");
    }
}
