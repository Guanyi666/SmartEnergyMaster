package com.smartenergy.workorder.controller;

import com.smartenergy.workorder.dto.BatchWorkOrderAssignRequest;
import com.smartenergy.workorder.dto.WorkOrderAssignRequest;
import com.smartenergy.workorder.dto.WorkOrderReplaceRequest;
import com.smartenergy.workorder.service.WorkOrderAssignmentService;
import com.smartenergy.workorder.vo.DispatchMatchVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workorder/orders")
@RequiredArgsConstructor
@Tag(name = "工单指派", description = "工单指派 / 批量指派 / 释放 / 替换 / 自动匹配")
public class WorkOrderAssignmentController {

    private final WorkOrderAssignmentService assignmentService;

    @PostMapping("/{id}/assign")
    @Operation(summary = "单人指派（事务内同步 8080）")
    public void assign(
            @PathVariable Long id,
            @Valid @RequestBody WorkOrderAssignRequest req) {
        assignmentService.assign(id, req);
    }

    @PostMapping("/{id}/batch-assign")
    @Operation(summary = "🆕 批量指派：一次把多人同时指到同一工单，事务内全部校验+全部插入+同步 8080")
    public void batchAssign(
            @PathVariable Long id,
            @Valid @RequestBody BatchWorkOrderAssignRequest req) {
        assignmentService.batchAssign(id, req);
    }

    @PostMapping("/{id}/assignments/{personnelId}/release")
    @Operation(summary = "🆕 单条释放：把某个具体人员从工单上撤下来")
    public void releaseOne(
            @PathVariable Long id,
            @PathVariable Long personnelId) {
        assignmentService.release(id, personnelId);
    }

    @PostMapping("/{id}/assignments/{personnelId}/replace")
    @Operation(summary = "🆕 替换：把某个人从工单上换掉，事务内释放+新指派+同步 8080")
    public void replace(
            @PathVariable Long id,
            @PathVariable Long personnelId,
            @Valid @RequestBody WorkOrderReplaceRequest req) {
        assignmentService.replace(id, personnelId, req);
    }

    @PostMapping("/{id}/release")
    @Operation(summary = "释放该工单所有活跃指派（保留向后兼容）")
    public void releaseAll(@PathVariable Long id) {
        assignmentService.release(id);
    }

    @GetMapping("/auto-match")
    @Operation(summary = "自动匹配 Top N（传入 workOrderId 排除曾被 release 的人员）")
    public DispatchMatchVO autoMatch(
            @Parameter(description = "故障类型英文常量", example = "MECHANICAL_JAM")
            @RequestParam String faultType,
            @Parameter(description = "工单 ID（可选，传入时排除该工单曾被 release 的人员）")
            @RequestParam(required = false) Long workOrderId,
            @Parameter(description = "推荐条数", example = "5")
            @RequestParam(defaultValue = "5") int topN) {
        return assignmentService.autoMatch(workOrderId, faultType, topN);
    }
}
