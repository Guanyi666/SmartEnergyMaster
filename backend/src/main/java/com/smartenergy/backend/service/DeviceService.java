package com.smartenergy.backend.service;

import com.smartenergy.backend.dto.DeviceUpsertRequest;
import com.smartenergy.backend.entity.Device;
import com.smartenergy.backend.vo.DeviceOverviewVO;

import java.util.List;

public interface DeviceService {

    List<DeviceOverviewVO> listDevices();

    Device createDevice(DeviceUpsertRequest request);

    Device updateDevice(Integer id, DeviceUpsertRequest request);

    void deleteDevice(Integer id);

    Device getDeviceById(Integer id);

    Device getDeviceByCode(String deviceCode);

    void updateDeviceStatus(Integer deviceId, String status);

    void restoreStatusFromLatestData(Integer deviceId);
}
