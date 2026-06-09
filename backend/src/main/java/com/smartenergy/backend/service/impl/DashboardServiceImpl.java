package com.smartenergy.backend.service.impl;

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
        return summary;
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
            return new DispatchAdviceVO("CRITICAL", "设备故障优先处置", "当前设备存在未闭环维修工单，请先完成检修，再恢复高耗能工序。");
        }

        if (List.of("CRITICAL_PEAK", "PEAK").contains(latestData.getXianPriceTier())
                && latestData.getUsageKwh() != null
                && latestData.getUsageKwh().compareTo(new BigDecimal("70")) >= 0) {
            return new DispatchAdviceVO("WARN", "建议推迟排产", "当前处于高价时段且负荷偏高，建议将非关键任务顺延 30-60 分钟，优先避峰。");
        }

        if (List.of("VALLEY", "DEEP_VALLEY").contains(latestData.getXianPriceTier())) {
            return new DispatchAdviceVO("GOOD", "建议加大排产", "当前处于低谷电价窗口，可安排可转移负荷或补充预热工序。");
        }

        return new DispatchAdviceVO("INFO", "维持当前策略", "当前负荷与电价处于可控区间，建议保持现有节奏并持续观察。");
    }
}
