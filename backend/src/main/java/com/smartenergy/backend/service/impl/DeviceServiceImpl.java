package com.smartenergy.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartenergy.backend.cache.CacheKeys;
import com.smartenergy.backend.cache.CacheService;
import com.smartenergy.backend.dto.DeviceUpsertRequest;
import com.smartenergy.backend.entity.Device;
import com.smartenergy.backend.entity.SensorData;
import com.smartenergy.backend.entity.WorkOrder;
import com.smartenergy.backend.mapper.DeviceMapper;
import com.smartenergy.backend.mapper.SensorDataMapper;
import com.smartenergy.backend.mapper.WorkOrderMapper;
import com.smartenergy.backend.service.DeviceService;
import com.smartenergy.backend.utils.DeviceStatusHelper;
import com.smartenergy.backend.vo.DeviceOverviewVO;
import com.smartenergy.backend.vo.PageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeviceServiceImpl implements DeviceService {

    private final DeviceMapper deviceMapper;
    private final SensorDataMapper sensorDataMapper;
    private final WorkOrderMapper workOrderMapper;
    private final CacheService cacheService;

    @Override
    public PageVO<DeviceOverviewVO> listDevices(int page, int size, String type, String status, String keyword) {
        QueryWrapper<Device> wrapper = new QueryWrapper<Device>().orderByAsc("id");
        if (StringUtils.hasText(type)) {
            wrapper.eq("device_type", type);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq("status", status.toUpperCase());
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like("device_name", keyword).or().like("device_code", keyword));
        }
        IPage<Device> result = deviceMapper.selectPage(new Page<>(page, size), wrapper);
        List<DeviceOverviewVO> records = result.getRecords().stream()
                .map(this::toOverview)
                .toList();
        IPage<DeviceOverviewVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(records);
        return PageVO.of(voPage);
    }

    @Override
    public DeviceOverviewVO getDeviceOverview(Integer id) {
        return toOverview(getDeviceById(id));
    }

    @Override
    @Transactional
    public Device createDevice(DeviceUpsertRequest request) {
        validateUniqueCode(request.getDeviceCode(), null);

        Device device = new Device();
        applyRequest(device, request);
        if (!StringUtils.hasText(device.getStatus())) {
            device.setStatus("STOPPED");
        }
        device.setCreatedAt(LocalDateTime.now());
        device.setUpdatedAt(LocalDateTime.now());
        deviceMapper.insert(device);
        cacheService.evict(CacheKeys.DEVICE_LIST);
        return device;
    }

    @Override
    @Transactional
    public Device updateDevice(Integer id, DeviceUpsertRequest request) {
        Device existing = getDeviceById(id);
        validateUniqueCode(request.getDeviceCode(), id);
        applyRequest(existing, request);
        existing.setUpdatedAt(LocalDateTime.now());
        deviceMapper.updateById(existing);
        cacheService.evict(CacheKeys.DEVICE_LIST);
        return existing;
    }

    @Override
    @Transactional
    public void deleteDevice(Integer id) {
        getDeviceById(id);
        deviceMapper.deleteById(id);
        cacheService.evict(CacheKeys.DEVICE_LIST);
    }

    @Override
    public Device getDeviceById(Integer id) {
        Device device = deviceMapper.selectById(id);
        if (device == null) {
            throw new IllegalArgumentException("设备不存在: " + id);
        }
        return device;
    }

    @Override
    public Device getDeviceByCode(String deviceCode) {
        Device device = deviceMapper.selectOne(new QueryWrapper<Device>().eq("device_code", deviceCode));
        if (device == null) {
            throw new IllegalArgumentException("设备不存在: " + deviceCode);
        }
        return device;
    }

    @Override
    @Transactional
    public void updateDeviceStatus(Integer deviceId, String status) {
        Device device = getDeviceById(deviceId);
        device.setStatus(status);
        device.setUpdatedAt(LocalDateTime.now());
        deviceMapper.updateById(device);
        cacheService.evict(CacheKeys.DEVICE_LIST);
    }

    @Override
    @Transactional
    public void restoreStatusFromLatestData(Integer deviceId) {
        Device device = getDeviceById(deviceId);
        SensorData latestData = findLatestSensor(deviceId);
        String status = latestData == null ? "STOPPED" : DeviceStatusHelper.statusFromOperating(latestData.getOperatingStatus());
        device.setStatus(status);
        device.setUpdatedAt(LocalDateTime.now());
        deviceMapper.updateById(device);
        cacheService.evict(CacheKeys.DEVICE_LIST);
    }

    private DeviceOverviewVO toOverview(Device device) {
        SensorData latestData = findLatestSensor(device.getId());
        WorkOrder latestOrder = workOrderMapper.selectOne(new QueryWrapper<WorkOrder>()
                .eq("device_id", device.getId())
                .orderByDesc("created_at")
                .last("LIMIT 1"));
        long activeCount = countActive(device.getId());

        DeviceOverviewVO overview = new DeviceOverviewVO();
        BeanUtils.copyProperties(device, overview);
        if (latestData != null) {
            overview.setLatestTime(latestData.getTime());
            overview.setUsageKwh(latestData.getUsageKwh());
            overview.setCo2Emission(latestData.getCo2Emission());
            overview.setTemperature(latestData.getTemperature());
            overview.setVibration(latestData.getVibration());
            overview.setPressure(latestData.getPressure());
            overview.setOperatingStatus(latestData.getOperatingStatus());
            overview.setXianPriceTier(latestData.getXianPriceTier());
        }
        overview.setActiveWorkOrderCount(activeCount);
        overview.setLatestWorkOrderTitle(latestOrder != null ? latestOrder.getTitle() : null);
        overview.setStatus(DeviceStatusHelper.resolveStatus(latestData, device.getStatus(), activeCount > 0));
        return overview;
    }

    private SensorData findLatestSensor(Integer deviceId) {
        return sensorDataMapper.selectOne(new QueryWrapper<SensorData>()
                .eq("device_id", deviceId)
                .orderByDesc("time")
                .last("LIMIT 1"));
    }

    private long countActive(Integer deviceId) {
        return workOrderMapper.selectCount(new QueryWrapper<WorkOrder>()
                .eq("device_id", deviceId)
                .in("status", List.of("PENDING", "IN_PROGRESS")));
    }

    private void validateUniqueCode(String deviceCode, Integer currentId) {
        Device existing = deviceMapper.selectOne(new QueryWrapper<Device>().eq("device_code", deviceCode));
        if (existing != null && (currentId == null || !existing.getId().equals(currentId))) {
            throw new IllegalArgumentException("设备编码已存在: " + deviceCode);
        }
    }

    private void applyRequest(Device device, DeviceUpsertRequest request) {
        device.setDeviceCode(request.getDeviceCode());
        device.setDeviceName(request.getDeviceName());
        device.setDeviceType(request.getDeviceType());
        device.setLocation(request.getLocation());
        device.setMaintainer(request.getMaintainer());
        device.setDescription(request.getDescription());
        if (StringUtils.hasText(request.getStatus())) {
            device.setStatus(request.getStatus().toUpperCase());
        }
    }
}
