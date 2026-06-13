package com.smartenergy.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartenergy.backend.cache.CacheKeys;
import com.smartenergy.backend.cache.CacheService;
import com.smartenergy.backend.dto.SensorDataDTO;
import com.smartenergy.backend.entity.Device;
import com.smartenergy.backend.entity.SensorData;
import com.smartenergy.backend.mapper.DeviceMapper;
import com.smartenergy.backend.mapper.SensorDataMapper;
import com.smartenergy.backend.service.DeviceService;
import com.smartenergy.backend.service.SensorDataService;
import com.smartenergy.backend.service.WorkOrderService;
import com.smartenergy.backend.utils.DeviceStatusHelper;
import com.smartenergy.backend.vo.PageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SensorDataServiceImpl implements SensorDataService {

    private final SensorDataMapper sensorDataMapper;
    private final DeviceMapper deviceMapper;
    private final DeviceService deviceService;
    private final WorkOrderService workOrderService;
    private final CacheService cacheService;

    @Override
    @Transactional
    public void uploadData(SensorDataDTO sensorDataDTO) {
        Device device = deviceMapper.selectOne(new QueryWrapper<Device>().eq("device_code", sensorDataDTO.getDeviceCode()));
        if (device == null) {
            throw new IllegalArgumentException("非法设备上报：找不到设备编号 " + sensorDataDTO.getDeviceCode());
        }

        SensorData sensorData = new SensorData();
        BeanUtils.copyProperties(sensorDataDTO, sensorData);
        sensorData.setDeviceId(device.getId());
        if (sensorData.getTime() == null) {
            sensorData.setTime(OffsetDateTime.now());
        }

        sensorDataMapper.insert(sensorData);
        deviceService.updateDeviceStatus(device.getId(), DeviceStatusHelper.statusFromOperating(sensorData.getOperatingStatus()));
        // 新数据落库后，让该设备的最新工况缓存失效，下次读取回源最新值
        cacheService.evict(CacheKeys.deviceLatest(device.getDeviceCode()));
        analyzeAndTriggerAlarm(device, sensorData);
    }

    @Override
    @Transactional
    public void uploadBatch(List<SensorDataDTO> sensorDataDTOList) {
        if (sensorDataDTOList == null || sensorDataDTOList.isEmpty()) {
            throw new IllegalArgumentException("sensor payload cannot be empty");
        }
        for (SensorDataDTO sensorDataDTO : sensorDataDTOList) {
            uploadData(sensorDataDTO);
        }
    }

    @Override
    public SensorData getLatestData(String deviceCode) {
        return cacheService.getOrLoad(CacheKeys.deviceLatest(deviceCode), CacheKeys.DEVICE_LATEST_TTL, () -> {
            Device device = deviceMapper.selectOne(new QueryWrapper<Device>().eq("device_code", deviceCode));
            if (device == null) {
                throw new IllegalArgumentException("找不到设备编号 " + deviceCode);
            }
            return sensorDataMapper.selectOne(new QueryWrapper<SensorData>()
                    .eq("device_id", device.getId())
                    .orderByDesc("time")
                    .last("LIMIT 1"));
        });
    }

    @Override
    public List<SensorData> getRecentData(String deviceCode, int limit) {
        Device device = deviceMapper.selectOne(new QueryWrapper<Device>().eq("device_code", deviceCode));
        if (device == null) {
            throw new IllegalArgumentException("找不到设备编号 " + deviceCode);
        }
        // ★ NC3 防御纵深: int 类型当前无法 SQL 注入,但强制钳制 [1, 500] 防止
        //   ① 后续 refactor 改成 String 类型激活漏洞
        //   ② 单次过大 LIMIT 拖垮 DB
        int safeLimit = Math.min(Math.max(1, limit), 500);
        List<SensorData> desc = sensorDataMapper.selectList(new QueryWrapper<SensorData>()
                .eq("device_id", device.getId())
                .orderByDesc("time")
                .last("LIMIT " + safeLimit));
        java.util.Collections.reverse(desc);   // 转为时间升序
        return desc;
    }

    @Override
    public List<SensorData> getHistoryData(String deviceCode, int hours) {
        Device device = deviceMapper.selectOne(new QueryWrapper<Device>().eq("device_code", deviceCode));
        if (device == null) {
            throw new IllegalArgumentException("找不到设备编号 " + deviceCode);
        }

        OffsetDateTime startTime = OffsetDateTime.now().minusHours(hours);
        return sensorDataMapper.selectList(new QueryWrapper<SensorData>()
                .eq("device_id", device.getId())
                .ge("time", startTime)
                .orderByAsc("time"));
    }

    @Override
    public PageVO<SensorData> getHistoryData(String deviceCode, int hours, long page, long size) {
        Device device = deviceMapper.selectOne(new QueryWrapper<Device>().eq("device_code", deviceCode));
        if (device == null) {
            throw new IllegalArgumentException("device not found: " + deviceCode);
        }

        long safePage = Math.max(1L, page);
        long safeSize = Math.min(Math.max(1L, size), 500L);
        OffsetDateTime startTime = OffsetDateTime.now().minusHours(Math.max(1, hours));
        Page<SensorData> result = sensorDataMapper.selectPage(
                new Page<>(safePage, safeSize),
                new QueryWrapper<SensorData>()
                        .eq("device_id", device.getId())
                        .ge("time", startTime)
                        .orderByAsc("time"));
        return PageVO.of(result);
    }

    private void analyzeAndTriggerAlarm(Device device, SensorData data) {
        if (data.getOperatingStatus() == 1 && data.getVibration() != null && data.getVibration().doubleValue() > 15.0) {
            workOrderService.createWorkOrderFromFault(
                    device,
                    data,
                    "MECHANICAL_JAM",
                    "机械卡涩维修工单",
                    "检测到空转工况下振动急剧升高，系统已自动派发检修任务，请尽快排查轴承、联轴器和传动机构。",
                    "HIGH"
            );
        }

        if (data.getTemperature() != null && data.getTemperature().doubleValue() > 1000.0
                && data.getPressure() != null && data.getPressure().doubleValue() < 50.0) {
            workOrderService.createWorkOrderFromFault(
                    device,
                    data,
                    "COOLING_INTERRUPT",
                    "冷却系统检修工单",
                    "检测到高温伴随水压异常下降，疑似冷却回路断流或堵塞，请立即复核泵组与阀门状态。",
                    "CRITICAL"
            );
        }
    }
}
