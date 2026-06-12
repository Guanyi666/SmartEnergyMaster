package com.smartenergy.backend.service;

import com.smartenergy.backend.dto.WorkOrderCreateRequest;
import com.smartenergy.backend.dto.WorkOrderStatusRequest;
import com.smartenergy.backend.entity.Device;
import com.smartenergy.backend.entity.SensorData;
import com.smartenergy.backend.vo.WorkOrderVO;

import java.util.List;

public interface WorkOrderService {

    void createWorkOrderFromFault(Device device, SensorData data, String faultType, String title, String description, String priority);

    /**
     * 操作员手动创建工单（与故障自动创建结构一致：设备快照 + SOP 自动匹配）
     */
    WorkOrderVO createWorkOrder(WorkOrderCreateRequest request);

    /**
     * 仅同步 work_order.assignee 老字段（不触发 status 变更副作用，不联动设备状态）
     * 用于 WorkOrderSyncService.sync() —— 取代之前走完整 updateStatus() 的做法，避免双重写
     *
     * @param assignee null/空 → 清空字段；非空 → 写入（>64 字符由调用方预截断）
     */
    void updateAssignee(Long workOrderId, String assignee);

    List<WorkOrderVO> listWorkOrders(String status);

    WorkOrderVO updateStatus(Long id, WorkOrderStatusRequest request);

    List<WorkOrderVO> listActiveAlerts(int limit);

    long countActiveByDevice(Integer deviceId);

    boolean hasActiveFault(Integer deviceId, String faultType);

    List<WorkOrderVO> listWorkOrdersByDevice(Integer deviceId);
}
