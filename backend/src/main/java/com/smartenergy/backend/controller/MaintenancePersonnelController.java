package com.smartenergy.backend.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartenergy.backend.dto.MaintenancePersonnelRequest;
import com.smartenergy.backend.dto.PageQuery;
import com.smartenergy.backend.service.MaintenancePersonnelService;
import com.smartenergy.backend.vo.MaintenancePersonnelVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workorder/personnel")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('HR_MANAGER', 'DEVICE_MANAGER', 'ADMIN')")  // v6.2 改造：只这 3 个角色能访问人员档案
@Tag(name = "维修人员", description = "维修人员档案 CRUD + 切岗")
public class MaintenancePersonnelController {

    private final MaintenancePersonnelService personnelService;

    @GetMapping
    @Operation(summary = "人员列表（分页 + 筛选）")
    public Page<MaintenancePersonnelVO> list(
            PageQuery pageQuery,
            @Parameter(description = "技能过滤（contains 匹配 specializations）")
            @RequestParam(required = false) String specialization,
            @Parameter(description = "技能等级：JUNIOR/INTERMEDIATE/SENIOR/EXPERT")
            @RequestParam(required = false) String skillLevel,
            @Parameter(description = "是否在岗")
            @RequestParam(required = false) Boolean onDuty) {
        return personnelService.list(pageQuery, specialization, skillLevel, onDuty);
    }

    @GetMapping("/{id}")
    @Operation(summary = "人员详情")
    public MaintenancePersonnelVO getById(@PathVariable Long id) {
        return personnelService.getById(id);
    }

    @PostMapping
    @Operation(summary = "新增人员")
    public MaintenancePersonnelVO create(@Valid @RequestBody MaintenancePersonnelRequest req) {
        return personnelService.create(req);
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑人员")
    public MaintenancePersonnelVO update(
            @PathVariable Long id,
            @Valid @RequestBody MaintenancePersonnelRequest req) {
        return personnelService.update(id, req);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除人员（仅当 current_workload=0）")
    public void delete(@PathVariable Long id) {
        personnelService.delete(id);
    }

    @PatchMapping("/{id}/duty")
    @Operation(summary = "切换在岗/离岗")
    public MaintenancePersonnelVO toggleDuty(
            @PathVariable Long id,
            @Parameter(description = "true=在岗, false=离岗")
            @RequestParam boolean onDuty) {
        return personnelService.toggleDuty(id, onDuty);
    }
}
