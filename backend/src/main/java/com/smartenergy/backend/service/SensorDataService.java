package com.smartenergy.backend.service;

import com.smartenergy.backend.dto.SensorDataDTO;
import com.smartenergy.backend.entity.SensorData;

import java.util.List;

/**
 * @author Duan Guanyi
 * @version 1.0.0
 * @date 2026/3/19
 */
public interface SensorDataService {
    /**
     * 处理并保存硬件网关上传的传感器数据
     */
    void uploadData(SensorDataDTO sensorDataDTO);

    /**
     * 【大屏接口】获取某设备的最新一条实时工况数据
     */
    SensorData getLatestData(String deviceCode);

    /**
     * 【大屏接口】获取某设备过去 N 小时的时序历史数据 (用于绘制折线图)
     */
    List<SensorData> getHistoryData(String deviceCode, int hours);
}
