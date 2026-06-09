package com.smartenergy.backend.service;

import com.smartenergy.backend.vo.DeviceHealthScoreVO;

public interface DeviceHealthService {

    DeviceHealthScoreVO evaluateHealth(Integer deviceId);
}
