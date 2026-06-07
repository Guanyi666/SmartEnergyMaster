package com.smartenergy.backend.utils;

import com.smartenergy.backend.entity.SensorData;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;

public final class DeviceStatusHelper {

    private DeviceStatusHelper() {
    }

    public static String resolveStatus(SensorData latestData, String currentStatus, boolean hasActiveWorkOrder) {
        if (latestData == null || latestData.getTime() == null || latestData.getTime().isBefore(OffsetDateTime.now().minusMinutes(5))) {
            return "OFFLINE";
        }
        if (hasActiveWorkOrder) {
            if ("MAINTENANCE".equalsIgnoreCase(currentStatus)) {
                return "MAINTENANCE";
            }
            return "FAULT";
        }
        if (StringUtils.hasText(currentStatus) && ("MAINTENANCE".equalsIgnoreCase(currentStatus) || "FAULT".equalsIgnoreCase(currentStatus))) {
            return currentStatus.toUpperCase();
        }
        return statusFromOperating(latestData.getOperatingStatus());
    }

    public static String statusFromOperating(Integer operatingStatus) {
        if (operatingStatus == null) {
            return "STOPPED";
        }
        return switch (operatingStatus) {
            case 0 -> "STOPPED";
            case 1 -> "IDLE";
            case 2 -> "RUNNING";
            case 3 -> "HIGH_LOAD";
            default -> "STOPPED";
        };
    }
}
