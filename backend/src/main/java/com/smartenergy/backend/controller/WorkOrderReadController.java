package com.smartenergy.backend.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartenergy.backend.service.WorkOrderReadService;
import com.smartenergy.backend.vo.WorkOrderAssignmentVO;
import com.smartenergy.backend.vo.WorkOrderReadVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workorder/orders")
@RequiredArgsConstructor
@Tag(name = "工单只读", description = "工单列表/详情/历史（仅读，含 JOIN）")
public class WorkOrderReadController {

    private final WorkOrderReadService readService;

    @GetMapping
    @Operation(summary = "工单分页列表（JOIN 设备 + 活跃指派人）")
    public Page<WorkOrderReadVO> list(
            @Parameter(description = "状态过滤：PENDING/IN_PROGRESS/RESOLVED")
            @RequestParam(required = false) String status,
            @Parameter(description = "页码", example = "1")
            @RequestParam(defaultValue = "1") long pageNum,
            @Parameter(description = "每页条数", example = "20")
            @RequestParam(defaultValue = "20") long pageSize) {
        return readService.listOrders(status, pageNum, pageSize);
    }

    @GetMapping("/{id}")
    @Operation(summary = "工单详情（JOIN 设备 + 活跃指派人）")
    public WorkOrderReadVO detail(@PathVariable Long id) {
        return readService.getOrderDetail(id);
    }

    @GetMapping("/{id}/assignments")
    @Operation(summary = "工单指派历史（按时间倒序）")
    public List<WorkOrderAssignmentVO> assignments(@PathVariable Long id) {
        return readService.getAssignments(id);
    }
}
