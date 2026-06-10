package com.smartenergy.backend.controller;

import com.smartenergy.backend.dto.SOPCreateRequest;
import com.smartenergy.backend.dto.SOPUpdateRequest;
import com.smartenergy.backend.service.MaintenanceSOPService;
import com.smartenergy.backend.vo.SOPDetailVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sops")
@RequiredArgsConstructor
@Tag(name = "维修 SOP", description = "标准操作规程管理 (Epic 07-1)")
public class SOPController {

    private final MaintenanceSOPService sopService;

    @GetMapping
    @Operation(summary = "SOP 列表", description = "支持按设备类型、故障类型、关键词筛选")
    public List<SOPDetailVO> listSOPs(
            @Parameter(description = "设备类型") @RequestParam(required = false) String deviceType,
            @Parameter(description = "故障类型") @RequestParam(required = false) String faultType,
            @Parameter(description = "关键词") @RequestParam(required = false) String keyword) {
        return sopService.listSOPs(deviceType, faultType, keyword);
    }

    @GetMapping("/{id}")
    @Operation(summary = "SOP 详情")
    public SOPDetailVO getSOP(@Parameter(description = "SOP ID") @PathVariable Long id) {
        return sopService.getDetail(id);
    }

    @GetMapping("/match")
    @Operation(summary = "按设备/故障匹配最佳 SOP")
    public SOPDetailVO matchSOP(
            @Parameter(description = "设备类型") @RequestParam(required = false) String deviceType,
            @Parameter(description = "故障类型") @RequestParam String faultType) {
        return sopService.findBestMatch(deviceType, faultType);
    }

    @PostMapping
    @Operation(summary = "新建 SOP")
    public SOPDetailVO createSOP(@Valid @RequestBody SOPCreateRequest request) {
        return sopService.createSOP(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新 SOP（自动版本号 +1）")
    public SOPDetailVO updateSOP(@Parameter(description = "SOP ID") @PathVariable Long id,
                                 @Valid @RequestBody SOPUpdateRequest request) {
        return sopService.updateSOP(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除 SOP")
    public void deleteSOP(@Parameter(description = "SOP ID") @PathVariable Long id) {
        sopService.deleteSOP(id);
    }
}