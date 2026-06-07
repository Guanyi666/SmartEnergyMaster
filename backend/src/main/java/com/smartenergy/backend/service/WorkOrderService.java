package com.smartenergy.backend.service;

import com.smartenergy.backend.dto.WorkOrderStatusRequest;
import com.smartenergy.backend.entity.Device;
import com.smartenergy.backend.entity.SensorData;
import com.smartenergy.backend.vo.WorkOrderVO;

import java.util.List;

public interface WorkOrderService {

    void createWorkOrderFromFault(Device device, SensorData data, String faultType, String title, String description, String priority);

    List<WorkOrderVO> listWorkOrders(String status);

    WorkOrderVO updateStatus(Long id, WorkOrderStatusRequest request);

    List<WorkOrderVO> listActiveAlerts(int limit);

    long countActiveByDevice(Integer deviceId);

    boolean hasActiveFault(Integer deviceId, String faultType);
}
