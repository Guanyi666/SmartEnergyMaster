package com.smartenergy.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smartenergy.backend.entity.Device;
import com.smartenergy.backend.entity.SensorData;
import com.smartenergy.backend.entity.WorkOrder;
import com.smartenergy.backend.mapper.DeviceMapper;
import com.smartenergy.backend.mapper.SensorDataMapper;
import com.smartenergy.backend.mapper.WorkOrderMapper;
import com.smartenergy.backend.service.DeviceHealthService;
import com.smartenergy.backend.vo.DeviceHealthScoreVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeviceHealthServiceImpl implements DeviceHealthService {

    private final DeviceMapper deviceMapper;
    private final SensorDataMapper sensorDataMapper;
    private final WorkOrderMapper workOrderMapper;

    @Override
    public DeviceHealthScoreVO evaluateHealth(Integer deviceId) {
        Device device = deviceMapper.selectById(deviceId);
        if (device == null) {
            throw new IllegalArgumentException("设备不存在: " + deviceId);
        }

        SensorData latestData = findLatestSensor(deviceId);
        long faultCount = countTotalFaults(deviceId);
        LocalDateTime lastMaintenance = findLastMaintenance(deviceId, device.getCreatedAt());

        DeviceTypeThresholds thresholds = getThresholds(device.getDeviceType());

        int runtimeScore = calcRuntimeScore(deviceId);
        int faultCountScore = calcFaultCountScore(faultCount);
        int vibrationScore = calcVibrationScore(latestData, thresholds.normalVibrationBaseline);
        int temperatureScore = calcTemperatureScore(latestData, thresholds.normalTemperatureCeiling);
        int maintenanceScore = calcMaintenanceScore(lastMaintenance);

        int overallScore = (int) (
                runtimeScore      * 0.20 +
                faultCountScore   * 0.30 +
                vibrationScore    * 0.20 +
                temperatureScore  * 0.15 +
                maintenanceScore  * 0.15
        );
        overallScore = clamp(overallScore);

        DeviceHealthScoreVO vo = new DeviceHealthScoreVO();
        vo.setDeviceId(device.getId());
        vo.setDeviceCode(device.getDeviceCode());
        vo.setDeviceName(device.getDeviceName());
        vo.setOverallScore(overallScore);
        vo.setRuntimeScore(runtimeScore);
        vo.setFaultCountScore(faultCountScore);
        vo.setVibrationScore(vibrationScore);
        vo.setTemperatureScore(temperatureScore);
        vo.setMaintenanceScore(maintenanceScore);
        vo.setEvaluatedAt(LocalDateTime.now());
        return vo;
    }

    // --- sub-score calculations ---

    private int calcRuntimeScore(Integer deviceId) {
        OffsetDateTime thirtyDaysAgo = OffsetDateTime.now().minusDays(30);
        List<SensorData> list = sensorDataMapper.selectList(new QueryWrapper<SensorData>()
                .eq("device_id", deviceId)
                .ge("time", thirtyDaysAgo));
        if (list.isEmpty()) {
            return 100;
        }
        double avg = list.stream()
                .mapToInt(d -> d.getOperatingStatus() != null ? d.getOperatingStatus() : 0)
                .average()
                .orElse(0);
        double avgLoad = avg / 3.0;
        return clamp((int) (100 * (1 - avgLoad)));
    }

    private int calcFaultCountScore(long faultCount) {
        return clamp((int) (100 - faultCount * 20));
    }

    private int calcVibrationScore(SensorData latestData, BigDecimal baseline) {
        if (latestData == null || latestData.getVibration() == null || baseline == null) {
            return 100;
        }
        double deviation = Math.abs(latestData.getVibration().subtract(baseline).doubleValue())
                / baseline.doubleValue();
        return clamp((int) (100 - deviation * 100));
    }

    private int calcTemperatureScore(SensorData latestData, BigDecimal ceiling) {
        if (latestData == null || latestData.getTemperature() == null || ceiling == null) {
            return 100;
        }
        double temp = latestData.getTemperature().doubleValue();
        double ceil = ceiling.doubleValue();
        if (temp <= ceil) {
            return 100;
        }
        return clamp((int) (100 - (temp - ceil) * 2));
    }

    private int calcMaintenanceScore(LocalDateTime lastMaintenance) {
        long days = java.time.Duration.between(lastMaintenance, LocalDateTime.now()).toDays();
        return clamp((int) (100 - days * 2));
    }

    // --- helpers ---

    private SensorData findLatestSensor(Integer deviceId) {
        return sensorDataMapper.selectOne(new QueryWrapper<SensorData>()
                .eq("device_id", deviceId)
                .orderByDesc("time")
                .last("LIMIT 1"));
    }

    private long countTotalFaults(Integer deviceId) {
        return workOrderMapper.selectCount(new QueryWrapper<WorkOrder>()
                .eq("device_id", deviceId));
    }

    private LocalDateTime findLastMaintenance(Integer deviceId, LocalDateTime fallback) {
        WorkOrder lastResolved = workOrderMapper.selectOne(new QueryWrapper<WorkOrder>()
                .eq("device_id", deviceId)
                .eq("status", "RESOLVED")
                .orderByDesc("resolved_at")
                .last("LIMIT 1"));
        if (lastResolved == null || lastResolved.getResolvedAt() == null) {
            return fallback;
        }
        return lastResolved.getResolvedAt();
    }

    private int clamp(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private DeviceTypeThresholds getThresholds(String deviceType) {
        if (deviceType == null) {
            return new DeviceTypeThresholds(new BigDecimal("60"), new BigDecimal("5.0"));
        }
        switch (deviceType.toUpperCase()) {
            case "ARC_FURNACE":
                // 电弧炉：高温设备，正常800°C以下，振动10mm/s以下
                return new DeviceTypeThresholds(new BigDecimal("800"), new BigDecimal("10.0"));
            case "LADLE_FURNACE":
                // 钢包精炼炉：精炼温度1480-1650°C，振动6mm/s以下
                return new DeviceTypeThresholds(new BigDecimal("1650"), new BigDecimal("6.0"));
            case "CONTINUOUS_CASTER":
                // 连铸机：结晶器温度正常350°C以下，振动5mm/s以下
                return new DeviceTypeThresholds(new BigDecimal("350"), new BigDecimal("5.0"));
            case "DUST_COLLECTOR":
                // 除尘系统：烟气温度正常200°C以下，振动7mm/s以下
                return new DeviceTypeThresholds(new BigDecimal("200"), new BigDecimal("7.0"));
            case "COMPRESSOR":
                // 空压机：排气温度正常100°C以下，振动9mm/s以下
                return new DeviceTypeThresholds(new BigDecimal("100"), new BigDecimal("9.0"));
            case "PUMP":
                // 循环水泵：出水温度正常80°C以下，振动8mm/s以下
                return new DeviceTypeThresholds(new BigDecimal("80"), new BigDecimal("8.0"));
            default:
                return new DeviceTypeThresholds(new BigDecimal("60"), new BigDecimal("5.0"));
        }
    }

    private record DeviceTypeThresholds(BigDecimal normalTemperatureCeiling, BigDecimal normalVibrationBaseline) {}
}
