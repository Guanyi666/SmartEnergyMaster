package com.smartenergy.backend.controller;

import com.smartenergy.backend.service.DashboardService;
import com.smartenergy.backend.vo.DashboardSummaryVO;
import com.smartenergy.backend.vo.DispatchAdviceVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "仪表盘", description = "大屏聚合统计与调度建议")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    @Operation(summary = "大屏聚合总览", description = "返回全厂总功耗、累计碳排放、当前电价区间、运行/离线设备数、活跃告警数、焦点设备详情")
    public DashboardSummaryVO getSummary(
            @Parameter(description = "焦点设备编码，不传则默认选择第一台设备")
            @RequestParam(required = false) String deviceCode) {
        return dashboardService.getSummary(deviceCode);
    }

    @GetMapping("/dispatch-advice")
    @Operation(summary = "调度建议", description = "基于焦点设备的实时数据与电价时段，给出 4 级调度建议（CRITICAL / WARN / GOOD / INFO）")
    public DispatchAdviceVO getDispatchAdvice(
            @Parameter(description = "设备编码") @RequestParam String deviceCode) {
        return dashboardService.getDispatchAdvice(deviceCode);
    }
}
