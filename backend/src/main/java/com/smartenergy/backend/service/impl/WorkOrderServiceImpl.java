package com.smartenergy.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smartenergy.backend.dto.WorkOrderStatusRequest;
import com.smartenergy.backend.entity.Device;
import com.smartenergy.backend.entity.SensorData;
import com.smartenergy.backend.entity.WorkOrder;
import com.smartenergy.backend.mapper.DeviceMapper;
import com.smartenergy.backend.mapper.WorkOrderMapper;
import com.smartenergy.backend.service.DeviceService;
import com.smartenergy.backend.service.WorkOrderService;
import com.smartenergy.backend.vo.WorkOrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkOrderServiceImpl implements WorkOrderService {

    private static final DateTimeFormatter ORDER_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final WorkOrderMapper workOrderMapper;
    private final DeviceMapper deviceMapper;
    private final DeviceService deviceService;

    @Override
    @Transactional
    public void createWorkOrderFromFault(Device device, SensorData data, String faultType, String title, String description, String priority) {
        if (hasActiveFault(device.getId(), faultType)) {
            return;
        }

        WorkOrder workOrder = new WorkOrder();
        workOrder.setOrderNo("WO-" + LocalDateTime.now().format(ORDER_NO_FORMATTER) + "-" + device.getId());
        workOrder.setDeviceId(device.getId());
        workOrder.setTitle(title);
        workOrder.setFaultType(faultType);
        workOrder.setDescription(description);
        workOrder.setStatus("PENDING");
        workOrder.setPriority(priority);
        workOrder.setAssignee(StringUtils.hasText(device.getMaintainer()) ? device.getMaintainer() : "待分配");
        workOrder.setSourceTime(data.getTime());
        workOrder.setLatestTemperature(data.getTemperature());
        workOrder.setLatestVibration(data.getVibration());
        workOrder.setLatestPressure(data.getPressure());
        workOrder.setCreatedAt(LocalDateTime.now());
        workOrder.setUpdatedAt(LocalDateTime.now());
        workOrderMapper.insert(workOrder);

        deviceService.updateDeviceStatus(device.getId(), "FAULT");
    }

    @Override
    public List<WorkOrderVO> listWorkOrders(String status) {
        QueryWrapper<WorkOrder> wrapper = new QueryWrapper<WorkOrder>().orderByDesc("created_at");
        if (StringUtils.hasText(status)) {
            wrapper.eq("status", status.toUpperCase());
        }
        return workOrderMapper.selectList(wrapper)
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    @Transactional
    public WorkOrderVO updateStatus(Long id, WorkOrderStatusRequest request) {
        WorkOrder workOrder = workOrderMapper.selectById(id);
        if (workOrder == null) {
            throw new IllegalArgumentException("工单不存在: " + id);
        }

        String targetStatus = request.getStatus().toUpperCase();
        if (!List.of("PENDING", "IN_PROGRESS", "RESOLVED").contains(targetStatus)) {
            throw new IllegalArgumentException("不支持的工单状态: " + request.getStatus());
        }

        // 🟢 修复：用 assigneeProvided 区分"未传"和"显式 null"
        //   - 未传 → 保持原值（兼容老客户端拖拽改 status 的场景）
        //   - 显式 null / 空串 → 清空字段（用于 release 时同步 8080）
        //   - 非空字符串 → 更新
        if (request.isAssigneeProvided()) {
            String a = request.getAssignee();
            if (a == null || a.isBlank()) {
                workOrder.setAssignee(null);
            } else {
                workOrder.setAssignee(a);
            }
        }
        if (StringUtils.hasText(request.getNote())) {
            String suffix = " | 处理备注: " + request.getNote();
            workOrder.setDescription((workOrder.getDescription() == null ? "" : workOrder.getDescription()) + suffix);
        }

        workOrder.setStatus(targetStatus);
        if ("IN_PROGRESS".equals(targetStatus) && workOrder.getAcceptedAt() == null) {
            workOrder.setAcceptedAt(LocalDateTime.now());
        }
        if ("RESOLVED".equals(targetStatus)) {
            workOrder.setResolvedAt(LocalDateTime.now());
        }
        workOrder.setUpdatedAt(LocalDateTime.now());
        workOrderMapper.updateById(workOrder);

        syncDeviceStatusAfterOrderUpdate(workOrder.getDeviceId(), targetStatus);
        return toVO(workOrderMapper.selectById(id));
    }

    @Override
    public List<WorkOrderVO> listActiveAlerts(int limit) {
        return workOrderMapper.selectList(new QueryWrapper<WorkOrder>()
                        .in("status", List.of("PENDING", "IN_PROGRESS"))
                        .orderByDesc("created_at")
                        .last("LIMIT " + limit))
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public long countActiveByDevice(Integer deviceId) {
        return workOrderMapper.selectCount(new QueryWrapper<WorkOrder>()
                .eq("device_id", deviceId)
                .in("status", List.of("PENDING", "IN_PROGRESS")));
    }

    @Override
    public boolean hasActiveFault(Integer deviceId, String faultType) {
        return workOrderMapper.selectCount(new QueryWrapper<WorkOrder>()
                .eq("device_id", deviceId)
                .eq("fault_type", faultType)
                .in("status", List.of("PENDING", "IN_PROGRESS"))) > 0;
    }

    @Override
    public List<WorkOrderVO> listWorkOrdersByDevice(Integer deviceId) {
        return workOrderMapper.selectList(new QueryWrapper<WorkOrder>()
                        .eq("device_id", deviceId)
                        .orderByDesc("created_at"))
                .stream()
                .map(this::toVO)
                .toList();
    }

    private void syncDeviceStatusAfterOrderUpdate(Integer deviceId, String currentStatus) {
        if ("RESOLVED".equals(currentStatus)) {
            boolean hasInProgress = workOrderMapper.selectCount(new QueryWrapper<WorkOrder>()
                    .eq("device_id", deviceId)
                    .eq("status", "IN_PROGRESS")) > 0;
            long activeCount = countActiveByDevice(deviceId);
            if (activeCount > 0) {
                deviceService.updateDeviceStatus(deviceId, hasInProgress ? "MAINTENANCE" : "FAULT");
            } else {
                deviceService.restoreStatusFromLatestData(deviceId);
            }
            return;
        }
        if ("IN_PROGRESS".equals(currentStatus)) {
            deviceService.updateDeviceStatus(deviceId, "MAINTENANCE");
        } else {
            deviceService.updateDeviceStatus(deviceId, "FAULT");
        }
    }

    private WorkOrderVO toVO(WorkOrder workOrder) {
        WorkOrderVO vo = new WorkOrderVO();
        BeanUtils.copyProperties(workOrder, vo);
        Device device = deviceMapper.selectById(workOrder.getDeviceId());
        if (device != null) {
            vo.setDeviceCode(device.getDeviceCode());
            vo.setDeviceName(device.getDeviceName());
        }
        return vo;
    }
}
