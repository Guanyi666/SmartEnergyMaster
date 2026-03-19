package com.smartenergy.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smartenergy.backend.dto.SensorDataDTO;
import com.smartenergy.backend.entity.Device;
import com.smartenergy.backend.entity.SensorData;
import com.smartenergy.backend.mapper.DeviceMapper;
import com.smartenergy.backend.mapper.SensorDataMapper;
import com.smartenergy.backend.service.SensorDataService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * @author Duan Guanyi
 * @version 1.0.0
 * @date 2026/3/19
 */

@Service
public class SensorDataServiceImpl implements SensorDataService {
    @Autowired
    private SensorDataMapper sensorDataMapper;

    @Autowired
    private DeviceMapper deviceMapper;

    @Override
    public void uploadData(SensorDataDTO sensorDataDTO) {
        // 1. 根据设备编号(deviceCode)查找真实的设备ID
        Device device = deviceMapper.selectOne(new QueryWrapper<Device>().eq("device_code", sensorDataDTO.getDeviceCode()));
        if(device == null) {
            throw new RuntimeException("非法设备上报：找不到设备编号 " + sensorDataDTO.getDeviceCode());

        }

        // 2. 将 DTO 转换为实体类
        SensorData sensorData = new SensorData();
        // 使用 Spring 神器自动拷贝同名属性
        BeanUtils.copyProperties(sensorDataDTO, sensorData);

        sensorData.setDeviceId(device.getId());
        // 如果 Python 脚本没传时间，默认使用服务器当前时间
        if (sensorData.getTime() == null) {
            sensorData.setTime(OffsetDateTime.now());
        }

        // 3. 保存到 TimescaleDB 时序数据库
        sensorDataMapper.insert(sensorData);

        analyzeAndTriggerAlarm(sensorData);
    }

    @Override
    public SensorData getLatestData(String deviceCode) {
        // 1. 根据设备编号(deviceCode)查找真实的设备ID
        Device device = deviceMapper.selectOne(new QueryWrapper<Device>().eq("device_code", deviceCode));
        if(device == null) {
            throw new RuntimeException("非法设备上报：找不到设备编号 " + deviceCode);

        }

        return sensorDataMapper.selectOne(new QueryWrapper<SensorData>()
                .eq("device_id", device.getId())
                .orderByDesc("time")
                .last("LIMIT 1")
        );

    }

    @Override
    public List<SensorData> getHistoryData(String deviceCode, int hours) {
        // 1. 根据设备编号(deviceCode)查找真实的设备ID
        Device device = deviceMapper.selectOne(new QueryWrapper<Device>().eq("device_code", deviceCode));
        if(device == null) {
            throw new RuntimeException("非法设备上报：找不到设备编号 " + deviceCode);
        }

        // 计算时间边界 (当前时间往回推 N 个小时)
        OffsetDateTime startTime = OffsetDateTime.now().minusHours(hours);

        // 从 TimescaleDB 中抓取该时间段内的所有时序数据，并按时间正序排列
        return sensorDataMapper.selectList(new QueryWrapper<SensorData>()
                .eq("device_id", device.getId())
                .ge("time", startTime) // ge 代表 greater than or equal (>=)
                .orderByAsc("time"));
    }

    /**
     * 简易版的智能诊断规则引擎（后续可替换为调用 AI 模型）
     */
    private void analyzeAndTriggerAlarm(SensorData data) {
        // 场景 A：耗电激增 + 空转 + 振动剧烈 = 机械卡涩
        if (data.getOperatingStatus() == 1 && data.getVibration() != null && data.getVibration().doubleValue() > 15.0) {
            System.err.println("【智能告警】检测到设备机械卡涩/轴承损坏风险！建议立即停机检查！");
        }

        // 场景 B：炉温飙升 + 水压骤降 = 冷却断流
        if (data.getTemperature() != null && data.getTemperature().doubleValue() > 1000.0
                && data.getPressure() != null && data.getPressure().doubleValue() < 50.0) {
            System.err.println("【智能告警】冷却系统水压过低，炉温过载！存在熔穿风险！");
        }
    }
}
