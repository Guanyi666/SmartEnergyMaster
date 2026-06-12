package com.smartenergy.backend.controller;

import com.smartenergy.backend.dto.SparePartCreateRequest;
import com.smartenergy.backend.dto.BatchSparePartUsageRequest;
import com.smartenergy.backend.dto.SparePartUsageRequest;
import com.smartenergy.backend.service.SparePartService;
import com.smartenergy.backend.vo.SparePartUsageVO;
import com.smartenergy.backend.vo.SparePartVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/spare-parts")
@RequiredArgsConstructor
@Tag(name = "备件库存", description = "备件库存与领用管理 (Epic 07-2)")
public class SparePartController {

    private final SparePartService sparePartService;

    @GetMapping
    @Operation(summary = "备件库存列表", description = "支持关键词搜索和仅看低库存筛选")
    public List<SparePartVO> listParts(
            @Parameter(description = "关键词（编号/名称/规格/供应商）") @RequestParam(required = false) String keyword,
            @Parameter(description = "true=仅返回低库存") @RequestParam(required = false) Boolean lowStockOnly) {
        return sparePartService.listParts(keyword, lowStockOnly);
    }

    @GetMapping("/{id}")
    @Operation(summary = "备件详情")
    public SparePartVO getPart(@Parameter(description = "备件 ID") @PathVariable Long id) {
        return sparePartService.getPart(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('DEVICE_MANAGER', 'ADMIN')")
    @Operation(summary = "新建备件")
    public SparePartVO createPart(@Valid @RequestBody SparePartCreateRequest request) {
        return sparePartService.createPart(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('DEVICE_MANAGER', 'ADMIN')")
    @Operation(summary = "更新备件")
    public SparePartVO updatePart(@Parameter(description = "备件 ID") @PathVariable Long id,
                                  @Valid @RequestBody SparePartCreateRequest request) {
        return sparePartService.updatePart(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('DEVICE_MANAGER', 'ADMIN')")
    @Operation(summary = "删除备件（同时级联删除领用记录）")
    public void deletePart(@Parameter(description = "备件 ID") @PathVariable Long id) {
        sparePartService.deletePart(id);
    }

    @PostMapping("/usage")
    @Operation(summary = "备件领用登记（自动扣减库存）")
    public SparePartUsageVO recordUsage(@Valid @RequestBody SparePartUsageRequest request,
                                        Authentication authentication) {
        request.setUserName(authentication.getName());
        return sparePartService.recordUsage(request);
    }

    @PostMapping("/usage/batch")
    @Operation(summary = "批量提交配件申请")
    public List<SparePartUsageVO> recordUsages(@Valid @RequestBody BatchSparePartUsageRequest request,
                                               Authentication authentication) {
        request.getItems().forEach(item -> item.setUserName(authentication.getName()));
        return sparePartService.recordUsages(request.getItems());
    }

    @GetMapping("/usage")
    @Operation(summary = "领用记录查询", description = "partId 和 workOrderId 至少传一个")
    public List<SparePartUsageVO> listUsages(
            @Parameter(description = "备件 ID") @RequestParam(required = false) Long partId,
            @Parameter(description = "工单 ID") @RequestParam(required = false) Long workOrderId,
            @Parameter(description = "领用人") @RequestParam(required = false) String userName,
            @Parameter(description = "返回条数") @RequestParam(defaultValue = "20") int limit,
            Authentication authentication) {
        if (workOrderId != null) {
            return sparePartService.listUsagesByWorkOrder(workOrderId);
        }
        boolean engineer = authentication.getAuthorities().stream()
                .anyMatch(authority -> "MAINTENANCE_ENGINEER".equals(authority.getAuthority()));
        return sparePartService.listUsages(partId, engineer ? authentication.getName() : userName, limit);
    }
}
