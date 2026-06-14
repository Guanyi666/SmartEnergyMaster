package com.smartenergy.backend.controller;

import com.smartenergy.backend.dto.WorkOrderCreateRequest;
import com.smartenergy.backend.dto.WorkOrderStatusRequest;
import com.smartenergy.backend.dto.WorkOrderSopRequest;
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

    /**
     * 🆕 手动创建工单入口（操作员在「维修指挥中心 → 新建工单」对话框调用）
     * 设备三件套快照由后端从 sensor_data 自动拉取，assignee 显式置 null
     * （不预填 device.maintainer，避免「张工幽灵指派人」问题）
     */
    @PostMapping
    @Operation(summary = "手动创建工单", description = "操作员手动开单，含设备快照与 SOP 自动匹配")
    public WorkOrderVO createWorkOrder(@Valid @RequestBody WorkOrderCreateRequest request) {
        return workOrderService.createWorkOrder(request);
    }

    /**
     * 🆕 合并 workorder-backend: 改回 DTO 接收
     * 之前用 Map 是为了在 8080 端区分"未传 assignee"和"传 null"，因为跨进程 HTTP 同步需要。
     * 合并后 WorkOrderSyncService 是同模块方法调用，直接构造 DTO 即可，HTTP 入口只需要
     * 正常处理 DTO（前端拖拽改 status 时不传 assignee 字段 → assigneeProvided 默认 false → 字段不变）。
     *
     * 语义保持：
     *   - status     必传
     *   - assignee   不传 → 保持原值（拖拽场景）；WorkOrderSyncService 显式设 assigneeProvided=true 可清空
     *   - note       不传 → 不变
     */
    @PatchMapping("/{id}/status")
    @Operation(summary = "工单状态流转", description = "更新工单状态：PENDING → IN_PROGRESS → RESOLVED。状态变更自动联动设备状态")
    public WorkOrderVO updateStatus(
            @Parameter(description = "工单 ID") @PathVariable Long id,
            @Valid @RequestBody WorkOrderStatusRequest request) {
        return workOrderService.updateStatus(id, request);
    }

    @PatchMapping("/{id}/sop")
    @Operation(summary = "选择工单维修流程")
    public WorkOrderVO updateSop(
            @PathVariable Long id,
            @Valid @RequestBody WorkOrderSopRequest request) {
        return workOrderService.updateSop(id, request.getSopId());
    }
}
