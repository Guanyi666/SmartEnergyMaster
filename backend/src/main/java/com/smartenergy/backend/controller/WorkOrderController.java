package com.smartenergy.backend.controller;

import com.smartenergy.backend.dto.WorkOrderStatusRequest;
import com.smartenergy.backend.service.WorkOrderService;
import com.smartenergy.backend.vo.WorkOrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
     * 🟢 修复：用 Map 接收请求体以区分"未提供"和"显式 null"
     * 之前用 WorkOrderStatusRequest 强类型 DTO + StringUtils.hasText() 判断，
     * 导致客户端发 { "assignee": null } 想清空字段时，hasText(null)=false 静默忽略，
     * 工单拖回 PENDING 后仍残留旧指派人文本。
     *
     * 现在的语义：
     *   - status     必传，否则 400
     *   - assignee   不传=保持原值；null 或 ""=清空；其他字符串=更新
     *   - note       不传=不变；其他=追加到描述尾部
     */
    @PatchMapping("/{id}/status")
    @Operation(summary = "工单状态流转", description = "更新工单状态：PENDING → IN_PROGRESS → RESOLVED。状态变更自动联动设备状态。assignee 支持显式清空（传 null）")
    public WorkOrderVO updateStatus(
            @Parameter(description = "工单 ID") @PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        // status 必传，单独取出后转 DTO 给 service
        Object statusObj = request.get("status");
        if (statusObj == null || statusObj.toString().isBlank()) {
            throw new IllegalArgumentException("工单状态不能为空");
        }
        WorkOrderStatusRequest dto = new WorkOrderStatusRequest();
        dto.setStatus(statusObj.toString());
        if (request.containsKey("assignee")) {
            Object a = request.get("assignee");
            dto.setAssignee(a == null ? null : a.toString());
        }
        if (request.containsKey("note")) {
            Object n = request.get("note");
            dto.setNote(n == null ? null : n.toString());
        }
        // 把"是否显式传了 assignee"标记传给 service，避免它在内部丢失语义
        dto.setAssigneeProvided(request.containsKey("assignee"));
        return workOrderService.updateStatus(id, dto);
    }
}
