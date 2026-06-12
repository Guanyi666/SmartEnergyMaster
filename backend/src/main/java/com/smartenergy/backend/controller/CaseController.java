package com.smartenergy.backend.controller;

import com.smartenergy.backend.dto.CaseCreateRequest;
import com.smartenergy.backend.service.RepairCaseService;
import com.smartenergy.backend.vo.CaseDetailVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cases")
@RequiredArgsConstructor
@Tag(name = "维修案例", description = "维修案例库 (Epic 07-2)")
public class CaseController {

    private final RepairCaseService caseService;

    @GetMapping
    @Operation(summary = "案例列表")
    public List<CaseDetailVO> listCases(
            @Parameter(description = "设备类型") @RequestParam(required = false) String deviceType,
            @Parameter(description = "故障类型") @RequestParam(required = false) String faultType,
            @Parameter(description = "关键词") @RequestParam(required = false) String keyword) {
        return caseService.listCases(deviceType, faultType, keyword);
    }

    @GetMapping("/{id}")
    @Operation(summary = "案例详情")
    public CaseDetailVO getCase(@Parameter(description = "案例 ID") @PathVariable Long id) {
        return caseService.getDetail(id);
    }

    @GetMapping("/similar")
    @Operation(summary = "相似案例检索", description = "按设备类型+故障类型+关键词评分排序")
    public List<CaseDetailVO> findSimilar(
            @Parameter(description = "设备类型") @RequestParam(required = false) String deviceType,
            @Parameter(description = "故障类型") @RequestParam(required = false) String faultType,
            @Parameter(description = "关键词（多个用逗号分隔）") @RequestParam(required = false) String keyword,
            @Parameter(description = "返回条数") @RequestParam(defaultValue = "5") int limit) {
        return caseService.findSimilar(deviceType, faultType, keyword, limit);
    }

    @PostMapping
    @Operation(summary = "新建案例")
    public CaseDetailVO createCase(@Valid @RequestBody CaseCreateRequest request) {
        return caseService.createCase(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新案例")
    public CaseDetailVO updateCase(
            @Parameter(description = "案例 ID") @PathVariable Long id,
            @Valid @RequestBody CaseCreateRequest request) {
        return caseService.updateCase(id, request);
    }

    @PostMapping("/from-work-order/{workOrderId}")
    @Operation(summary = "从闭环工单提取为维修案例")
    public CaseDetailVO createFromWorkOrder(
            @Parameter(description = "工单 ID") @PathVariable Long workOrderId,
            @RequestBody FromWorkOrderRequest body) {
        return caseService.createCaseFromWorkOrder(workOrderId,
                body == null ? null : body.getTitle(),
                body == null ? null : body.getRootCause(),
                body == null ? null : body.getRepairProcess(),
                body == null ? null : body.getTechnician());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除案例")
    public void deleteCase(@Parameter(description = "案例 ID") @PathVariable Long id) {
        caseService.deleteCase(id);
    }

    @lombok.Data
    public static class FromWorkOrderRequest {
        private String title;
        private String rootCause;
        private String repairProcess;
        private String technician;
    }
}