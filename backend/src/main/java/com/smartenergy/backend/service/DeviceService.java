package com.smartenergy.backend.service;

import com.smartenergy.backend.dto.DeviceUpsertRequest;
import com.smartenergy.backend.entity.Device;
import com.smartenergy.backend.vo.DeviceOverviewVO;
import com.smartenergy.backend.vo.PageVO;

public interface DeviceService {

    PageVO<DeviceOverviewVO> listDevices(int page, int size, String type, String status, String keyword);

    DeviceOverviewVO getDeviceOverview(Integer id);

    Device createDevice(DeviceUpsertRequest request);

    Device updateDevice(Integer id, DeviceUpsertRequest request);

    void deleteDevice(Integer id);

    Device getDeviceById(Integer id);

    Device getDeviceByCode(String deviceCode);

    void updateDeviceStatus(Integer deviceId, String status);

    void restoreStatusFromLatestData(Integer deviceId);
}
