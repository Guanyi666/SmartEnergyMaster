package com.smartenergy.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smartenergy.backend.dto.WorkOrderCreateRequest;
import com.smartenergy.backend.dto.WorkOrderStatusRequest;
import com.smartenergy.backend.entity.Device;
import com.smartenergy.backend.entity.SensorData;
import com.smartenergy.backend.entity.WorkOrder;
import com.smartenergy.backend.mapper.DeviceMapper;
import com.smartenergy.backend.mapper.SensorDataMapper;
import com.smartenergy.backend.mapper.WorkOrderMapper;
import com.smartenergy.backend.service.DeviceService;
import com.smartenergy.backend.service.MaintenanceSOPService;
import com.smartenergy.backend.service.SparePartService;
import com.smartenergy.backend.service.WorkOrderService;
import com.smartenergy.backend.vo.WorkOrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkOrderServiceImpl implements WorkOrderService {

    private static final DateTimeFormatter ORDER_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final WorkOrderMapper workOrderMapper;
    private final DeviceMapper deviceMapper;
    private final SensorDataMapper sensorDataMapper;
    private final DeviceService deviceService;
    private final MaintenanceSOPService sopService;
    private final SparePartService sparePartService;

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
        workOrder.setAssignee(null);                              // 🔒 不预填 device.maintainer，避免幽灵指派人（与手动创建路径一致）
        workOrder.setSource("AUTO");                              // 🆕 故障自动生成
        workOrder.setSourceTime(data.getTime());
        workOrder.setLatestTemperature(data.getTemperature());
        workOrder.setLatestVibration(data.getVibration());
        workOrder.setLatestPressure(data.getPressure());
        workOrder.setSopId(sopService.matchSOPId(device.getDeviceType(), faultType));
        workOrder.setCreatedAt(LocalDateTime.now());
        workOrder.setUpdatedAt(LocalDateTime.now());
        workOrderMapper.insert(workOrder);

        deviceService.updateDeviceStatus(device.getId(), "FAULT");
    }

    @Override
    @Transactional
    public WorkOrderVO createWorkOrder(WorkOrderCreateRequest req) {
        // 1. 设备校验
        Device device = deviceMapper.selectById(req.getDeviceId());
        if (device == null) {
            throw new IllegalArgumentException("设备不存在: id=" + req.getDeviceId());
        }

        // 2. 拉设备最新传感器快照（与自动创建路径行为一致，让工单带上下文）
        //    没有数据时三件套为 NULL，sourceTime 退化为当前时间
        SensorData latest = sensorDataMapper.selectOne(new QueryWrapper<SensorData>()
                .eq("device_id", device.getId())
                .orderByDesc("time")
                .last("LIMIT 1"));

        // 3. 拼装工单
        WorkOrder wo = new WorkOrder();
        wo.setOrderNo("WO-" + LocalDateTime.now().format(ORDER_NO_FORMATTER) + "-" + device.getId());
        wo.setDeviceId(device.getId());
        wo.setTitle(req.getTitle());
        wo.setFaultType(req.getFaultType());
        wo.setDescription(req.getDescription());
        wo.setStatus("PENDING");
        wo.setPriority(req.getPriority().toUpperCase());
        wo.setAssignee(null);                              // 🔒 手动创建不预填指派人，避免幽灵指派人
        wo.setSource("MANUAL");                            // 🆕 操作员手动创建
        wo.setSourceTime(latest != null ? latest.getTime() : OffsetDateTime.now());
        wo.setLatestTemperature(latest != null ? latest.getTemperature() : null);
        wo.setLatestVibration(latest != null ? latest.getVibration() : null);
        wo.setLatestPressure(latest != null ? latest.getPressure() : null);
        wo.setSopId(sopService.matchSOPId(device.getDeviceType(), req.getFaultType()));
        wo.setCreatedAt(LocalDateTime.now());
        wo.setUpdatedAt(LocalDateTime.now());

        workOrderMapper.insert(wo);
        return toVO(workOrderMapper.selectById(wo.getId()));
    }

    @Override
    @Transactional
    public void updateAssignee(Long workOrderId, String assignee) {
        WorkOrder workOrder = workOrderMapper.selectById(workOrderId);
        if (workOrder == null) {
            throw new IllegalArgumentException("工单不存在: id=" + workOrderId);
        }
        // null 或空白串都视为清空（与原 updateStatus assignee 分支语义一致）
        workOrder.setAssignee(StringUtils.hasText(assignee) ? assignee : null);
        workOrder.setUpdatedAt(LocalDateTime.now());
        workOrderMapper.updateById(workOrder);
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

        // 🔒 RESOLVED 状态机守卫：已闭环的工单不可重新打开（避免时间线脏数据）
        //   resolvedAt/acceptedAt 一旦写入不再清理，盲目回退会让抽屉时间线显示陈旧的"已闭环"条目
        //   工业现场如需反工，应创建新工单而不是撤销旧工单
        String oldStatus = workOrder.getStatus();
        if ("RESOLVED".equals(oldStatus) && !"RESOLVED".equals(targetStatus)) {
            throw new IllegalArgumentException("已闭环工单不可重新打开，如需反工请创建新工单");
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
            // 工单闭环触发 SOP 关联备件自动扣减（idempotent：同 workOrder+part 已存在 usage 记录会跳过）
            sparePartService.autoDeductForWorkOrder(workOrder);
        }
        workOrder.setUpdatedAt(LocalDateTime.now());
        workOrderMapper.updateById(workOrder);

        // 🟢 仅当 status 真变才联动设备状态，避免 WorkOrderSyncService.sync() 路径里双重写
        if (!oldStatus.equals(targetStatus)) {
            syncDeviceStatusAfterOrderUpdate(workOrder.getDeviceId(), targetStatus);
        }
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
