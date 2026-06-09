package com.smartenergy.backend.controller;

import com.smartenergy.backend.dto.DeviceUpsertRequest;
import com.smartenergy.backend.entity.Device;
import com.smartenergy.backend.service.DeviceHealthService;
import com.smartenergy.backend.service.DeviceService;
import com.smartenergy.backend.service.WorkOrderService;
import com.smartenergy.backend.vo.DeviceHealthScoreVO;
import com.smartenergy.backend.vo.DeviceOverviewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.smartenergy.backend.vo.PageVO;
import com.smartenergy.backend.vo.WorkOrderVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
@Tag(name = "设备管理", description = "设备的增删改查")
public class DeviceController {

    private final DeviceService deviceService;
    private final WorkOrderService workOrderService;
    private final DeviceHealthService deviceHealthService;

    @GetMapping
    public PageVO<DeviceOverviewVO> listDevices(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return deviceService.listDevices(page, size, type, status, keyword);
    }

    @GetMapping("/{id}")
    public DeviceOverviewVO getDeviceDetail(@PathVariable Integer id) {
        return deviceService.getDeviceOverview(id);
    }

    @GetMapping("/{id}/fault-history")
    public List<WorkOrderVO> getFaultHistory(@PathVariable Integer id) {
        deviceService.getDeviceById(id);
        return workOrderService.listWorkOrdersByDevice(id);
    }

    @GetMapping("/{id}/health-score")
    public DeviceHealthScoreVO getHealthScore(@PathVariable Integer id) {
        return deviceHealthService.evaluateHealth(id);
    }
    @Operation(summary = "设备列表", description = "查询所有设备，每台设备附带最新传感器数据、当前状态和活跃工单数")
    public List<DeviceOverviewVO> listDevices() {
        return deviceService.listDevices();
    }

    @PostMapping
    @Operation(summary = "创建设备", description = "注册新设备，设备编码必须唯一")
    public Device createDevice(@Valid @RequestBody DeviceUpsertRequest request) {
        return deviceService.createDevice(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑设备", description = "根据设备 ID 更新设备信息")
    public Device updateDevice(
            @Parameter(description = "设备 ID") @PathVariable Integer id,
            @Valid @RequestBody DeviceUpsertRequest request) {
        return deviceService.updateDevice(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除设备", description = "根据设备 ID 删除设备，关联的传感器数据级联删除，工单不受影响")
    public void deleteDevice(
            @Parameter(description = "设备 ID") @PathVariable Integer id) {
        deviceService.deleteDevice(id);
    }
}
