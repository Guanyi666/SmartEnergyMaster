package com.smartenergy.backend.controller;

import com.smartenergy.backend.dto.WorkOrderStatusRequest;
import com.smartenergy.backend.service.WorkOrderService;
import com.smartenergy.backend.vo.WorkOrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/work-orders")
@RequiredArgsConstructor
@Tag(name = "维修工单", description = "工单生命周期管理")
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    @GetMapping
    @Operation(summary = "工单列表", description = "查询所有工单，支持按状态筛选")
    public List<WorkOrderVO> listWorkOrders(
            @Parameter(description = "工单状态筛选（PENDING / IN_PROGRESS / RESOLVED），不传则返回全部")
            @RequestParam(required = false) String status) {
        return workOrderService.listWorkOrders(status);
    }

    @GetMapping("/active-alerts")
    @Operation(summary = "活跃告警", description = "返回 PENDING 和 IN_PROGRESS 状态的工单，按创建时间倒序")
    public List<WorkOrderVO> listActiveAlerts(
            @Parameter(description = "返回条数上限") @RequestParam(defaultValue = "5") int limit) {
        return workOrderService.listActiveAlerts(limit);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "工单状态流转", description = "更新工单状态：PENDING → IN_PROGRESS → RESOLVED。状态变更自动联动设备状态")
    public WorkOrderVO updateStatus(
            @Parameter(description = "工单 ID") @PathVariable Long id,
            @Valid @RequestBody WorkOrderStatusRequest request) {
        return workOrderService.updateStatus(id, request);
    }
}
